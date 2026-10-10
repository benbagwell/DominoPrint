package io.github.benbagwell.dominoprint.model;

import java.util.*;

/** A by-value document, safe to share after every source Domino object is recycled. */
public final class DocumentSnapshot {
    private final Map<String,String> metadata;
    private final List<ItemSnapshot> items;
    private final Map<String,List<ItemSnapshot>> index;
    private final List<Diagnostic> diagnostics;
    private final String sourceDxl, rawDxl;
    public DocumentSnapshot(Map<String,String> metadata, List<ItemSnapshot> items, String sourceDxl, String rawDxl, List<Diagnostic> diagnostics) {
        this.metadata = Map.copyOf(metadata); this.items = List.copyOf(items); this.sourceDxl = Objects.requireNonNullElse(sourceDxl, ""); this.rawDxl = Objects.requireNonNullElse(rawDxl, ""); this.diagnostics = List.copyOf(diagnostics);
        Map<String,List<ItemSnapshot>> grouped = new LinkedHashMap<>();
        for (var item : items) grouped.computeIfAbsent(normalize(item.name()), k -> new ArrayList<>()).add(item);
        grouped.replaceAll((k,v) -> List.copyOf(v)); index = Map.copyOf(grouped);
    }
    public static String normalize(String name) { return name.toLowerCase(Locale.ROOT); }
    public Map<String,String> metadata() { return metadata; }
    public List<ItemSnapshot> items() { return items; }
    public List<ItemSnapshot> items(String name) { return index.getOrDefault(normalize(name), List.of()); }
    public Optional<ItemSnapshot> item(String name) { return items(name).stream().findFirst(); }
    public String text(String name) { return item(name).flatMap(i -> i.textValues().stream().findFirst()).orElse(""); }
    public String unid() { return metadata.getOrDefault("unid", ""); }
    public String sourceDxl() { return sourceDxl; }
    public String rawDxl() { return rawDxl; }
    public List<Diagnostic> diagnostics() { return diagnostics; }
}
