package io.github.benbagwell.dominoprint.adapter;

import lotus.domino.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Checks real Notes.jar signatures without opening a session or loading native code. */
class DominoApiContractTest {
    @Test void adapterMethodsExistInSuppliedRuntime() throws Exception {
        assertNotNull(ViewColumn.class.getMethod("getColumnValuesIndex"));
        assertNotNull(View.class.getMethod("isAutoUpdate"));
        assertNotNull(View.class.getMethod("setAutoUpdate",boolean.class));
        assertNotNull(DateTime.class.getMethod("getDateOnly"));
        assertNotNull(DateTime.class.getMethod("getTimeOnly"));
        for(String method:new String[]{"setOutputDOCTYPE","setForceNoteFormat","setOmitRichtextAttachments","setOmitRichtextPictures","setOmitOLEObjects","setOmitMiscFileObjects","setConvertNotesBitmapsToGIF","setExitOnFirstFatalError"})assertNotNull(DxlExporter.class.getMethod(method,boolean.class));
        assertNotNull(DxlExporter.class.getMethod("setRichTextOption",int.class));
    }
}
