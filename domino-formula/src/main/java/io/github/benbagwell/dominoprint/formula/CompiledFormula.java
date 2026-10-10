package io.github.benbagwell.dominoprint.formula;

import java.util.*;

/** Immutable AST and dependency information; safe for concurrent evaluation. */
public final class CompiledFormula {
    final FormulaEngine.Expr root;
    private final String source;
    private final Set<String> functions, fieldReferences;
    CompiledFormula(String source, FormulaEngine.Expr root, Set<String> functions, Set<String> fields) { this.source=source; this.root=root; this.functions=Set.copyOf(functions); fieldReferences=Set.copyOf(fields); }
    public String source() { return source; }
    public Set<String> functions() { return functions; }
    public Set<String> fieldReferences() { return fieldReferences; }
}
