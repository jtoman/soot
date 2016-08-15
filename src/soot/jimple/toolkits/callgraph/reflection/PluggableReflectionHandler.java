package soot.jimple.toolkits.callgraph.reflection;

import soot.SootMethod;
import soot.jimple.Stmt;

public interface PluggableReflectionHandler {
	public boolean handleForNameCall(SootMethod container, Stmt s, CallGraphBuilderBridge bridge);
	public boolean handleNewInstanceCall(SootMethod container, Stmt s, CallGraphBuilderBridge bridge);
	public boolean handleInvokeCall(SootMethod container, Stmt s, CallGraphBuilderBridge bridge);
	public boolean handleConstructorNewInstanceCall(SootMethod container, Stmt s, CallGraphBuilderBridge bridge);
	public void handleNewMethod(SootMethod m, CallGraphBuilderBridge bridge);
}
