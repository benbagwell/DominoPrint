package io.github.benbagwell.dominoprint.formula;

import java.util.*;

/** Supplies detached resources only. Implementations used for offline rendering must not perform I/O. */
public interface LookupProvider {
    record Request(String server, String database, String view, FormulaValue key, FormulaValue fieldOrColumn, Set<String> keywords) {
        public Request { Objects.requireNonNull(server); Objects.requireNonNull(database); Objects.requireNonNull(view); Objects.requireNonNull(key); Objects.requireNonNull(fieldOrColumn); keywords=Set.copyOf(keywords); }
    }
    FormulaValue lookup(Request request);
    FormulaValue column(String server,String database,String view,int column);
    default FormulaValue documentField(String database,String unid,String field) { return FormulaValue.error("MISSING_RESOURCE","Document is not in the snapshot"); }
    default FormulaValue profileField(String database,String profile,String key,String field) { return FormulaValue.error("MISSING_RESOURCE","Profile is not in the snapshot"); }
    default FormulaValue translate(String set,String code) { return FormulaValue.error("MISSING_RESOURCE","Translation set is not in the snapshot"); }
    static LookupProvider empty() { return new LookupProvider() {
        public FormulaValue lookup(Request r) { return FormulaValue.error("MISSING_RESOURCE","Lookup view is not in the snapshot: " + r.view()); }
        public FormulaValue column(String s,String d,String v,int c) { return FormulaValue.error("MISSING_RESOURCE","Lookup view is not in the snapshot: " + v); }
    }; }
}
