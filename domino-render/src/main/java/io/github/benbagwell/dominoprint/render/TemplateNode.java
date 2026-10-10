package io.github.benbagwell.dominoprint.render;

import io.github.benbagwell.dominoprint.formula.CompiledFormula;
import java.util.*;

public record TemplateNode(String kind,Map<String,String> attributes,String text,List<TemplateNode> children,
                           Map<String,CompiledFormula> formulas,String sourceXml) {
    public TemplateNode { attributes=Map.copyOf(attributes);children=List.copyOf(children);formulas=Map.copyOf(formulas);Objects.requireNonNull(kind);Objects.requireNonNull(text);Objects.requireNonNull(sourceXml); }
}
