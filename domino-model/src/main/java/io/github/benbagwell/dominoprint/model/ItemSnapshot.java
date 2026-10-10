package io.github.benbagwell.dominoprint.model;

import java.util.*;

public record ItemSnapshot(String name, String type, boolean list, Map<String,String> flags,
                           List<DominoValue> values, String sourceXml) {
    public ItemSnapshot { Objects.requireNonNull(name); Objects.requireNonNull(type); flags = Map.copyOf(flags); values = List.copyOf(values); sourceXml = Objects.requireNonNullElse(sourceXml, ""); }
    public static ItemSnapshot text(String name, String value) { return new ItemSnapshot(name, "text", false, Map.of(), List.of(new DominoValue.Text(value)), ""); }
    public List<String> textValues() { return values.stream().filter(DominoValue.Text.class::isInstance).map(DominoValue.Text.class::cast).map(DominoValue.Text::value).toList(); }
}
