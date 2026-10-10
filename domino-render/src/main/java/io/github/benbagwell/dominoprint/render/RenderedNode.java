package io.github.benbagwell.dominoprint.render;

import java.util.*;

/** Both output formats consume this same fully computed tree. */
public record RenderedNode(String tag,Map<String,String> attributes,String text,List<RenderedNode> children) {
    public RenderedNode { Objects.requireNonNull(tag);attributes=Map.copyOf(attributes);Objects.requireNonNull(text);children=List.copyOf(children); }
    public static RenderedNode text(String value){return new RenderedNode("#text",Map.of(),value,List.of());}
    public String plainText(){
        if(tag.equals("#text"))return text;if(tag.equals("br"))return "\n";if(tag.equals("hr"))return "\n---\n";
        if(tag.equals("img"))return attributes.getOrDefault("alt","");
        String value=children.stream().map(RenderedNode::plainText).reduce("",String::concat);
        if(tag.equals("td")||tag.equals("th"))return value.stripTrailing()+"\t";
        if(tag.equals("tr"))return (value.endsWith("\t")?value.substring(0,value.length()-1):value)+"\n";
        return value+switch(tag){case "p","section","h3","figcaption"->"\n";case "div"->Arrays.asList(attributes.getOrDefault("class","").split("\\s+")).contains("domino-paragraph")?"\n":"";default->"";};
    }
    public String html(){
        if(tag.equals("#text"))return escape(text);var out=new StringBuilder("<"+tag);
        new TreeMap<>(attributes).forEach((k,v)->out.append(' ').append(k).append("=\"").append(escape(v)).append('"'));out.append('>');
        if(Set.of("br","hr","img","col").contains(tag))return out.toString();
        for(var child:children)out.append(child.html());return out.append("</").append(tag).append('>').toString();
    }
    public static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
