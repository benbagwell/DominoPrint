package io.github.benbagwell.dominoprint.formula;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompatibilityCatalogTest {
    @Test void inventoryIncludesMissingImplementationsAndExcludedCommands(){var catalog=new CompatibilityCatalog(FunctionRegistry.standard());assertTrue(catalog.entries().size()>250);assertEquals(FunctionRegistry.Status.NOT_IMPLEMENTED,catalog.entries().stream().filter(e->e.name().equals("HashPassword")).findFirst().orElseThrow().status());assertEquals(CompatibilityCatalog.Eligibility.EXCLUDED,catalog.entries().stream().filter(e->e.name().equals("Command")).findFirst().orElseThrow().eligibility());}
    @Test void offlineTestsCannotPassProductionGate(){var report=new CompatibilityCatalog(FunctionRegistry.standard()).assess(List.of());assertFalse(report.ready());assertFalse(report.missingImplementations().isEmpty());assertFalse(report.missingEvidence().isEmpty());assertThrows(IllegalStateException.class,report::requireReady);}
    @Test void incompleteOrFailingLiveEvidenceRemainsVisible(){var evidence=new CompatibilityCatalog.Evidence("Abs","14.0",3,1,Set.of("scalar"));var report=new CompatibilityCatalog(FunctionRegistry.standard()).assess(List.of(evidence));assertTrue(report.failedFunctions().contains("Abs"));assertTrue(report.missingEvidence().contains("Abs"));}
}
