package io.github.benbagwell.dominoprint.adapter;

import io.github.benbagwell.dominoprint.model.DocumentSnapshot;
import io.github.benbagwell.dominoprint.render.*;
import lotus.domino.*;

/** Optional compile-time typed facade, enabled by the domino-integration Maven profile. */
public final class Domino14Importer {
    private final DominoImporter importer;
    public Domino14Importer(Session session){importer=new DominoImporter(session);}
    public DocumentSnapshot capture(Document document){return importer.capture(document);}
    public CompiledPrintForm captureDesign(Document document){return importer.captureDesign(document);}
    public DominoImporter.DesignCapture captureDesigns(Database database){return importer.captureDesigns(database);}
    public ViewSnapshot captureView(View view){return importer.captureView(view);}
}
