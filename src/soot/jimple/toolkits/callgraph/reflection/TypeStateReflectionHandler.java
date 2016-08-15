package soot.jimple.toolkits.callgraph.reflection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import soot.EntryPoints;
import soot.FastHierarchy;
import soot.Immediate;
import soot.Kind;
import soot.Local;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Value;
import soot.jimple.NullConstant;
import soot.jimple.Stmt;
import soot.jimple.toolkits.annotation.nullcheck.NullnessAnalysis;
import soot.jimple.toolkits.callgraph.ConstantArrayAnalysis;
import soot.jimple.toolkits.callgraph.OnFlyCallGraphBuilder;
import soot.jimple.toolkits.callgraph.ConstantArrayAnalysis.ArrayTypes;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.Pair;
import soot.util.NumberedString;

public class TypeStateReflectionHandler extends AbstractReflectionHandler {
	private SootMethod analysisKey;
	private ReflectionTypeStateAnalysis rtsaCache;
	
	private SootMethod constructorAnalysisKey;
	/*
	 * Using the immutable pair type makes sure these analyses are
	 * kept in sync
	 */
	private Pair<NullnessAnalysis, ConstantArrayAnalysis> constructorAnalysisCache;
	
	private final FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();
	private final NumberedString sigInit = Scene.v().getSubSigNumberer().findOrAdd("void <init>()");

	private ReflectionTypeStateAnalysis getReflectionAnalysis(SootMethod m) {
		if(analysisKey == m) {
			return rtsaCache;
		} else {
			rtsaCache = new ReflectionTypeStateAnalysis(m);
			analysisKey = m;
			return rtsaCache;
		}
	}
	
	@Override
	public boolean handleForNameCall(SootMethod source, Stmt s, CallGraphBuilderBridge bridge) {
		Set<Type> reachingTypes = getReflectionAnalysis(source).getForNameTypes(s);
		if(reachingTypes == null) {
			return false;
		}
		for (SootClass cls : Scene.v().dynamicClasses()) {
			for(Type t : reachingTypes) {
				if(fh.canStoreType(cls.getType(), t)) {
                    for (SootMethod clinit : EntryPoints.v().clinitsOf(cls)) {
                        bridge.addEdge( source, s, clinit, Kind.CLINIT);
                    }
                    break;
				}
			}
        }
		return true;
	}
	
	@Override
	public boolean handleNewInstanceCall(SootMethod source, Stmt s, CallGraphBuilderBridge bridge) {
		Set<Type> candidateTypes = getReflectionAnalysis(source).getNewInstanceTypes(s);
		if(candidateTypes == null) {
			return false;
		}
		resolveNullaryConstructor(source, s, candidateTypes, bridge);
		return true;
	}
	
	private void resolveNullaryConstructor(SootMethod source, Stmt s, Set<Type> candidateTypes, CallGraphBuilderBridge bridge) {
		for (SootClass cls : Scene.v().dynamicClasses()) {
			if(cls.isAbstract()) {
				continue;
			}
			SootMethod sm = cls.getMethodUnsafe(sigInit);
			if(sm == null) {
				continue;
			}
			for(Type t : candidateTypes) {
				if(fh.canStoreType(cls.getType(), t)) {
					bridge.addEdge( source, s, sm, Kind.NEWINSTANCE );	
				}
				break;
			}
		}
	}
	
	@Override
	public boolean handleConstructorNewInstanceCall(SootMethod container, Stmt s, CallGraphBuilderBridge bridge) {
		Set<Type> candidateTypes = getReflectionAnalysis(container).getConstructorNewInstanceTypes(s);
		if(candidateTypes == null) {
			return false;
		}
		Pair<NullnessAnalysis, ConstantArrayAnalysis> constructorAnalyses = getConstructorAnalyses(container);
		NullnessAnalysis na = constructorAnalyses.getO1();
		ConstantArrayAnalysis ca = constructorAnalyses.getO2();
		Value constrArgs = s.getInvokeExpr().getArg(0);
		assert constrArgs instanceof Immediate;
		if(constrArgs instanceof NullConstant || na.isAlwaysNullBefore(s, (Immediate) constrArgs)) {
			resolveNullaryConstructor(container, s, candidateTypes, bridge);
			return true;
		}
		assert constrArgs instanceof Local;
		Local argLocal = (Local) constrArgs;
		boolean mustNotBeNull = na.isAlwaysNonNullBefore(s, argLocal);
		boolean constantArray = ca.isConstantBefore(s, argLocal);
		if(constantArray) {
			ArrayTypes at = ca.getArrayTypesBefore(s, argLocal);
			if(!mustNotBeNull) {
				at.possibleSizes.add(0);
			}
			for(SootClass cls : Scene.v().dynamicClasses()) {
				if(cls.isAbstract()) {
					continue;
				}
				for(Type t : candidateTypes) {
					if(fh.canStoreType(cls.getType(), t)) {
						for(SootMethod constr : getConstructorMatchingTypes(cls, at)) {
							bridge.addEdge(container, s, constr, Kind.REFL_CONSTR_NEWINSTANCE);
						}
						break;
					}
				}
			}
		} else {
			for(SootClass cls : Scene.v().dynamicClasses()) {
				if(cls.isAbstract()) {
					continue;
				}
				for(Type t : candidateTypes) {
					if(fh.canStoreType(cls.getType(), t)) {
						for(SootMethod constr : getConstructors(cls)) {
							bridge.addEdge(container, s, constr, Kind.REFL_CONSTR_NEWINSTANCE);
						}
						break;
					}
				}
			}
		}
		return true;
	}
	
	private Collection<SootMethod> getConstructors(SootClass cls) {
		List<SootMethod> toReturn = new ArrayList<SootMethod>();
		for(SootMethod m : cls.getMethods()) {
			if(!m.isConstructor() || !m.isPublic() || !m.isConcrete()) {
				continue;
			}
			toReturn.add(m);
		}
		return toReturn;
	}
	
	private Collection<SootMethod> getConstructorMatchingTypes(SootClass cls, ArrayTypes at) {
		List<SootMethod> toReturn = new ArrayList<SootMethod>();
		outer_search: for(SootMethod m : cls.getMethods()) {
			if(!m.isConstructor() || !m.isPublic() || !m.isConcrete()) {
				continue;
			}
			if(!at.possibleSizes.contains(m.getParameterCount())) {
				continue;
			}
			for(int i = 0; i < m.getParameterCount(); i++) {
				if(!OnFlyCallGraphBuilder.isReflectionCompatible(fh, m.getParameterType(i), at.possibleTypes[i])) {
					continue outer_search;
				}
			}
			toReturn.add(m);
		}
		return toReturn;
	}

	private Pair<NullnessAnalysis, ConstantArrayAnalysis> getConstructorAnalyses(SootMethod container) {
		if(constructorAnalysisKey == container) {
			return constructorAnalysisCache;
		}
		ExceptionalUnitGraph graph = new ExceptionalUnitGraph(container.getActiveBody());
		NullnessAnalysis na = new NullnessAnalysis(graph);
		ConstantArrayAnalysis caa = new ConstantArrayAnalysis(graph, container.getActiveBody());
		constructorAnalysisCache = new Pair<NullnessAnalysis, ConstantArrayAnalysis>(na, caa);
		constructorAnalysisKey = container;
		return constructorAnalysisCache;
	}
}
