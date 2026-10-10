package io.github.benbagwell.dominoprint.render;

import io.github.benbagwell.dominoprint.formula.*;
import io.github.benbagwell.dominoprint.model.*;
import java.util.*;

/** Thread-safe renderer; every invocation owns its field map, assets and computed layout. */
public final class PrintRenderer {
    private final FormulaEngine engine;
    private final PrintFormCompiler compiler;
    public PrintRenderer(FormulaEngine engine){this.engine=Objects.requireNonNull(engine);compiler=new PrintFormCompiler(engine);}
    public RenderResult render(PrintCache cache,String name,DocumentSnapshot document){return render(cache.snapshot(),name,document,EvaluationContext.of(document));}
    public RenderResult render(PrintCache.Version version,String name,DocumentSnapshot document,EvaluationContext context){
        var form=version.form(name);if(!form.kind().equals("form"))throw new IllegalArgumentException("Requested design element is not a form");
        var render=new Run(version,document,context.withLookups(version.lookups()));
        render.issues.addAll(document.diagnostics());render.issues.addAll(form.diagnostics());render.check();
        var expanded=render.prepare(form,new ArrayDeque<>());
        var layout=render.output(expanded,0);render.check();
        String html="<!doctype html><html><head><meta charset=\"utf-8\"><title>"+RenderedNode.escape(form.name())+"</title><style>body{font-family:Arial,sans-serif}.domino-paragraph{margin:0;white-space:pre-wrap}table{border-collapse:collapse}td{vertical-align:top}img{max-width:100%}@media print{a{color:inherit;text-decoration:none}}</style></head><body>"+layout.html()+"</body></html>";
        return new RenderResult(html,layout.plainText(),layout,render.assets,render.issues,version.number(),form.sourceVersion(),render.fields);
    }
    private final class Run {
        final PrintCache.Version cache;final DocumentSnapshot document;final EvaluationContext context;
        final Map<String,FormulaValue> fields;final Map<String,BinaryContent> assets=new LinkedHashMap<>(),attachments=new LinkedHashMap<>();
        final List<Diagnostic> issues=new ArrayList<>();int nodes,steps;boolean unverifiedReported;
        Run(PrintCache.Version cache,DocumentSnapshot document,EvaluationContext context){this.cache=cache;this.document=document;this.context=context;fields=new LinkedHashMap<>(context.fields());for(var item:document.items())for(var value:item.values())if(value instanceof DominoValue.Binary b)attachments.putIfAbsent(b.content().name(),b.content());}
        void check(){if(issues.stream().anyMatch(d->d.severity()==Diagnostic.Severity.ERROR))throw new RenderException(issues);}
        TemplateNode prepare(CompiledPrintForm form,Deque<String> path){
            String designKey=form.kind()+":"+form.name().toLowerCase(Locale.ROOT);
            if(path.size()>=64||path.contains(designKey))fail("RECURSIVE_DESIGN",form.name(),"Recursive or excessive subform expansion");
            issues.addAll(form.diagnostics());check();path.addLast(designKey);
            for(String dep:form.preparationDependencies()){
                var hook=cache.hooks().get(dep);if(hook==null)fail("MISSING_PREPARATION_HOOK",dep,"Missing Java preparation hook");
                Map<String,FormulaValue> prepared;
                try{prepared=Objects.requireNonNull(hook.prepare(Map.copyOf(fields),context.withFields(fields)));}catch(RuntimeException ex){fail("PREPARATION_FAILED",dep,Objects.requireNonNullElse(ex.getMessage(),"Preparation failed"));return null;}
                fields.clear();prepared.forEach((k,v)->fields.put(k.toLowerCase(Locale.ROOT),Objects.requireNonNull(v)));
            }
            var expanded=prepareNode(form.body(),path,0);path.removeLast();return expanded;
        }
        TemplateNode prepareNode(TemplateNode node,Deque<String> path,int depth){
            limit(depth);
            if(Set.of("subformref","sharedfieldref").contains(node.kind())){
                String name=node.attributes().getOrDefault("name","");
                if(!node.formulas().isEmpty())name=evaluate(node.formulas().values().iterator().next(),"subform").string();
                if(name.isEmpty())return new TemplateNode("richtext",Map.of(),"",List.of(),Map.of(),"");
                CompiledPrintForm sub;try{sub=cache.design(node.kind().equals("subformref")?"subform":"sharedfield",name);}catch(IllegalArgumentException e){fail("MISSING_DESIGN",name,e.getMessage());return null;}
                if(node.kind().equals("subformref")&&!sub.kind().equals("subform")||node.kind().equals("sharedfieldref")&&!sub.kind().equals("sharedfield"))fail("DESIGN_KIND",name,"Incorrect referenced design kind");
                return prepare(sub,path);
            }
            if(node.kind().equals("field")){
                String name=node.attributes().getOrDefault("name","");String key=name.toLowerCase(Locale.ROOT),kind=node.attributes().getOrDefault("kind","editable");
                boolean newDoc=context.attributes().getOrDefault("isnewdoc",FormulaValue.number(0)).truth();
                boolean compute=kind.equals("computedfordisplay")||kind.equals("computed")&&(newDoc||!fields.containsKey(key))||kind.equals("computedwhencomposed")&&newDoc;
                var formula=node.formulas().get(compute?"value":"defaultvalue");
                if(compute&&formula==null)fail("MISSING_FIELD_FORMULA",name,"Computed field has no value formula");
                if(compute||kind.equals("editable")&&newDoc&&!fields.containsKey(key)&&formula!=null){var value=evaluate(formula,name);if(value.type()==FormulaValue.Type.NOTHING)fields.remove(key);else fields.put(key,value);}
            }
            List<TemplateNode> children=new ArrayList<>();for(var child:node.children())children.add(prepareNode(child,path,depth+1));
            return new TemplateNode(node.kind(),node.attributes(),node.text(),children,node.formulas(),node.sourceXml());
        }
        FormulaValue evaluate(CompiledFormula formula,String location){
            if(!unverifiedReported){issues.add(Diagnostic.warning("FORMULA_CONFORMANCE_UNVERIFIED",location,"Offline formula behavior has not been certified against a live Domino reference"));unverifiedReported=true;}
            Map<String,FormulaValue> attrs=new LinkedHashMap<>(context.attributes());attrs.put("thisname",FormulaValue.text(location));attrs.put("thisvalue",fields.getOrDefault(location.toLowerCase(Locale.ROOT),FormulaValue.unavailable()));
            int remaining=context.maxSteps()-steps;if(remaining<1)fail("EVALUATION_LIMIT",location,"Render evaluation step budget exhausted");
            var c=new EvaluationContext(fields,attrs,context.locale(),context.zone(),context.now(),cache.lookups(),Math.max(1,remaining),context.maxDepth(),context.maxListElements());var result=engine.evaluate(formula,c);steps+=result.steps();
            if(result.value().isError())fail(result.value().error().code(),location,result.value().error().message());fields.clear();fields.putAll(result.workingFields());return result.value();
        }
        RenderedNode output(TemplateNode node,int depth){
            limit(depth);String kind=node.kind();
            Set<String> hide=new HashSet<>(Arrays.asList(node.attributes().getOrDefault("hide","").split("\\s+")));
            if(hide.contains("read")||hide.contains("web")||hide.contains("print"))return RenderedNode.text("");
            var hideWhen=node.formulas().get("hidewhen");if(hideWhen!=null){var value=evaluate(hideWhen,"hidewhen");try{if(value.truth())return RenderedNode.text("");}catch(IllegalArgumentException ex){fail("HIDEWHEN_TYPE","hidewhen","Hide-when formula must return a number");}}
            if(kind.equals("#text"))return RenderedNode.text(node.text());
            if(Set.of("pardef","parstyle","font","numberformat","datetimeformat","keywords","textlist","tablecolumn","border","code","formula","gif","png","jpeg","imageref").contains(kind))return RenderedNode.text("");
            if(kind.equals("computedtext")||kind.equals("sectiontitle")&&!node.formulas().isEmpty()){
                var formula=node.formulas().getOrDefault("value",node.formulas().values().stream().findFirst().orElse(null));if(formula==null)fail("MISSING_FORMULA",kind,"Missing computed text formula");return new RenderedNode(kind.equals("sectiontitle")?"h3":"span",htmlAttributes(node),"",List.of(RenderedNode.text(evaluate(formula,kind).display(separator(node)))));
            }
            if(kind.equals("field"))return field(node,depth);
            if(kind.equals("picture"))return picture(node,depth);
            if(kind.equals("attachmentref")){
                String name=node.attributes().getOrDefault("name","");var binary=attachments.get(name);if(binary==null)fail("MISSING_ATTACHMENT",name,"Attachment payload not captured");assets.put(binary.sha256(),binary);return new RenderedNode("a",Map.of("href",dataUri(binary),"download",name),"",List.of(RenderedNode.text(name)));
            }
            String tag=switch(kind){case "par"->"div";case "run","text"->"span";case "table"->"table";case "tablerow"->"tr";case "tablecell"->"td";case "break"->"br";case "horizrule"->"hr";case "section"->"section";case "sectiontitle"->"h3";case "caption"->"figcaption";case "urllink","doclink","viewlink","databaselink","namedelementlink"->"a";case "body","richtext","block","sharedfield"->"div";default->{fail("UNSUPPORTED_LAYOUT",kind,"Unsupported layout element");yield "div";}};
            Map<String,String> attrs=htmlAttributes(node);
            if(kind.equals("par"))attrs.put("class",(attrs.getOrDefault("class","")+" domino-paragraph").trim());
            if(tag.equals("a")){String href=node.attributes().getOrDefault("href","");if(!node.formulas().isEmpty())href=evaluate(node.formulas().values().iterator().next(),"link").string();if(!kind.equals("urllink")&&href.isEmpty()){issues.add(Diagnostic.warning("NOTES_LINK_TARGET",kind,"Notes link source retained; a web target was not available"));}if(!href.isEmpty()){if(!safeUrl(href))fail("UNSUPPORTED_LINK",kind,"Unsupported URI scheme");attrs.put("href",href);}}
            List<RenderedNode> children=new ArrayList<>();for(var child:node.children())children.add(output(child,depth+1));
            if(tag.equals("table")){var cols=node.children().stream().filter(c->c.kind().equals("tablecolumn")).map(c->new RenderedNode("col",htmlAttributes(c),"",List.of())).toList();if(!cols.isEmpty())children.add(0,new RenderedNode("colgroup",Map.of(),"",cols));}
            return new RenderedNode(tag,attrs,"",children);
        }
        RenderedNode field(TemplateNode node,int depth){
            String name=node.attributes().getOrDefault("name","");String type=node.attributes().getOrDefault("type","text");
            if(type.equals("richtext")){
                var item=document.item(name);if(item.isEmpty())return RenderedNode.text("");List<RenderedNode> children=new ArrayList<>();for(var value:item.get().values())if(value instanceof DominoValue.RichText rich){var tree=compiler.compileRichText(rich.root(),issues);check();children.add(output(tree,depth+1));}else fail("UNSUPPORTED_RICH_TEXT",name,"Rich text has no decoded printable representation");return new RenderedNode("div",htmlAttributes(node),"",children);
            }
            var value=fields.getOrDefault(name.toLowerCase(Locale.ROOT),FormulaValue.text(""));if(value.isError())fail(value.error().code(),name,value.error().message());
            String format="";for(var c:node.children()){
                if(c.kind().equals("numberformat")){String f=c.attributes().getOrDefault("format","general");format=switch(f){case "general"->"G";case "fixed"->"F";case "scientific"->"S";case "currency"->"C";default->{fail("UNSUPPORTED_FORMAT",name,"Number format: "+f);yield "";}};format+=c.attributes().getOrDefault("digits","");if("true".equals(c.attributes().get("punctuated")))format+=",";if("true".equals(c.attributes().get("percent")))format+="%";if("true".equals(c.attributes().get("parentheses")))format+="()";}
                if(c.kind().equals("datetimeformat")){String show=c.attributes().getOrDefault("show","both");format=switch(show){case "date"->"S0";case "time"->"S1";case "both","datetime"->"S2";default->{fail("UNSUPPORTED_FORMAT",name,"Datetime format: "+show);yield "";}};}
            }
            List<String> strings=new ArrayList<>();for(var atom:value.elements()){
                if(atom instanceof Double number)strings.add(FunctionRegistry.formatNumber(number,format,context.locale()));else if(atom instanceof DominoDateTime date)strings.add(FunctionRegistry.formatDate(date,format,context));else strings.add(atom.toString());
            }
            // Notes keyword aliases use displayed-label|stored-value pairs.
            for(var keywords:node.children())if(keywords.kind().equals("keywords")){
                List<String> choices=new ArrayList<>();collectText(keywords,choices);var formula=node.formulas().get("choices");if(formula==null)formula=keywords.formulas().get("choices");if(formula==null)formula=keywords.formulas().get("value");if(formula!=null)choices=evaluate(formula,name).elements().stream().map(Object::toString).toList();Map<String,String> labels=new HashMap<>();for(String choice:choices){int bar=choice.lastIndexOf('|');if(bar>=0)labels.put(choice.substring(bar+1),choice.substring(0,bar));}strings.replaceAll(x->labels.getOrDefault(x,x));
            }
            Map<String,String> attrs=htmlAttributes(node);attrs.put("data-field",name);return new RenderedNode("span",attrs,"",List.of(RenderedNode.text(String.join(separator(node),strings))));
        }
        RenderedNode picture(TemplateNode node,int depth){
            BinaryContent binary=null;String alt=node.attributes().getOrDefault("alttext","");
            for(var child:node.children()){
                if(Set.of("gif","png","jpeg").contains(child.kind())){String encoded=Dxl.parse(child.sourceXml()).getDocumentElement().getTextContent();try{binary=new BinaryContent("inline."+child.kind(),"image/"+child.kind(),DxlDocumentReader.decode(encoded));}catch(IllegalArgumentException e){fail("INVALID_IMAGE","picture","Invalid encoded image");}}
                if(child.kind().equals("imageref")){String name=child.attributes().getOrDefault("name","");if(!child.formulas().isEmpty())name=evaluate(child.formulas().values().iterator().next(),"image").string();binary=cache.resources().get(name);if(binary==null)fail("MISSING_IMAGE",name,"Image resource not cached");}
            }
            if(binary==null)fail("MISSING_IMAGE","picture","No supported image payload");assets.put(binary.sha256(),binary);var attrs=htmlAttributes(node);attrs.put("src",dataUri(binary));attrs.put("alt",alt);var image=new RenderedNode("img",attrs,"",List.of());List<RenderedNode> captions=new ArrayList<>();captions.add(image);for(var child:node.children())if(child.kind().equals("caption"))captions.add(output(child,depth+1));return captions.size()==1?image:new RenderedNode("figure",Map.of(),"",captions);
        }
        void limit(int depth){if(depth>128||++nodes>100_000)fail("LAYOUT_LIMIT","layout","Layout expansion limit exceeded");}
        void fail(String code,String location,String message){issues.add(Diagnostic.error(code,location,message));throw new RenderException(issues);}
    }
    private static String dataUri(BinaryContent binary){return "data:"+binary.mediaType()+";base64,"+Base64.getEncoder().encodeToString(binary.bytes());}
    private static boolean safeUrl(String value){return value.matches("(?i)^(?:https?://|mailto:|notes:|#|/).*" )||!value.contains(":");}
    private static void collectText(TemplateNode node,List<String> out){if(node.kind().equals("text")){out.add(node.children().stream().filter(c->c.kind().equals("#text")).map(TemplateNode::text).reduce("",String::concat));return;}for(var child:node.children())collectText(child,out);}
    private static String separator(TemplateNode node){return switch(node.attributes().getOrDefault("listdisplayseparator","semicolon")){case "space"->" ";case "comma"->", ";case "newline"->"\n";case "blankline"->"\n\n";case "none"->"";default->"; ";};}
    private static Map<String,String> htmlAttributes(TemplateNode node){
        Map<String,String> out=new LinkedHashMap<>();var attrs=node.attributes();StringBuilder css=new StringBuilder();
        for(var mapping:Map.of("htmlid","id","htmlclass","class","htmltitle","title").entrySet())if(attrs.containsKey(mapping.getKey()))out.put(mapping.getValue(),attrs.get(mapping.getKey()));
        for(var mapping:Map.of("leftmargin","margin-left","rightmargin","margin-right","width","width","height","height","spacebefore","margin-top","spaceafter","margin-bottom").entrySet()){String value=attrs.get(mapping.getKey());if(value!=null&&value.matches("[0-9]+(?:\\.[0-9]+)?(?:in|cm|mm|pt|px|%|em)"))css.append(mapping.getValue()).append(':').append(value).append(';');}
        String align=attrs.get("align");if(align!=null&&Set.of("left","right","center","full").contains(align))css.append("text-align:").append(align.equals("full")?"justify":align).append(';');
        String color=attrs.get("bgcolor");if(color!=null&&color.matches("(?:#[0-9a-fA-F]{3,8}|[a-zA-Z]+)"))css.append("background-color:").append(color.equals("none")?"transparent":color).append(';');
        if("true".equals(attrs.get("newpage")))css.append("break-before:page;");if("true".equals(attrs.get("keeptogether")))css.append("break-inside:avoid;");if("true".equals(attrs.get("keepwithnext")))css.append("break-after:avoid;");
        for(var font:node.children())if(font.kind().equals("font")){
            String size=font.attributes().get("size"),face=font.attributes().get("name"),fg=font.attributes().get("color");if(size!=null&&size.matches("[0-9]+(?:\\.[0-9]+)?pt"))css.append("font-size:").append(size).append(';');if(face!=null&&face.matches("[a-zA-Z0-9 _-]+"))css.append("font-family:'").append(face).append("';");if(fg!=null&&fg.matches("(?:#[0-9a-fA-F]{3,8}|[a-zA-Z]+)"))css.append("color:").append(fg).append(';');String style=font.attributes().getOrDefault("style","");if(style.contains("bold"))css.append("font-weight:bold;");if(style.contains("italic"))css.append("font-style:italic;");if(style.contains("underline"))css.append("text-decoration:underline;");
        }
        if(node.kind().equals("tablecell")){
            for(var key:List.of("colspan","rowspan")){String value=attrs.get(key);if(value!=null&&value.matches("[1-9][0-9]{0,3}"))out.put(key,value);}
            String border=attrs.get("borderwidth");if(border!=null&&border.matches("[0-9]+px"))css.append("border:").append(border).append(" solid;");
        }
        if(!css.isEmpty())out.put("style",css.toString());return out;
    }
}
