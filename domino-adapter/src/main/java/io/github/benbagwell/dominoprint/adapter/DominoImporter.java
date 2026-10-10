package io.github.benbagwell.dominoprint.adapter;

import io.github.benbagwell.dominoprint.model.*;
import io.github.benbagwell.dominoprint.formula.*;
import io.github.benbagwell.dominoprint.render.*;
import org.w3c.dom.Element;
import java.time.*;
import java.util.*;

/**
 * Thread-confined import adapter for a caller-owned lotus.domino.Session.
 * Only adapter-created exporters, collections, entries and document wrappers are recycled.
 * The caller owns NotesThread initialization and the Session/Database/Document arguments.
 */
public final class DominoImporter {
    public record Options(boolean preserveRawNote,int maxDxlCharacters){public Options{if(maxDxlCharacters<1)throw new IllegalArgumentException("maxDxlCharacters");}public static Options defaults(){return new Options(true,64*1024*1024);}}
    public record DesignCapture(List<CompiledPrintForm> forms,Map<String,BinaryContent> resources,List<Diagnostic> diagnostics,String sourceDxl){public DesignCapture{forms=List.copyOf(forms);resources=Map.copyOf(resources);diagnostics=List.copyOf(diagnostics);}}
    private record Export(String dxl,List<Diagnostic> diagnostics){}
    private final Object session;
    private final Thread owner=Thread.currentThread();
    private final Options options;
    private final PrintFormCompiler compiler;
    private final DxlDocumentReader reader;
    public DominoImporter(Object session){this(session,Options.defaults(),new FormulaEngine());}
    public DominoImporter(Object session,Options options,FormulaEngine engine){this.session=Objects.requireNonNull(session);this.options=Objects.requireNonNull(options);compiler=new PrintFormCompiler(engine);reader=new DxlDocumentReader(options.maxDxlCharacters);}
    public DocumentSnapshot capture(Object document){
        owner();Map<String,String> metadata=sourceMetadata(DominoAccess.call(document,"getParentDatabase"));
        Export structured=export(document,false);Export raw=options.preserveRawNote?export(document,true):new Export("",List.of());
        List<Diagnostic> issues=new ArrayList<>(structured.diagnostics);issues.addAll(raw.diagnostics);
        if(!raw.dxl.isEmpty()&&!version(structured.dxl).equals(version(raw.dxl)))throw new DominoImportException("Document changed between structured and raw exports; retry capture");
        return reader.read(structured.dxl,raw.dxl,metadata,issues);
    }
    public CompiledPrintForm captureDesign(Object designDocument){owner();var result=export(designDocument,false);checkErrors(result.diagnostics);return compiler.compile(result.dxl);}
    public List<DocumentSnapshot> captureDocuments(Object collection){
        owner();List<DocumentSnapshot> result=new ArrayList<>();Object document=DominoAccess.call(collection,"getFirstDocument");
        try{while(document!=null){Object next=null;try{next=DominoAccess.call(collection,"getNextDocument",document);result.add(capture(document));}catch(RuntimeException ex){DominoAccess.recycle(next);throw ex;}finally{DominoAccess.recycle(document);}document=next;}}finally{if(document!=null){/* Current wrapper is recycled in the loop's finally. */}}
        return List.copyOf(result);
    }
    public DesignCapture captureDesigns(Object database){
        owner();Object collection=DominoAccess.call(database,"createNoteCollection",false);
        try{for(String kind:List.of("Forms","Subforms","SharedFields","ImageResources"))DominoAccess.call(collection,"setSelect"+kind,true);DominoAccess.call(collection,"buildCollection");var exported=export(collection,false);checkErrors(exported.diagnostics);return readDesigns(exported.dxl,compiler,exported.diagnostics);}
        finally{DominoAccess.recycle(collection);}
    }
    public static DesignCapture readDesigns(String dxl,PrintFormCompiler compiler,List<Diagnostic> exportDiagnostics){
        var root=Dxl.parse(dxl).getDocumentElement();List<Element> elements=Dxl.name(root).equals("database")?Dxl.children(root):List.of(root);List<CompiledPrintForm> forms=new ArrayList<>();Map<String,BinaryContent> resources=new LinkedHashMap<>();List<Diagnostic> issues=new ArrayList<>(exportDiagnostics);
        for(var e:elements){String kind=Dxl.name(e);if(Set.of("form","subform","sharedfield").contains(kind)){var form=compiler.compile(Dxl.xml(e));forms.add(form);issues.addAll(form.diagnostics());}
            else if(kind.equals("imageresource")){var binary=image(e,issues);if(binary!=null){List<String> names=new ArrayList<>(Arrays.stream(e.getAttribute("alias").split("\\|")).filter(x->!x.isBlank()).toList());names.add(e.getAttribute("name"));for(var name:names)if(resources.putIfAbsent(name,binary)!=null)throw new IllegalArgumentException("Duplicate image resource: "+name);}}
        }
        return new DesignCapture(forms,resources,issues,dxl);
    }
    private static BinaryContent image(Element e,List<Diagnostic> issues){
        for(String kind:List.of("gif","jpeg","png")){var parts=Dxl.descendants(e,kind);if(!parts.isEmpty())try{return new BinaryContent(e.getAttribute("name"),"image/"+kind,DxlDocumentReader.decode(parts.get(0).getTextContent()));}catch(IllegalArgumentException ex){issues.add(Diagnostic.error("INVALID_IMAGE",e.getAttribute("name"),"Invalid image resource payload"));return null;}}
        issues.add(Diagnostic.warning("OPAQUE_IMAGE_RESOURCE",e.getAttribute("name"),"Image source retained in design DXL; supported payload not found"));return null;
    }
    /** Column numbers and the first-sorted lookup key are read from Domino's actual view-column indexes. */
    public ViewSnapshot captureView(Object view){
        return captureView(view,ZoneId.systemDefault());
    }
    /** The zone must match the Domino host's regional settings for partial date/time values. */
    public ViewSnapshot captureView(Object view,ZoneId zone){
        owner();boolean autoUpdate=(Boolean)DominoAccess.call(view,"isAutoUpdate");
        try{DominoAccess.call(view,"setAutoUpdate",false);return captureStableView(view,Objects.requireNonNull(zone));}
        finally{DominoAccess.call(view,"setAutoUpdate",autoUpdate);}
    }
    private ViewSnapshot captureStableView(Object view,ZoneId zone){
        owner();Object database=DominoAccess.call(view,"getParent");Map<String,String> metadata=sourceMetadata(database);String name=DominoAccess.string(view,"getName");List<String> aliases=strings(DominoAccess.call(view,"getAliases"));
        List<?> columns=list(DominoAccess.call(view,"getColumns"));int firstSorted=-1;Set<Integer> indexes=new TreeSet<>();
        try{for(Object column:columns){int index=((Number)DominoAccess.call(column,"getColumnValuesIndex")).intValue();if(index>=0&&index!=65535){indexes.add(index);if(firstSorted<0&&(Boolean)DominoAccess.call(column,"isSorted"))firstSorted=index;}}}finally{for(Object column:columns)DominoAccess.recycle(column);}
        if(firstSorted<0)throw new DominoImportException("View has no sorted lookup column");
        Object entries=DominoAccess.call(view,"getAllEntries"),entry=null;List<ViewSnapshot.Row> rows=new ArrayList<>();
        try{entry=DominoAccess.call(entries,"getFirstEntry");while(entry!=null){Object next=null;try{next=DominoAccess.call(entries,"getNextEntry",entry);if((Boolean)DominoAccess.call(entry,"isDocument")){
                    var values=list(DominoAccess.call(entry,"getColumnValues"));Map<Integer,FormulaValue> rowColumns=new LinkedHashMap<>();
                    try{for(int i:indexes){if(i>=values.size())throw new DominoImportException("Missing view column value at index "+i);rowColumns.put(i+1,nativeValue(values.get(i),zone));}}finally{recycleValues(values,Collections.newSetFromMap(new IdentityHashMap<>()));}
                    Object document=DominoAccess.call(entry,"getDocument");try{rows.add(new ViewSnapshot.Row(rowColumns.get(firstSorted+1),rowColumns,capture(document)));}finally{DominoAccess.recycle(document);}
                }}catch(RuntimeException ex){DominoAccess.recycle(next);throw ex;}finally{DominoAccess.recycle(entry);}entry=next;}
        }finally{DominoAccess.recycle(entries);}
        return new ViewSnapshot(metadata.get("server"),metadata.get("dbpath"),name,aliases,rows,zone);
    }
    private static void recycleValues(Object value,Set<Object> seen){if(value instanceof Collection<?> c){for(Object v:c)recycleValues(v,seen);}else if(value!=null&&!(value instanceof String)&&!(value instanceof Number)&&!(value instanceof java.util.Date)&&seen.add(value))DominoAccess.recycle(value);}
    private FormulaValue nativeValue(Object value,ZoneId zone){
        if(value instanceof String text)return FormulaValue.text(text);if(value instanceof Number number)return FormulaValue.number(number.doubleValue());
        if(value instanceof Collection<?> values){var result=FormulaValue.nothing();for(Object part:values)result=result.append(nativeValue(part,zone));return result;}
        if(value instanceof java.util.Date date)return FormulaValue.date(DominoDateTime.of(date.toInstant().atZone(ZoneOffset.UTC)));
        if(value!=null){boolean anyDate=DominoAccess.string(value,"getDateOnly").isBlank(),anyTime=DominoAccess.string(value,"getTimeOnly").isBlank();var date=(java.util.Date)DominoAccess.call(value,"toJavaDate");var dt=date.toInstant().atZone(zone);return FormulaValue.date(new DominoDateTime(anyDate?Optional.empty():Optional.of(dt.toLocalDate()),anyTime?Optional.empty():Optional.of(dt.toLocalTime()),anyDate||anyTime?Optional.empty():Optional.of(dt.getOffset()),(Boolean)DominoAccess.call(value,"isDST"),DominoAccess.string(value,"getLocalTime")));}
        return FormulaValue.unavailable();
    }
    private Export export(Object object,boolean raw){
        Object exporter=DominoAccess.call(session,"createDxlExporter");
        try{DominoAccess.call(exporter,"setOutputDOCTYPE",false);DominoAccess.call(exporter,"setForceNoteFormat",raw);DominoAccess.call(exporter,"setRichTextOption",raw?1:0);DominoAccess.call(exporter,"setOmitRichtextAttachments",false);DominoAccess.call(exporter,"setOmitRichtextPictures",false);DominoAccess.call(exporter,"setOmitOLEObjects",false);DominoAccess.call(exporter,"setOmitMiscFileObjects",false);DominoAccess.call(exporter,"setConvertNotesBitmapsToGIF",!raw);DominoAccess.call(exporter,"setExitOnFirstFatalError",true);
            String dxl=Objects.toString(DominoAccess.call(exporter,"exportDxl",object),"");if(dxl.length()>options.maxDxlCharacters)throw new DominoImportException("Export exceeds configured DXL limit");var issues=log(DominoAccess.string(exporter,"getLog"));checkErrors(issues);return new Export(dxl,issues);
        }finally{DominoAccess.recycle(exporter);}
    }
    private Map<String,String> sourceMetadata(Object database){var out=new LinkedHashMap<String,String>();out.put("server",DominoAccess.string(database,"getServer"));out.put("dbpath",DominoAccess.string(database,"getFilePath"));out.put("replicaid",DominoAccess.string(database,"getReplicaID"));out.put("dbtitle",DominoAccess.string(database,"getTitle"));out.put("loadingidentity",DominoAccess.string(session,"getEffectiveUserName"));return Map.copyOf(out);}
    private static String version(String source){var root=Dxl.parse(source).getDocumentElement();return Dxl.child(root,"noteinfo").map(e->e.getAttribute("unid")+":"+e.getAttribute("sequence")).orElseThrow(()->new DominoImportException("Export has no note identity/version; cannot validate consistent capture"));}
    private static List<Diagnostic> log(String log){if(log.isBlank())return List.of();var root=Dxl.parse(log).getDocumentElement();List<Diagnostic> issues=new ArrayList<>();for(String kind:List.of("error","fatalerror","warning"))for(var e:Dxl.descendants(root,kind))issues.add(new Diagnostic(kind.equals("warning")?Diagnostic.Severity.WARNING:Diagnostic.Severity.ERROR,"DXL_EXPORT_"+kind.toUpperCase(Locale.ROOT),e.getAttribute("id"),e.getTextContent().trim()));return List.copyOf(issues);}
    private static void checkErrors(List<Diagnostic> issues){if(issues.stream().anyMatch(d->d.severity()==Diagnostic.Severity.ERROR))throw new DominoImportException("DXL export failed: "+issues);}
    private static List<?> list(Object value){if(value instanceof List<?> list)return list;throw new DominoImportException("Expected a Domino Vector");}
    private static List<String> strings(Object value){return list(value).stream().map(Object::toString).toList();}
    private void owner(){if(Thread.currentThread()!=owner)throw new DominoImportException("DominoImporter must be used on its creating Domino thread");}
}
