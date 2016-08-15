package soot.jimple.toolkits.callgraph.reflection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Predicate;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import soot.G;
import soot.Local;
import soot.NullType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.internal.JimpleLocal;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph.ExceptionDest;
import soot.toolkits.scalar.Pair;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

public class ReflectionTypeStateAnalysis {
	public static enum State {
		LOST,
		ID,
		INIT {
			@Override
			public State composeWith(State other) {
				if(other == UNCAST) {
					return other;
				} else if(other == KILL) {
					return other;
				} else if(other == LOST) {
					return BOTTOM;
				} else if(other == ID || other == INIT) {
					return this;
				} else {
					return BOTTOM;
				}
			}
		},
		UNINIT {
			@Override
			public State composeWith(State other) {
				if(other == UNCAST || other == INIT) {
					return other;
				} else if(other == ID) {
					return this;
				} else {
					return BOTTOM;
				}
			}
		},
		NOT_NEW {
			@Override
			public State composeWith(State other) {
				if(other == ID) {
					return this;
				} else if(other == UNCAST) {
					return other;
				} else if(other == INIT) {
					return other;
				} else {
					return BOTTOM;
				}
			}
		},
		UNCAST,
		BOTTOM,
		KILL;
		
		public State composeWith(State other) {
			if(other == ID) {
				return this;
			} else {
				return BOTTOM;
			}
		}
		public State joinWith(State j) {
			if(j == this) {
				return this;
			} else {
				return BOTTOM;
			}
		}
	}
	
	private static class WorklistEdge {
		public final Unit start;
		public final Unit target;
		public final Local fact;
		public WorklistEdge(Unit start, Unit tgt, Local fact) {
			this.start = start;
			this.target = tgt;
			this.fact = fact;
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((fact == null) ? 0 : fact.hashCode());
			result = prime * result + ((start == null) ? 0 : start.hashCode());
			result = prime * result
					+ ((target == null) ? 0 : target.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			WorklistEdge other = (WorklistEdge) obj;
			if (!fact.equals(other.fact)) {
				return false;
			}
			if (!start.equals(other.start)) {
				return false;
			}
			if (!target.equals(other.target)) {
				return false;
			}
			return true;
		}
		@Override
		public String toString() {
			return "WorklistEdge [start=" + start + ", target=" + target
					+ ", fact=" + fact + "]";
		}
	}
	
	private final static Local zeroLocal = new JimpleLocal("<<zero>>", NullType.v());
	private final MultiMap<Unit, Type> reachingCasts = new HashMultiMap<Unit, Type>();
	private final Table<Pair<Unit, Unit>, Local, State> allocState = HashBasedTable.create();
	private ExceptionalUnitGraph ug;
	
	public ReflectionTypeStateAnalysis(SootMethod m) {
		ug = new ExceptionalUnitGraph(m.getActiveBody());
		forwardForNameAnalysis(m);
		forwardNewInstanceAnalysis(m);
		forwardConstructorNewInstanceAnalysis(m);
	}
	
	private void castAnalysis(SootMethod m, Predicate<Stmt> p) {
		allocState.clear();
		HashSet<WorklistEdge> workset = new HashSet<WorklistEdge>();
		for(Unit u : m.getActiveBody().getUnits()) {
			Stmt s = (Stmt) u;
			if(p.apply(s)) {
				if(!(s instanceof AssignStmt)) {
					continue;
				}
				AssignStmt as = (AssignStmt) s;
				assert as.getLeftOp() instanceof Local;
				Local l = (Local) as.getLeftOp();
				for(Unit succ : getSuccessors(u)) {
					Pair<Unit, Unit> k = new Pair<Unit, Unit>(u, succ);
					allocState.put(k, l, State.UNCAST);
					WorklistEdge w = new WorklistEdge(u, succ, l);
					workset.add(w);
				}
			}
		}
		computeTypeState(workset);
	}
	
	private void forwardNewInstanceAnalysis(SootMethod m) {
		castAnalysis(m, new Predicate<Stmt>() {
			public boolean apply(Stmt s) {
				return containsNewInstance(s);
			}
		});
	}
	
	private void forwardConstructorNewInstanceAnalysis(SootMethod m) {
		castAnalysis(m, new Predicate<Stmt>() {
			public boolean apply(Stmt s) {
				return containsConstructorNewInstance(s);
			}
		});
	}

	private boolean containsConstructorNewInstance(Stmt s) {
		return s.containsInvokeExpr() && s.getInvokeExpr().getMethod().getSignature().equals("<java.lang.reflect.Constructor: java.lang.Object newInstance(java.lang.Object[])>");
	}

	private boolean containsNewInstance(Stmt s) {
		return s.containsInvokeExpr() && s.getInvokeExpr().getMethod().getSignature().equals("<java.lang.Class: java.lang.Object newInstance()>");
	}

	public Set<Type> getForNameTypes(Unit u) {
		if(!containsForNameCall((Stmt) u)) {
			return null;
		}
		if(reachingCasts.containsKey(u)) {
			return reachingCasts.get(u);
		}
		return null;
	}
	
	public Set<Type> getNewInstanceTypes(Unit u) {
		if(!containsNewInstance((Stmt) u)) {
			return null;
		}
		if(reachingCasts.containsKey(u)) {
			return reachingCasts.get(u);
		}
		return null;
	}
	
	public Set<Type> getConstructorNewInstanceTypes(Unit u) {
		if(!containsConstructorNewInstance((Stmt)u)) {
			return null;
		}
		if(reachingCasts.containsKey(u)) {
			return reachingCasts.get(u);
		}
		return null;
	}
	
	private void forwardForNameAnalysis(SootMethod m) {
		HashSet<WorklistEdge> workset = new HashSet<WorklistEdge>();
		for(Unit u : m.getActiveBody().getUnits()) {
			Stmt s = (Stmt) u;
			if(containsForNameCall(s)) {
				if(!(s instanceof AssignStmt)) {
					continue;
				}
				AssignStmt as = (AssignStmt) s;
				assert as.getLeftOp() instanceof Local;
				WorklistEdge w = new WorklistEdge(u, u, zeroLocal);
				workset.add(w);
			}
		}
		computeTypeState(workset);
	}
	
	public boolean hasTypeInformation() {
		return !reachingCasts.isEmpty();
	}
	
	public void dumpDebugInfo() {
		for(Unit u : reachingCasts.keySet()) {
			G.v().out.println(u + " --> " + reachingCasts.get(u));
		}
	}

	private boolean containsForNameCall(Stmt s) {
		return s.containsInvokeExpr() && s.getInvokeExpr().getMethod().getSignature().equals("<java.lang.Class: java.lang.Class forName(java.lang.String)>");
	}
	
	
	private boolean isReflectiveOperation(Stmt s) {
		if(!s.containsInvokeExpr()) {
			return false;
		}
		if(containsForNameCall(s) || containsNewInstance(s)) {
			return true;
		}
		if(!(s.getInvokeExpr() instanceof InstanceInvokeExpr)) {
			return false;
		}
		InstanceInvokeExpr iie = (InstanceInvokeExpr) s.getInvokeExpr();
		return iie.getMethod().getDeclaringClass().getName().startsWith("java.lang.reflect");
	}

	private void computeTypeState(HashSet<WorklistEdge> workset) {
		Set<Unit> bottomUnits = new HashSet<Unit>();
		while(!workset.isEmpty()) {
			WorklistEdge curr = workset.iterator().next();
			workset.remove(curr);
			if(bottomUnits.contains(curr.start)) {
				continue;
			}
			if(ug.getTails().contains(curr.target)) {
				State currState = allocState.get(new Pair<Unit, Unit>(curr.start, curr.target), curr.fact);
				Set<Pair<Local, State>> outFacts = handleFlow(curr.start, curr.target, curr.fact);
				for(Pair<Local, State> f : outFacts) {
					State composed = currState.composeWith(f.getO2());
					if(composed != State.INIT) {
						bottomUnits.add(curr.start);
						break;
					}
				}
				assert ug.getSuccsOf(curr.target).size() == 0;
				continue;
			}
			List<Unit> succsOf = getSuccessors(curr.target);
			Set<Pair<Local, State>> outputFacts = handleFlow(curr.start, curr.target, curr.fact);
			Local source = curr.fact;
			Pair<Unit, Unit> valueKey = new Pair<Unit, Unit>(curr.start, curr.target);
			for(Pair<Local, State> n : outputFacts) {
				State comp;
				if(source == zeroLocal) {
					comp = n.getO2();
				} else {
					assert allocState.contains(valueKey, curr.fact) : curr;
					comp = allocState.get(valueKey, curr.fact).composeWith(n.getO2());
					if(comp == State.BOTTOM) {
						bottomUnits.add(curr.start);
						continue;
					}
				}
				if(comp == State.KILL) {
					continue;
				}
				for(Unit u : succsOf) {
					Pair<Unit, Unit> nextValueKey = new Pair<Unit, Unit>(curr.start, u);
					boolean isNew = false;
					Local targetLocal = n.getO1();
					if(allocState.contains(nextValueKey, targetLocal)) {
						State currState = allocState.get(nextValueKey, targetLocal);
						State joined = currState.joinWith(comp);
						if(joined == State.BOTTOM) {
							bottomUnits.add(curr.start);
						}
						if(joined != currState) {
							allocState.put(nextValueKey, targetLocal, joined);
							isNew = true;
						}
					} else {
						isNew = true;
						allocState.put(nextValueKey, targetLocal, comp);
					}
					if(isNew) {
						workset.add(new WorklistEdge(curr.start, u, targetLocal));
					}
				}
			}
		}
		for(Unit u : bottomUnits) {
			reachingCasts.remove(u);
		}
	}
	
	private final static String[] REFLECTION_EXCEPTION_CLASSNAMES = new String[]{
		"java.lang.ClassNotFoundException",
		"java.lang.InstantiationException",
		"java.lang.IllegalAccessException",
		"java.lang.reflect.InvocationTargetException",
		"java.lang.SecurityException",
		"java.lang.NoSuchMethodException",
		"java.lang.ClassCastException"
	};
	private final static RefType[] REFLECTION_EXCEPTION_TYPES;

	
	static {
		REFLECTION_EXCEPTION_TYPES = new RefType[REFLECTION_EXCEPTION_CLASSNAMES.length];
		int i = 0;
		for(String cls : REFLECTION_EXCEPTION_CLASSNAMES) {
			Scene.v().loadClass(cls, SootClass.HIERARCHY);
			REFLECTION_EXCEPTION_TYPES[i++] = Scene.v().getRefType(cls);
		}
	}
	
	
	private List<Unit> getSuccessors(Unit target) {
		List<Unit> toReturn = new ArrayList<Unit>(ug.getUnexceptionalSuccsOf(target));
		Stmt s = (Stmt) target;
		// explicitly ignore exceptional flow from reflective calls
		if(ug.getExceptionalSuccsOf(target).isEmpty() || isReflectiveOperation(s)) {
			return toReturn;
		}
		e_search: for(ExceptionDest d : ug.getExceptionDests(target)) {
			if(d.getTrap() == null) {
				continue;
			}
			for(RefType r : REFLECTION_EXCEPTION_TYPES) {
				if(d.getThrowables().catchableAs(r)) {
					continue e_search;
				}
			}
			assert ug.getExceptionalSuccsOf(target).contains(d.getTrap().getHandlerUnit());
			toReturn.add(d.getTrap().getHandlerUnit());
		}
		return toReturn;
	}

	private Set<Pair<Local, State>> handleFlow(Unit src, Unit target, Local fact) {
		Stmt s = (Stmt) target;
		if(target instanceof AssignStmt) {
			AssignStmt as = (AssignStmt) target;
			Value lhs = as.getLeftOp();
			Value rhs = as.getRightOp();
			if(rhs instanceof CastExpr && ((CastExpr) rhs).getOp().equivTo(fact)) {
				addSummary(src, ((CastExpr)rhs).getCastType());
				return Collections.emptySet();
			} else if(rhs instanceof InstanceInvokeExpr) {
				InstanceInvokeExpr iie = (InstanceInvokeExpr) rhs;
				if(iie.getBase() == fact && containsNewInstance(s)) {
					HashSet<Pair<Local, State>> toReturn = new HashSet<Pair<Local, State>>();
					toReturn.add(new Pair<Local, State>(fact, State.INIT));
					toReturn.add(new Pair<Local, State>((Local) lhs, State.UNCAST));
					return toReturn;
				} else if(iie.getBase() == fact && containsGetConstructor(s)) {
					HashSet<Pair<Local, State>> toReturn = new HashSet<Pair<Local, State>>();
					toReturn.add(new Pair<Local, State>(fact, State.INIT));
					toReturn.add(new Pair<Local, State>((Local)lhs, State.NOT_NEW));
					return toReturn;
				} else if(iie.getBase() == fact && containsConstructorNewInstance(s)) {
					HashSet<Pair<Local, State>> toReturn = new HashSet<Pair<Local, State>>();
					toReturn.add(new Pair<Local, State>(fact, State.INIT));
					toReturn.add(new Pair<Local, State>((Local)lhs, State.UNCAST));
					return toReturn;
				// all other operations on reflection objects are assumed pure
				} else if(iie.getBase() == fact) {
					return Collections.singleton(new Pair<Local, State>(fact, State.ID));
				}
			} else if(rhs instanceof StaticInvokeExpr && containsForNameCall(s) && fact == zeroLocal) {
				return Collections.singleton(new Pair<Local, State>((Local) lhs, State.UNINIT));
			} else if(rhs instanceof Local && rhs == fact && lhs instanceof Local) {
				HashSet<Pair<Local, State>> toReturn = new HashSet<Pair<Local, State>>();
				toReturn.add(new Pair<Local, State>(fact, State.ID));
				toReturn.add(new Pair<Local, State>((Local) lhs, State.ID));
				return toReturn;
			} else if(lhs == fact) {
				return Collections.singleton(new Pair<Local, State>(fact, State.KILL));
			}
		}
		for(ValueBox vb : s.getUseBoxes()) {
			if(vb.getValue().equivTo(fact)) {
				return Collections.singleton(new Pair<Local, State>(fact, State.LOST));
			}
		}
		return Collections.singleton(new Pair<Local, State>(fact, State.ID));
	}
	
	private boolean containsGetConstructor(Stmt s) {
		return s.containsInvokeExpr() && s.getInvokeExpr().getMethod().getSignature().equals("<java.lang.Class: java.lang.reflect.Constructor getConstructor(java.lang.Class[])>");
	}

	private void addSummary(Unit src, Type castType) {
		reachingCasts.put(src, castType);
	}
}
