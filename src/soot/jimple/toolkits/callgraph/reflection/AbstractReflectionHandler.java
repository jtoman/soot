package soot.jimple.toolkits.callgraph.reflection;

import soot.SootMethod;
import soot.jimple.Stmt;

public class AbstractReflectionHandler implements PluggableReflectionHandler {
	@Override
	public boolean handleForNameCall(SootMethod container, Stmt s, CallGraphBuilderBridge bridge) {
		return false;
	}

	@Override
	public boolean handleNewInstanceCall(SootMethod container, Stmt s, CallGraphBuilderBridge bridge) {
		return false;
	}

	@Override
	public boolean handleInvokeCall(SootMethod container, Stmt s, CallGraphBuilderBridge bridge) {
		return false;
	}

	@Override
	public boolean handleConstructorNewInstanceCall(SootMethod container, Stmt s, CallGraphBuilderBridge bridge) {
		return false;
	}

	@Override
	public void handleNewMethod(SootMethod m, CallGraphBuilderBridge bridge) {
	}
}
