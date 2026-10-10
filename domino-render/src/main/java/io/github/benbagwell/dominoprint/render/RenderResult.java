package io.github.benbagwell.dominoprint.render;

import io.github.benbagwell.dominoprint.formula.FormulaValue;
import io.github.benbagwell.dominoprint.model.*;
import java.util.*;

public record RenderResult(String html,String plainText,RenderedNode layout,Map<String,BinaryContent> assets,
                           List<Diagnostic> diagnostics,long cacheVersion,String formVersion,Map<String,FormulaValue> workingFields) {
    public RenderResult { Objects.requireNonNull(html);Objects.requireNonNull(plainText);Objects.requireNonNull(layout);assets=Map.copyOf(assets);diagnostics=List.copyOf(diagnostics);workingFields=Map.copyOf(workingFields); }
}
