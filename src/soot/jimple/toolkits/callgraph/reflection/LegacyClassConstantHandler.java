package soot.jimple.toolkits.callgraph.reflection;

import java.util.HashMap;
import java.util.Map;

import soot.EntryPoints;
import soot.Kind;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.util.HashMultiMap;
import soot.util.MultiMap;
import soot.util.NumberedString;

/**
 * Special cases how java handles class constants before java 1.5.
 * 
 * In Java 1.4, Foo.class creates a call to class$("Foo").
 * This generated class$ method simply calls Class.forName(..) on it's argument.
 * It is trivial to identify this calling pattern and allows for unambiguously
 * resolving the forName calls within the class$ method
 * even when not using spark's string constant propagation.
 * 
 * @author jtoman
 *
 */
public class LegacyClassConstantHandler extends AbstractReflectionHandler {
	private final static NumberedString classAccessSig = Scene.v().getSubSigNumberer().findOrAdd("java.lang.Class class$(java.lang.String)");
	private Map<SootMethod, Stmt> classToForName = new HashMap<SootMethod, Stmt>();
	private MultiMap<SootMethod, String> deferredForName = new HashMultiMap<SootMethod, String>();
	
	@Override
	public boolean handleForNameCall(SootMethod container, Stmt s, CallGraphBuilderBridge bridge) {
		if(container.getNumberedSubSignature() != classAccessSig) {
			return false;
		}
		if(deferredForName.containsKey(container)) {
			for(String deferredName : deferredForName.get(container)) {
				resolveForName(container, s, deferredName, bridge);
			}
		}
		deferredForName.remove(container);
		classToForName.put(container, s);
		return true;
	}
	
	private void resolveForName(SootMethod container, Stmt s, String clsName, CallGraphBuilderBridge bridge) {
		SootClass cls = Scene.v().getSootClass(clsName);
		if(!cls.isPhantom()) {
			for(SootMethod m : EntryPoints.v().clinitsOf(cls)) {
				bridge.addEdge(container, s, m, Kind.CLINIT);
			}
		}
	}
	
	@Override
	public void handleNewMethod(SootMethod m, CallGraphBuilderBridge bridge) {
		for(Unit u : m.getActiveBody().getUnits()) {
			Stmt s = (Stmt) u;
			if(!s.containsInvokeExpr()) {
				continue;
			}
			if(s.getInvokeExpr().getMethod().getNumberedSubSignature() != classAccessSig) {
				continue;
			}
			InvokeExpr ie = s.getInvokeExpr();
			assert ie instanceof StaticInvokeExpr;
			SootMethod key = ie.getMethod();
			assert ie.getArg(0) instanceof StringConstant;
			String clsName = ((StringConstant)ie.getArg(0)).value;
			if(classToForName.containsKey(key)) {
				Stmt forNameUnit = classToForName.get(key);
				resolveForName(key, forNameUnit, clsName, bridge);
			} else {
				deferredForName.put(key, clsName);
			}
		}
	}
}
