package io.github.benbagwell.dominoprint.formula;

import io.github.benbagwell.dominoprint.model.DominoDateTime;
import java.util.*;

/** Runs an offline expression and an independently supplied Domino reference. No reference is invented. */
public final class FormulaReferenceVerifier {
    public record Case(String id, String function, String scenario, String source, EvaluationContext context) {
        public Case {
            Objects.requireNonNull(id); Objects.requireNonNull(function); Objects.requireNonNull(source); Objects.requireNonNull(context);
            if (!Set.of("scalar", "list", "boundary", "errors", "options").contains(scenario))
                throw new IllegalArgumentException("Unknown coverage scenario: " + scenario);
        }
    }
    public record Observation(FormulaValue value, Map<String,FormulaValue> workingFields, String detail) {
        public Observation { Objects.requireNonNull(value); workingFields=Map.copyOf(workingFields); Objects.requireNonNull(detail); }
    }
    @FunctionalInterface public interface Oracle { Observation evaluate(Case testCase) throws Exception; }
    public record Result(Case testCase, Observation offline, Observation reference, boolean matches, String failure) {}
    public record Report(String dominoVersion, List<Result> results) {
        public Report { Objects.requireNonNull(dominoVersion); results=List.copyOf(results); }
        public List<CompatibilityCatalog.Evidence> evidence() {
            Map<String,List<Result>> groups=new TreeMap<>();
            for(var r:results)groups.computeIfAbsent(r.testCase.function().replaceFirst("^@", "").toLowerCase(Locale.ROOT),k->new ArrayList<>()).add(r);
            List<CompatibilityCatalog.Evidence> evidence=new ArrayList<>();
            groups.forEach((name,cases)->evidence.add(new CompatibilityCatalog.Evidence(name,dominoVersion,cases.size(),
                (int)cases.stream().filter(r->!r.matches).count(),new HashSet<>(cases.stream().map(r->r.testCase.scenario()).toList()))));
            return List.copyOf(evidence);
        }
        public long failures(){return results.stream().filter(r->!r.matches).count();}
        public void requireMatching(){if(failures()>0)throw new IllegalStateException(failures()+" Domino reference cases differ or could not be evaluated");}
    }
    private final FormulaEngine engine;
    public FormulaReferenceVerifier(FormulaEngine engine){this.engine=Objects.requireNonNull(engine);}
    public Report verify(String dominoVersion, Collection<Case> cases, Oracle oracle) {
        Objects.requireNonNull(oracle); List<Result> results=new ArrayList<>(); Set<String> ids=new HashSet<>();
        for(var c:cases) {
            if(!ids.add(c.id()))throw new IllegalArgumentException("Duplicate reference case id: "+c.id());
            var formula=engine.compile(c.source());
            if(!formula.functions().contains(c.function().replaceFirst("^@", "").toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Case does not exercise its target function: "+c.id());
            var evaluation=engine.evaluate(formula,c.context());
            var offline=new Observation(evaluation.value(),evaluation.workingFields(),"Offline Java evaluation");
            try {
                var reference=Objects.requireNonNull(oracle.evaluate(c));
                boolean equal=equivalent(offline.value(),reference.value(),c.context())&&fieldsEqual(offline.workingFields(),reference.workingFields(),c.context());
                results.add(new Result(c,offline,reference,equal,equal?"":"Value, type, order, or working fields differ"));
            } catch(Exception e) {results.add(new Result(c,offline,null,false,e.getClass().getSimpleName()+": "+Objects.toString(e.getMessage(),"Reference failed")));}
        }
        return new Report(dominoVersion,results);
    }
    private static boolean fieldsEqual(Map<String,FormulaValue> a,Map<String,FormulaValue> b,EvaluationContext c) {
        var left=c.withFields(a).fields(); var right=c.withFields(b).fields();
        return left.keySet().equals(right.keySet())&&left.entrySet().stream().allMatch(e->equivalent(e.getValue(),right.get(e.getKey()),c));
    }
    private static boolean equivalent(FormulaValue a,FormulaValue b,EvaluationContext c) {
        if(a.type()!=b.type()||a.size()!=b.size())return false;
        for(int i=0;i<a.size();i++) {
            Object x=a.elements().get(i), y=b.elements().get(i);
            if(x instanceof DominoDateTime d&&y instanceof DominoDateTime e) {
                if(d.date().isPresent()!=e.date().isPresent()||d.time().isPresent()!=e.time().isPresent())return false;
                if(d.date().isPresent()&&d.time().isPresent()) {if(!d.toInstant(c.zone()).equals(e.toInstant(c.zone())))return false;}
                else if(!d.date().equals(e.date())||!d.time().equals(e.time()))return false;
            }else if(!x.equals(y))return false;
        }
        return true;
    }
}
