package soot.jimple.toolkits.callgraph.reflection;

import soot.Kind;
import soot.SootMethod;
import soot.jimple.Stmt;

public interface CallGraphBuilderBridge {
	public void addEdge(SootMethod src, Stmt stmt, SootMethod tgt, Kind kind);
}
