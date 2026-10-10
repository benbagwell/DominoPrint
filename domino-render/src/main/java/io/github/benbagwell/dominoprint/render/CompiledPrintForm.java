package io.github.benbagwell.dominoprint.render;

import io.github.benbagwell.dominoprint.model.*;
import java.util.*;

public record CompiledPrintForm(String name,List<String> aliases,String kind,TemplateNode body,String sourceDxl,
                                String sourceVersion,List<Diagnostic> diagnostics,Set<String> preparationDependencies) {
    public CompiledPrintForm { Objects.requireNonNull(name);aliases=List.copyOf(aliases);Objects.requireNonNull(kind);Objects.requireNonNull(body);Objects.requireNonNull(sourceDxl);Objects.requireNonNull(sourceVersion);diagnostics=List.copyOf(diagnostics);preparationDependencies=Set.copyOf(preparationDependencies); }
    public boolean structurallySupported(){return diagnostics.stream().noneMatch(d->d.severity()==Diagnostic.Severity.ERROR);}
}
