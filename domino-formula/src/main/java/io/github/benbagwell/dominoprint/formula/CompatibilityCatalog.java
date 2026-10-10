package io.github.benbagwell.dominoprint.formula;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned inventory; missing implementations are visible rather than silently omitted. */
public final class CompatibilityCatalog {
    public enum Eligibility { OFFLINE_TARGET, EXCLUDED }
    public record Entry(String name,Eligibility eligibility,FunctionRegistry.Status status,String notes) {}
    public record Evidence(String function,String dominoVersion,int cases,int failures,Set<String> coveredScenarios) {
        public Evidence { Objects.requireNonNull(function);Objects.requireNonNull(dominoVersion);if(cases<0||failures<0||failures>cases)throw new IllegalArgumentException("Invalid evidence counts");coveredScenarios=Set.copyOf(coveredScenarios); }
    }
    public record ReleaseReport(boolean ready,List<String> missingImplementations,List<String> missingEvidence,List<String> failedFunctions) {
        public ReleaseReport { missingImplementations=List.copyOf(missingImplementations);missingEvidence=List.copyOf(missingEvidence);failedFunctions=List.copyOf(failedFunctions); }
        public void requireReady(){if(!ready)throw new IllegalStateException("Broad release gate failed: "+missingImplementations.size()+" unimplemented functions, "+missingEvidence.size()+" functions without complete Domino evidence, "+failedFunctions.size()+" failing functions");}
    }
    private static final Set<String> EXCLUDED=Set.of("addtofolder","allchildren","alldescendants","checkalarms","command","ddeexecute","ddeinitiate","ddepoke","ddeterminate","deletedocument","dialogbox","doclock","docmark","editecl","edituserecl","enablealarms","getfocustable","harddeletedocument","launchapp","mailsend","openinnewwindow","picklist","postedcommand","prompt","refreshecl","scanbarcode","setdocfield","setenvironment","sethttpheader","setprofilefield","settargetframe","setviewinfo","showparentpreview","statusbar","undeletedocument","updateformulacontext","urlopen","viewshowthisunread");
    private final List<Entry> entries;
    public CompatibilityCatalog(FunctionRegistry registry){
        List<Entry> out=new ArrayList<>();
        try(var input=CompatibilityCatalog.class.getResourceAsStream("domino-14-functions.txt")){
            if(input==null)throw new IllegalStateException("Missing function inventory");
            for(String name:new String(input.readAllBytes(),StandardCharsets.UTF_8).lines().filter(s->!s.isBlank()&&!s.startsWith("#")).toList()){
                var spec=registry.specification(name);boolean excluded=EXCLUDED.contains(name.toLowerCase(Locale.ROOT));
                var status=excluded?FunctionRegistry.Status.EXCLUDED:spec.map(FunctionRegistry.Specification::status).orElse(FunctionRegistry.Status.NOT_IMPLEMENTED);
                out.add(new Entry(name,excluded?Eligibility.EXCLUDED:Eligibility.OFFLINE_TARGET,status,spec.map(FunctionRegistry.Specification::notes).orElse("Implementation or snapshot/context provider is required")));
            }
        }catch(IOException e){throw new UncheckedIOException(e);}
        entries=List.copyOf(out);
    }
    public String dominoBaseline(){return "14.0";}
    public List<Entry> entries(){return entries;}
    /** Evidence is an audit input, not proof generated from offline unit-test success. */
    public ReleaseReport assess(Collection<Evidence> evidence){
        Map<String,List<Evidence>> byFunction=new HashMap<>();for(var e:evidence)byFunction.computeIfAbsent(e.function.toLowerCase(Locale.ROOT),k->new ArrayList<>()).add(e);
        List<String> missing=new ArrayList<>(),unverified=new ArrayList<>(),failed=new ArrayList<>();
        for(var entry:entries)if(entry.eligibility==Eligibility.OFFLINE_TARGET){
            if(entry.status!=FunctionRegistry.Status.IMPLEMENTED_UNVERIFIED){missing.add(entry.name);continue;}
            var matching=byFunction.getOrDefault(entry.name.toLowerCase(Locale.ROOT),List.of()).stream().filter(e->e.dominoVersion.matches("14(?:\\..*)?")&&e.cases>0).toList();
            if(matching.stream().anyMatch(e->e.failures>0))failed.add(entry.name);
            Set<String> scenarios=new HashSet<>();matching.forEach(e->scenarios.addAll(e.coveredScenarios));
            if(!scenarios.containsAll(Set.of("scalar","list","boundary","errors","options")))unverified.add(entry.name);
        }
        return new ReleaseReport(missing.isEmpty()&&unverified.isEmpty()&&failed.isEmpty(),missing,unverified,failed);
    }
    public String markdown(){var out=new StringBuilder("# Domino 14 function compatibility\n\nGenerated from the bundled HCL function inventory and the runtime registry. `IMPLEMENTED_UNVERIFIED` is experimental, not a claim of Domino equivalence.\n\n| Function | Offline eligibility | Implementation |\n| --- | --- | --- |\n");for(var e:entries)out.append("| @").append(e.name).append(" | ").append(e.eligibility).append(" | ").append(e.status).append(" |\n");return out.toString();}
}
