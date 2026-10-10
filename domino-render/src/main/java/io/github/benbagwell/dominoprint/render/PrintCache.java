package io.github.benbagwell.dominoprint.render;

import io.github.benbagwell.dominoprint.formula.LookupProvider;
import io.github.benbagwell.dominoprint.model.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Caller-driven refresh. Publication is atomic; source NSF reads are not a database transaction. */
public final class PrintCache {
    public record Scope(String sourceIdentity,String loadingIdentity) { public Scope { Objects.requireNonNull(sourceIdentity);Objects.requireNonNull(loadingIdentity);if(sourceIdentity.isBlank()||loadingIdentity.isBlank())throw new IllegalArgumentException("Cache identity must be explicit"); } }
    public record Bundle(Scope scope,List<CompiledPrintForm> forms,CodeTranslations codes,SnapshotLookups lookups,
                         Map<String,BinaryContent> resources,Map<String,PreparationHook> hooks,Map<String,String> sourceVersions) {
        public Bundle { Objects.requireNonNull(scope);forms=List.copyOf(forms);Objects.requireNonNull(codes);lookups=Objects.requireNonNull(lookups).withCodes(codes);resources=Map.copyOf(resources);hooks=Map.copyOf(hooks);sourceVersions=Map.copyOf(sourceVersions); }
    }
    public record Version(long number,Instant loadedAt,Scope scope,Map<String,CompiledPrintForm> forms,CodeTranslations codes,
                          LookupProvider lookups,Map<String,BinaryContent> resources,Map<String,PreparationHook> hooks,Map<String,String> sourceVersions) {
        public Version { forms=Map.copyOf(forms);resources=Map.copyOf(resources);hooks=Map.copyOf(hooks);sourceVersions=Map.copyOf(sourceVersions); }
        public CompiledPrintForm form(String name){return design("form",name);}
        public CompiledPrintForm design(String kind,String name){var form=forms.get(designKey(kind,name));if(form==null)throw new IllegalArgumentException("Design element not cached: "+kind+"/"+name);return form;}
    }
    private final Scope scope;
    private final AtomicReference<Version> current=new AtomicReference<>();
    public PrintCache(Scope scope){this.scope=Objects.requireNonNull(scope);}
    public Version snapshot(){var v=current.get();if(v==null)throw new IllegalStateException("Cache has not been loaded");return v;}
    public synchronized Version refresh(Supplier<Bundle> loader){
        var candidate=Objects.requireNonNull(loader.get());if(!scope.equals(candidate.scope))throw new IllegalArgumentException("Cache source/loading identity mismatch");
        Map<String,CompiledPrintForm> forms=new LinkedHashMap<>();
        for(var form:candidate.forms){if(!form.structurallySupported())throw new IllegalArgumentException("Unsupported form "+form.name()+": "+form.diagnostics());List<String> names=new ArrayList<>(form.aliases());names.add(form.name());for(var name:names)if(forms.putIfAbsent(designKey(form.kind(),name),form)!=null)throw new IllegalArgumentException("Duplicate design name/alias: "+form.kind()+"/"+name);}
        for(var form:candidate.forms){for(String dep:form.preparationDependencies())if(!candidate.hooks.containsKey(dep))throw new IllegalArgumentException("Missing preparation hook: "+dep);validateReferences(form.body(),forms,new ArrayDeque<>(List.of(designKey(form.kind(),form.name()))));}
        var prior=current.get();var version=new Version(prior==null?1:prior.number+1,Instant.now(),scope,forms,candidate.codes,candidate.lookups,candidate.resources,candidate.hooks,candidate.sourceVersions);current.set(version);return version;
    }
    private static void validateReferences(TemplateNode node,Map<String,CompiledPrintForm> forms,Deque<String> path){
        if(Set.of("subformref","sharedfieldref").contains(node.kind())&&node.formulas().isEmpty()){
            String name=designKey(node.kind().equals("subformref")?"subform":"sharedfield",node.attributes().getOrDefault("name",""));var form=forms.get(name);if(form==null)throw new IllegalArgumentException("Missing design dependency: "+name);
            if(path.contains(name))throw new IllegalArgumentException("Recursive design dependency: "+path+" -> "+name);path.addLast(name);validateReferences(form.body(),forms,path);path.removeLast();
        }
        for(var child:node.children())validateReferences(child,forms,path);
    }
    private static String designKey(String kind,String name){return kind+":"+name.toLowerCase(Locale.ROOT);}
}
