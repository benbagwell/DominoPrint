package io.github.benbagwell.dominoprint.model;

import org.w3c.dom.Element;
import java.math.BigDecimal;
import java.util.*;

/** Imports document DXL with explicit preservation of values that have no typed decoder. */
public final class DxlDocumentReader {
    private final int maxCharacters;
    public DxlDocumentReader() { this(64 * 1024 * 1024); }
    public DxlDocumentReader(int maxCharacters) { if (maxCharacters < 1) throw new IllegalArgumentException("maxCharacters"); this.maxCharacters=maxCharacters; }
    public DocumentSnapshot read(String source) { return read(source, "", Map.of(), List.of()); }
    public DocumentSnapshot read(String source, String rawDxl, Map<String,String> sourceMetadata, List<Diagnostic> exportDiagnostics) {
        var root=Dxl.parse(source,maxCharacters).getDocumentElement();
        if (!Set.of("document","note","profiledocument").contains(Dxl.name(root))) throw new IllegalArgumentException("Expected document, note or profiledocument DXL");
        Map<String,String> metadata=new LinkedHashMap<>(Dxl.attributes(root)); metadata.putAll(sourceMetadata);
        Dxl.child(root,"noteinfo").ifPresent(info -> {
            metadata.putAll(Dxl.attributes(info));
            for (var c:Dxl.children(info)) metadata.put(Dxl.name(c),c.getTextContent().trim());
        });
        List<Diagnostic> issues=new ArrayList<>(exportDiagnostics); List<ItemSnapshot> items=new ArrayList<>();
        for (var item:Dxl.children(root,"item")) items.add(item(item,issues));
        // Interpreted document DXL can move the Form item to the document's form attribute.
        if (root.hasAttribute("form") && items.stream().noneMatch(i -> i.name().equalsIgnoreCase("Form"))) items.add(ItemSnapshot.text("Form",root.getAttribute("form")));
        return new DocumentSnapshot(metadata,items,source,rawDxl,issues);
    }
    private ItemSnapshot item(Element e, List<Diagnostic> issues) {
        List<DominoValue> values=new ArrayList<>(); var children=Dxl.children(e); String type=children.isEmpty()?"empty":Dxl.name(children.get(0));
        for (var c:children) {
            String kind=Dxl.name(c);
            if (Set.of("textlist","numberlist","datetimelist").contains(kind)) for (var v:Dxl.children(c)) values.add(value(v,e.getAttribute("name"),issues));
            else values.add(value(c,e.getAttribute("name"),issues));
        }
        if ("true".equals(e.getAttribute("seal"))) issues.add(Diagnostic.warning("ENCRYPTED_ITEM",e.getAttribute("name"),"Item is marked encrypted; only content available to the exporting identity was captured."));
        return new ItemSnapshot(e.getAttribute("name"),type,type.endsWith("list"),Dxl.attributes(e),values,Dxl.xml(e));
    }
    private DominoValue value(Element e, String location, List<Diagnostic> issues) {
        String type=Dxl.name(e);
        try {
            return switch(type) {
                case "text" -> new DominoValue.Text(text(e));
                case "number" -> new DominoValue.Numeric(new BigDecimal(e.getTextContent().trim()));
                case "datetime" -> new DominoValue.DateTime(datetime(e));
                case "datetimepair" -> {
                    var parts=Dxl.children(e,"datetime");
                    if(parts.size()!=2) throw new IllegalArgumentException("Range requires two datetimes");
                    yield new DominoValue.DateRange(datetime(parts.get(0)),datetime(parts.get(1)));
                }
                case "richtext" -> new DominoValue.RichText(Dxl.tree(e));
                case "rawitemdata", "richtextdata" -> {
                    decode(e.getTextContent()); // Validate but preserve the entire encoded representation and its type.
                    issues.add(Diagnostic.warning("OPAQUE_ITEM",location,"Raw " + type + " preserved; typed interpretation is unavailable."));
                    yield new DominoValue.Opaque(type,Dxl.xml(e));
                }
                case "object" -> binaryObject(e,location,issues);
                case "mime" -> { issues.add(Diagnostic.warning("MIME_SOURCE_PRESERVED",location,"MIME headers and content retained as DXL; rich-text MIME rendering requires a supported body representation.")); yield new DominoValue.Opaque(type,Dxl.xml(e)); }
                default -> { issues.add(Diagnostic.warning("OPAQUE_ITEM",location,"Unsupported item representation retained: " + type)); yield new DominoValue.Opaque(type,Dxl.xml(e)); }
            };
        } catch(IllegalArgumentException ex) {
            issues.add(Diagnostic.error("INVALID_ITEM",location,ex.getMessage())); return new DominoValue.Opaque(type,Dxl.xml(e));
        }
    }
    private DominoValue binaryObject(Element e, String location, List<Diagnostic> issues) {
        var file=Dxl.child(e,"file");
        if (file.isPresent()) {
            var f=file.get(); var data=Dxl.child(f,"filedata");
            if(data.isPresent()) return new DominoValue.Binary(new BinaryContent(f.getAttribute("name"),mediaType(f.getAttribute("name")),decode(data.get().getTextContent())));
        }
        issues.add(Diagnostic.warning("OPAQUE_OBJECT",location,"Object data retained as DXL; no supported file payload found."));
        return new DominoValue.Opaque("object",Dxl.xml(e));
    }
    public static byte[] decode(String value) { return Base64.getDecoder().decode(value.replaceAll("\\s+","")); }
    public static String mediaType(String name) {
        return switch(name.substring(Math.max(0,name.lastIndexOf('.')+1)).toLowerCase(Locale.ROOT)) {
            case "png" -> "image/png"; case "gif" -> "image/gif"; case "jpg","jpeg" -> "image/jpeg";
            case "pdf" -> "application/pdf"; case "txt" -> "text/plain"; default -> "application/octet-stream";
        };
    }
    private static DominoDateTime datetime(Element e) { return DominoDateTime.parse(e.getTextContent().trim(),"true".equals(e.getAttribute("dst"))); }
    private static String text(Element e) {
        var out=new StringBuilder();
        for(var n=e.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof Element c) out.append(Dxl.name(c).equals("break")?"\n":c.getTextContent()); else out.append(n.getNodeValue()==null?"":n.getNodeValue());
        return out.toString();
    }
}
