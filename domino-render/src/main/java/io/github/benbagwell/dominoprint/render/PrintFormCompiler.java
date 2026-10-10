package io.github.benbagwell.dominoprint.render;

import io.github.benbagwell.dominoprint.formula.*;
import io.github.benbagwell.dominoprint.model.*;
import org.w3c.dom.*;
import java.util.*;

/** Imports Designer form/subform/shared-field DXL and audits executable dependencies. */
public final class PrintFormCompiler {
    private final FormulaEngine engine;
    private static final Set<String> SUPPORTED=Set.of("richtext","body","sharedfield","par","pardef","parstyle","run","font","text","break","table","tablecolumn","tablerow","tablecell","border","field","keywords","textlist","numberformat","datetimeformat","computedtext","section","sectiontitle","subformref","sharedfieldref","picture","gif","png","jpeg","imageref","caption","attachmentref","urllink","doclink","viewlink","databaselink","namedelementlink","horizrule","block","code","formula","javascript","lotusscript","javaproject","simpleaction","#text");
    public PrintFormCompiler(FormulaEngine engine){this.engine=Objects.requireNonNull(engine);}
    public CompiledPrintForm compile(String source){
        var root=Dxl.parse(source).getDocumentElement();String kind=Dxl.name(root);
        if(!Set.of("form","subform","sharedfield").contains(kind))throw new IllegalArgumentException("Expected form, subform or sharedfield DXL");
        String name=root.getAttribute("name");if(name.isEmpty())throw new IllegalArgumentException("Design element has no name");
        List<Diagnostic> issues=new ArrayList<>();Set<String> dependencies=new LinkedHashSet<>();
        audit(root,name,dependencies,issues);
        Element body=kind.equals("sharedfield")?root:Dxl.child(root,"body").flatMap(b->Dxl.child(b,"richtext")).orElseThrow(()->new IllegalArgumentException("Design element has no body/richtext"));
        Map<String,Element> paragraphs=new HashMap<>();for(var p:Dxl.descendants(body,"pardef")){if(paragraphs.putIfAbsent(p.getAttribute("id"),p)!=null)issues.add(Diagnostic.error("DUPLICATE_PARAGRAPH_DEFINITION",name,"Duplicate paragraph definition: "+p.getAttribute("id")));}
        var tree=node(body,paragraphs,issues,name,0);
        List<String> aliases=Arrays.stream(root.getAttribute("alias").split("\\|" )).filter(x->!x.isBlank()).toList();
        String version=Dxl.child(root,"noteinfo").map(e->e.getAttribute("unid")+":"+e.getAttribute("sequence")).orElse("");
        return new CompiledPrintForm(name,aliases,kind,tree,source,version,issues,dependencies);
    }
    public TemplateNode compileRichText(RichTextNode root,List<Diagnostic> issues){var e=Dxl.parse(root.sourceXml()).getDocumentElement();Map<String,Element> defs=new HashMap<>();for(var p:Dxl.descendants(e,"pardef"))defs.put(p.getAttribute("id"),p);return node(e,defs,issues,"richtext",0);}
    private TemplateNode node(Element e,Map<String,Element> paragraphs,List<Diagnostic> issues,String location,int depth){
        if(depth>128)throw new IllegalArgumentException("Layout nesting limit exceeded");String kind=Dxl.name(e);String here=location+"/"+kind+(e.hasAttribute("name")?"["+e.getAttribute("name")+"]":"");
        if(!SUPPORTED.contains(kind))issues.add(Diagnostic.error("UNSUPPORTED_LAYOUT",here,"Layout element retained but cannot be rendered accurately: "+kind));
        Map<String,String> attrs=new LinkedHashMap<>(Dxl.attributes(e));Map<String,CompiledFormula> formulas=new LinkedHashMap<>();
        if(kind.equals("par")&&e.hasAttribute("def")){
            var definition=paragraphs.get(e.getAttribute("def"));if(definition==null)issues.add(Diagnostic.error("MISSING_PARAGRAPH_DEFINITION",here,"Paragraph definition not found: "+e.getAttribute("def")));
            else{Map<String,String> merged=new LinkedHashMap<>(Dxl.attributes(definition));merged.putAll(attrs);attrs=merged;formulas.putAll(formulas(definition,issues,here));}
        }
        formulas.putAll(formulas(e,issues,here));
        for(var attr:List.of("htmlstyle","extrahtmlattrs"))if(e.hasAttribute(attr)&&!e.getAttribute(attr).isBlank())issues.add(Diagnostic.error("UNSUPPORTED_HTML_ATTRIBUTES",here,"Raw "+attr+" requires an explicit rendering extension"));
        if(kind.equals("field")&&!Set.of("editable","computed","computedfordisplay","computedwhencomposed").contains(attrs.getOrDefault("kind","editable")))issues.add(Diagnostic.error("UNSUPPORTED_FIELD_KIND",here,"Unknown field kind"));
        List<TemplateNode> children=new ArrayList<>();
        for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling()){
            if(n instanceof Element c){if(!Dxl.name(c).equals("code")&&!(kind.equals("sharedfield")&&Set.of("noteinfo","item","updatedby","wassignedby").contains(Dxl.name(c))))children.add(node(c,paragraphs,issues,here,depth+1));}
            else if((n.getNodeType()==Node.TEXT_NODE||n.getNodeType()==Node.CDATA_SECTION_NODE)&&(!n.getNodeValue().isBlank()||Set.of("run","text").contains(kind)||kind.equals("par")&&!n.getNodeValue().contains("\n")))children.add(new TemplateNode("#text",Map.of(),n.getNodeValue(),List.of(),Map.of(),""));
        }
        return new TemplateNode(kind,attrs,"",children,formulas,Dxl.xml(e));
    }
    private Map<String,CompiledFormula> formulas(Element e,List<Diagnostic> issues,String location){
        Map<String,CompiledFormula> result=new LinkedHashMap<>();
        for(var code:Dxl.children(e,"code")){
            if(code.getAttribute("enabled").equals("false")||code.getAttribute("for").equals("notes"))continue;
            String event=code.getAttribute("event").toLowerCase(Locale.ROOT);
            if(!Set.of("value","defaultvalue","hidewhen","subform","sectiontitle","computed","htmlattributes","inputtranslation","inputvalidation","choices").contains(event))continue;
            if(event.equals("htmlattributes")){issues.add(Diagnostic.error("UNSUPPORTED_HTML_ATTRIBUTES",location,"Computed HTML attributes require an extension"));continue;}
            var formula=Dxl.child(code,"formula");if(formula.isEmpty()){issues.add(Diagnostic.error("NON_FORMULA_COMPUTATION",location,"Event "+event+" is not a formula"));continue;}
            try{var compiled=engine.compile(formula.get().getTextContent());for(String fn:compiled.functions())if(!engine.functions().implemented(fn))issues.add(Diagnostic.error("UNSUPPORTED_FUNCTION",location,"@"+fn+" cannot execute offline"));result.put(event,compiled);}
            catch(FormulaSyntaxException ex){issues.add(Diagnostic.error("FORMULA_SYNTAX",location,ex.getMessage()));}
        }
        return result;
    }
    private static void audit(Element root,String name,Set<String> dependencies,List<Diagnostic> issues){
        for(var code:Dxl.descendants(root,"code")){
            if(code.getAttribute("enabled").equals("false")||code.getAttribute("for").equals("notes"))continue;
            String event=code.getAttribute("event").toLowerCase(Locale.ROOT);
            if(Set.of("webqueryopen","queryopen","postopen","onload").contains(event)){
                String dependency=name+":"+event;dependencies.add(dependency);issues.add(Diagnostic.warning("PREPARATION_HOOK_REQUIRED",dependency,"Rendering requires a Java replacement or an explicitly verified no-op for this event"));
            }
        }
    }
}
