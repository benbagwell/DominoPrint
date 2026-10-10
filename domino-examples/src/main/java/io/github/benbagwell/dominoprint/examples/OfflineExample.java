package io.github.benbagwell.dominoprint.examples;

import io.github.benbagwell.dominoprint.formula.*;
import io.github.benbagwell.dominoprint.model.*;
import io.github.benbagwell.dominoprint.render.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** A complete render using only detached DXL fixtures and JDK classes. */
public final class OfflineExample {
    private OfflineExample(){}
    public static void main(String[] args) throws IOException {
        Path output=Path.of(args.length==0?"examples/output":args[0]);Files.createDirectories(output);
        var reader=new DxlDocumentReader();var engine=new FormulaEngine();
        var data=reader.read(resource("data.dxl"));
        var codes=CodeTranslations.fromDocuments(List.of(reader.read(resource("code.dxl"))),new CodeTranslations.Mapping("CodeSet","Code","Translation"));
        var form=new PrintFormCompiler(engine).compile(resource("print-form.dxl"));
        var scope=new PrintCache.Scope("synthetic-example","offline-example");var cache=new PrintCache(scope);
        cache.refresh(()->new PrintCache.Bundle(scope,List.of(form),codes,SnapshotLookups.empty(codes),Map.of(),Map.of(),Map.of("example","synthetic-v1")));
        var context=EvaluationContext.of(data).withTime(Instant.parse("2026-10-10T12:00:00Z"),ZoneId.of("America/New_York"));
        var result=new PrintRenderer(engine).render(cache.snapshot(),"Order Print",data,context);
        Files.writeString(output.resolve("order.html"),result.html(),StandardCharsets.UTF_8);
        Files.writeString(output.resolve("order.txt"),result.plainText(),StandardCharsets.UTF_8);
        var catalog=new CompatibilityCatalog(engine.functions());var report=catalog.assess(List.of());
        Files.writeString(output.resolve("compatibility.md"),catalog.markdown(),StandardCharsets.UTF_8);
        Files.writeString(output.resolve("release-status.txt"),"Experimental; broad release ready: "+report.ready()+"\nMissing implementations: "+String.join(", ",report.missingImplementations())+"\nMissing Domino evidence: "+String.join(", ",report.missingEvidence())+"\n",StandardCharsets.UTF_8);
        if(args.length>1&&args[1].equals("--require-ready"))report.requireReady();
        System.out.println("Rendered HTML/text and compatibility inventory to "+output.toAbsolutePath());
        System.out.println("Broad release gate: "+report.missingImplementations().size()+" missing implementations; "+report.missingEvidence().size()+" functions need Domino evidence.");
    }
    private static String resource(String name) throws IOException {
        try(var stream=OfflineExample.class.getResourceAsStream("/examples/"+name)){if(stream==null)throw new FileNotFoundException(name);return new String(stream.readAllBytes(),StandardCharsets.UTF_8);}
    }
}
