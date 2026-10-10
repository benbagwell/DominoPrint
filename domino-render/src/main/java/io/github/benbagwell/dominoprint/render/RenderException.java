package io.github.benbagwell.dominoprint.render;

import io.github.benbagwell.dominoprint.model.Diagnostic;
import java.util.List;

public final class RenderException extends IllegalStateException {
    private final List<Diagnostic> diagnostics;
    public RenderException(List<Diagnostic> diagnostics){super(diagnostics.stream().map(Diagnostic::message).reduce((a,b)->a+"; "+b).orElse("Render failed"));this.diagnostics=List.copyOf(diagnostics);}
    public List<Diagnostic> diagnostics(){return diagnostics;}
}
