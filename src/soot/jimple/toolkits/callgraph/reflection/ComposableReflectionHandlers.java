package soot.jimple.toolkits.callgraph.reflection;

import java.util.ArrayList;
import java.util.List;

import soot.G;
import soot.Singletons.Global;
import soot.SootMethod;
import soot.jimple.Stmt;

public class ComposableReflectionHandlers implements PluggableReflectionHandler {
	private List<PluggableReflectionHandler> handlers = new ArrayList<PluggableReflectionHandler>();
	
	public ComposableReflectionHandlers(Global g) { }

	public void addHandler(PluggableReflectionHandler ph) {
		handlers.add(ph);
	}
	
	public void clearHandlers() {
		handlers.clear();
	}
	
	@Override
	public boolean handleForNameCall(SootMethod container, Stmt s, CallGraphBuilderBridge bridge) {
		boolean toReturn = false;
		for(PluggableReflectionHandler pr : handlers) {
			toReturn |= pr.handleForNameCall(container, s, bridge);
		}
		return toReturn;
	}

	@Override
	public boolean handleNewInstanceCall(SootMethod container, Stmt s,
			CallGraphBuilderBridge bridge) {
		boolean toReturn = false;
		for(PluggableReflectionHandler pr : handlers) {
			toReturn |= pr.handleNewInstanceCall(container, s, bridge);
		}
		return toReturn;
	}

	@Override
	public boolean handleInvokeCall(SootMethod container, Stmt s, CallGraphBuilderBridge bridge) {
		boolean toReturn = false;
		for(PluggableReflectionHandler pr : handlers) {
			toReturn |= pr.handleInvokeCall(container, s, bridge);
		}
		return toReturn;
	}

	@Override
	public boolean handleConstructorNewInstanceCall(SootMethod container, Stmt s, CallGraphBuilderBridge bridge) {
		boolean toReturn = false;
		for(PluggableReflectionHandler pr : handlers) {
			toReturn |= pr.handleConstructorNewInstanceCall(container, s, bridge);
		}
		return toReturn;
	}

	@Override
	public void handleNewMethod(SootMethod m, CallGraphBuilderBridge bridge) {
		for(PluggableReflectionHandler pr : handlers) {
			pr.handleNewMethod(m, bridge);
		}
	}
	
	public static ComposableReflectionHandlers v() {
		return G.v().soot_jimple_toolkits_callgraph_reflection_ComposableReflectionHandlers();
	}
}
