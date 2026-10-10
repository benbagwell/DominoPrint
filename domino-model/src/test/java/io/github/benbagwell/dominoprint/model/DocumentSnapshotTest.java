package io.github.benbagwell.dominoprint.model;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentSnapshotTest {
    @org.junit.jupiter.api.Test void rejectsExcessiveNestingAndPreservesInlineSpaces(){assertThrows(IllegalArgumentException.class,()->new DxlDocumentReader().read("<document><item name='Body'><richtext>"+"<par>".repeat(300)+"x"+"</par>".repeat(300)+"</richtext></item></document>"));var d=new DxlDocumentReader().read("<document><item name='Body'><richtext><par><run>a</run> <run>b</run></par></richtext></item></document>");assertEquals("a b\n",((DominoValue.RichText)d.item("Body").orElseThrow().values().get(0)).root().plainText());}
    @Test void preservesListsDuplicateItemsFlagsRichTextAndBinaries(){
        var doc=new DxlDocumentReader().read("""
            <document xmlns="http://www.lotus.com/dxl" form="Data"><noteinfo unid="ABCD" noteid="123" sequence="3"><created><datetime>20261010T120000,50-04</datetime></created></noteinfo>
              <item name="Names" names="true" summary="true"><textlist><text>Alice</text><text>Bob</text></textlist></item>
              <item name="Names"><text>duplicate</text></item>
              <item name="Amount"><number>123.4500</number></item>
              <item name="When"><datetimelist><datetime>20261010</datetime><datetime>T120000,25</datetime><datetimepair><datetime>20261010</datetime><datetime>20261012</datetime></datetimepair></datetimelist></item>
              <item name="Body"><richtext><par><run><font style="bold"/>Hello</run></par><table><tablerow><tablecell><par>A</par></tablecell><tablecell><par>B</par></tablecell></tablerow></table></richtext></item>
              <item name="$FILE"><object><file name="a.txt"><filedata>SGVsbG8=</filedata></file></object></item>
              <item name="Custom"><unfamiliar data="retained"/></item>
            </document>
            """);
        assertEquals("Data",doc.text("FORM"));assertEquals("ABCD",doc.unid());assertEquals(2,doc.items("names").size());assertEquals(List.of("Alice","Bob"),doc.item("NAMES").orElseThrow().textValues());assertEquals("true",doc.item("Names").orElseThrow().flags().get("names"));
        assertTrue(doc.item("When").orElseThrow().values().get(2) instanceof DominoValue.DateRange);
        var rich=(DominoValue.RichText)doc.item("Body").orElseThrow().values().get(0);assertTrue(rich.root().plainText().contains("Hello"));assertTrue(rich.root().sourceXml().contains("style=\"bold\""));
        var binary=(DominoValue.Binary)doc.item("$FILE").orElseThrow().values().get(0);assertEquals("Hello",new String(binary.content().bytes(),java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(doc.item("Custom").orElseThrow().values().get(0) instanceof DominoValue.Opaque);assertFalse(doc.diagnostics().isEmpty());assertThrows(UnsupportedOperationException.class,()->doc.items().clear());
    }
    @Test void binaryArraysAreDefensivelyCopied(){byte[] bytes={1,2,3};var b=new BinaryContent("a","x/test",bytes);bytes[0]=9;var copy=b.bytes();copy[1]=9;assertArrayEquals(new byte[]{1,2,3},b.bytes());assertEquals(64,b.sha256().length());}
    @Test void partialDatesDoNotBecomeArbitraryInstants(){var date=DominoDateTime.parse("20261010",false);assertTrue(date.time().isEmpty());assertThrows(IllegalStateException.class,()->date.toInstant(ZoneOffset.UTC));var time=DominoDateTime.parse("T120000,25",false);assertTrue(time.date().isEmpty());assertEquals(250_000_000,time.time().orElseThrow().getNano());var full=DominoDateTime.parse("20261010T120000,50-04",true);assertEquals(Instant.parse("2026-10-10T16:00:00.500Z"),full.toInstant(ZoneOffset.UTC));}
    @Test void sourceCollectionsAreCopied(){var metadata=new HashMap<String,String>();var items=new ArrayList<ItemSnapshot>();items.add(ItemSnapshot.text("Subject","Original"));var doc=new DocumentSnapshot(metadata,items,"","",List.of());items.clear();metadata.put("unid","changed");assertEquals("Original",doc.text("Subject"));assertEquals("",doc.unid());}
    @Test void xmlDoesNotResolveExternalEntities(){assertThrows(IllegalArgumentException.class,()->Dxl.parse("<!DOCTYPE document [<!ENTITY x SYSTEM 'file:///C:/Windows/win.ini'>]><document><item name='x'><text>&x;</text></item></document>"));}
    @Test void normalExternalDominoDoctypeIsAcceptedWithoutReadingIt(){assertEquals("document",Dxl.name(Dxl.parse("<!DOCTYPE document SYSTEM 'domino.dtd'><document/>").getDocumentElement()));}
    @Test void invalidValuesAreRetainedAndDiagnosed(){var doc=new DxlDocumentReader().read("<document><item name='N'><number>not a number</number></item></document>");assertTrue(doc.item("N").orElseThrow().values().get(0) instanceof DominoValue.Opaque);assertEquals(Diagnostic.Severity.ERROR,doc.diagnostics().get(0).severity());}
    @Test void documentSizeLimitIsEnforced(){assertThrows(IllegalArgumentException.class,()->new DxlDocumentReader(10).read("<document/>"));}
}
