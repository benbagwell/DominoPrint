package io.github.benbagwell.dominoprint.model;

import java.util.*;

/** An ordered, immutable rich-text/DXL node. Unknown nodes retain their source XML. */
public record RichTextNode(String kind, Map<String,String> attributes, String text,
                           List<RichTextNode> children, String sourceXml) {
    public RichTextNode { Objects.requireNonNull(kind); attributes = Map.copyOf(attributes); text = Objects.requireNonNullElse(text, ""); children = List.copyOf(children); sourceXml = Objects.requireNonNullElse(sourceXml, ""); }
    public static RichTextNode text(String text) { return new RichTextNode("#text", Map.of(), text, List.of(), ""); }
    public String plainText() {
        if (kind.equals("#text")) return text;
        if (kind.equals("break")) return "\n";
        if (Set.of("code", "formula", "font", "pardef", "rawitemdata", "richtextdata").contains(kind)) return "";
        String joined = children.stream().map(RichTextNode::plainText).reduce("", String::concat);
        return joined + (Set.of("par", "tablerow", "section").contains(kind) ? "\n" : kind.equals("tablecell") ? "\t" : "");
    }
}
