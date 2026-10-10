package io.github.benbagwell.dominoprint.formula;

import io.github.benbagwell.dominoprint.model.*;
import java.time.*;
import java.util.*;

/** Immutable inputs. Time is fixed per context so @Now cannot drift during a render. */
public record EvaluationContext(Map<String,FormulaValue> fields, Map<String,FormulaValue> attributes,
                                Locale locale, ZoneId zone, Instant now, LookupProvider lookups,
                                int maxSteps, int maxDepth, int maxListElements) {
    public EvaluationContext {
        fields=normalize(fields); attributes=normalize(attributes); Objects.requireNonNull(locale); Objects.requireNonNull(zone); Objects.requireNonNull(now); Objects.requireNonNull(lookups);
        if(maxSteps<1 || maxDepth<1 || maxListElements<1) throw new IllegalArgumentException("Evaluation limits must be positive");
    }
    private static Map<String,FormulaValue> normalize(Map<String,FormulaValue> values) { Map<String,FormulaValue> out=new LinkedHashMap<>(); values.forEach((k,v)->out.put(k.toLowerCase(Locale.ROOT),v)); return Map.copyOf(out); }
    public static EvaluationContext of(DocumentSnapshot doc) {
        Map<String,FormulaValue> fields=new LinkedHashMap<>(); for(var item:doc.items()) fields.putIfAbsent(item.name(),FormulaValue.from(item));
        Map<String,FormulaValue> attrs=new LinkedHashMap<>(); doc.metadata().forEach((k,v)->attrs.put(k,FormulaValue.text(v)));
        attrs.put("isnewdoc",FormulaValue.number(0)); attrs.put("iseditmode",FormulaValue.number(0));
        return new EvaluationContext(fields,attrs,Locale.US,ZoneOffset.UTC,Instant.now(),LookupProvider.empty(),100_000,128,100_000);
    }
    public static EvaluationContext empty() { return of(new DocumentSnapshot(Map.of(),List.of(),"","",List.of())); }
    public EvaluationContext withFields(Map<String,FormulaValue> values) { return new EvaluationContext(values,attributes,locale,zone,now,lookups,maxSteps,maxDepth,maxListElements); }
    public EvaluationContext withLookups(LookupProvider provider) { return new EvaluationContext(fields,attributes,locale,zone,now,provider,maxSteps,maxDepth,maxListElements); }
    public EvaluationContext withTime(Instant instant, ZoneId zone) { return new EvaluationContext(fields,attributes,locale,zone,instant,lookups,maxSteps,maxDepth,maxListElements); }
    public EvaluationContext withAttributes(Map<String,FormulaValue> values) { return new EvaluationContext(fields,values,locale,zone,now,lookups,maxSteps,maxDepth,maxListElements); }
}
