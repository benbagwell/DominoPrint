package io.github.benbagwell.dominoprint.model;

import org.w3c.dom.*;
import org.xml.sax.InputSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.*;

/** Secure XML utilities. Parsed DOM nodes never escape into detached models. */
public final class Dxl {
    private Dxl() {}
    public static org.w3c.dom.Document parse(String source) {
        return parse(source, 64 * 1024 * 1024);
    }
    public static org.w3c.dom.Document parse(String source, int maxCharacters) {
        if (source.length() > maxCharacters) throw new IllegalArgumentException("DXL exceeds configured character limit");
        try {
            var f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true); f.setXIncludeAware(false); f.setExpandEntityReferences(false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "256");
            var b = f.newDocumentBuilder(); b.setEntityResolver((publicId,systemId) -> new InputSource(new StringReader("")));
            b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() { @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; } });
            var result = b.parse(new InputSource(new StringReader(source)));
            rejectEntities(result); return result;
        } catch (Exception e) { throw new IllegalArgumentException("Invalid or unsafe DXL", e); }
    }
    private static void rejectEntities(Node node) {
        if (node.getNodeType() == Node.ENTITY_REFERENCE_NODE) throw new IllegalArgumentException("Unresolved XML entity: " + node.getNodeName());
        record Pending(Node value, int depth) {}
        var pending = new ArrayDeque<Pending>(); pending.push(new Pending(node,0)); int count=0;
        while(!pending.isEmpty()) {
            var p=pending.pop();
            if(p.depth()>256 || ++count>1_000_000) throw new IllegalArgumentException("XML structure limit exceeded");
            if(p.value().getNodeType()==Node.ENTITY_REFERENCE_NODE) throw new IllegalArgumentException("Unresolved XML entity");
            for(Node child=p.value().getFirstChild();child!=null;child=child.getNextSibling()) pending.push(new Pending(child,p.depth()+1));
        }
    }
    public static String name(Node n) { return n.getLocalName() == null ? n.getNodeName() : n.getLocalName(); }
    public static List<Element> children(Element e) {
        List<Element> result = new ArrayList<>();
        for (Node n=e.getFirstChild(); n!=null; n=n.getNextSibling()) if (n instanceof Element child) result.add(child);
        return List.copyOf(result);
    }
    public static List<Element> children(Element e, String name) { return children(e).stream().filter(c -> name(c).equals(name)).toList(); }
    public static Optional<Element> child(Element e, String name) { return children(e,name).stream().findFirst(); }
    public static List<Element> descendants(Element e, String name) {
        List<Element> result = new ArrayList<>(); collect(e,name,result); return List.copyOf(result);
    }
    private static void collect(Element e, String name, List<Element> result) { for (var c : children(e)) { if (name(c).equals(name)) result.add(c); collect(c,name,result); } }
    public static Map<String,String> attributes(Element e) {
        Map<String,String> result = new LinkedHashMap<>(); var attrs = e.getAttributes();
        for (int i=0; i<attrs.getLength(); i++) { var a=attrs.item(i); if (!a.getNodeName().startsWith("xmlns")) result.put(a.getNodeName(),a.getNodeValue()); }
        return Map.copyOf(result);
    }
    public static String xml(Node node) {
        try { var f=TransformerFactory.newInstance(); f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET,""); var t=f.newTransformer(); t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION,"yes"); var out=new StringWriter(); t.transform(new DOMSource(node),new StreamResult(out)); return out.toString(); }
        catch (TransformerException e) { throw new IllegalArgumentException("Unable to retain XML",e); }
    }
    public static RichTextNode tree(Element e) {
        List<RichTextNode> children = new ArrayList<>();
        for (Node n=e.getFirstChild(); n!=null; n=n.getNextSibling()) {
            if (n instanceof Element child) children.add(tree(child));
            else if (n.getNodeType()==Node.TEXT_NODE || n.getNodeType()==Node.CDATA_SECTION_NODE) {
                String text=n.getNodeValue();
                if (!text.isBlank() || Set.of("run","text","formula","javascript","lotusscript").contains(name(e)) || name(e).equals("par")&&!text.contains("\n")) children.add(RichTextNode.text(text));
            }
        }
        return new RichTextNode(name(e),attributes(e),"",children,xml(e));
    }
}
