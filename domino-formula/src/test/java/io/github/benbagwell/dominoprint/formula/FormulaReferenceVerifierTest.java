package io.github.benbagwell.dominoprint.formula;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FormulaReferenceVerifierTest {
    final FormulaEngine engine=new FormulaEngine();
    FormulaReferenceVerifier.Case testCase(String id,String source){return new FormulaReferenceVerifier.Case(id,"Abs","scalar",source,EvaluationContext.empty());}
    @Test void reportsIndependentMismatchAndReferenceFailure(){
        var report=new FormulaReferenceVerifier(engine).verify("14.0",List.of(testCase("mismatch","@Abs(-2)"),testCase("failed","@Abs(-3)")),c->{if(c.id().equals("failed"))throw new IllegalStateException("No server");return new FormulaReferenceVerifier.Observation(FormulaValue.text("2"),Map.of(),"independent reference");});
        assertEquals(2,report.failures());assertEquals(2,report.evidence().get(0).failures());assertThrows(IllegalStateException.class,report::requireMatching);
    }
    @Test void detectsWorkingFieldChangesEvenWhenReturnedValueMatches(){
        var report=new FormulaReferenceVerifier(engine).verify("14.0",List.of(testCase("field","FIELD Changed := 1; @Abs(-2)")),c->new FormulaReferenceVerifier.Observation(FormulaValue.number(2),Map.of(),"reference"));assertEquals(1,report.failures());
    }
    @Test void neverCertifiesUntestedFunctions(){
        var report=new FormulaReferenceVerifier(engine).verify("14.0",List.of(testCase("ok","@Abs(-2)")),c->new FormulaReferenceVerifier.Observation(FormulaValue.number(2),Map.of(),"independent reference"));assertEquals(0,report.failures());assertFalse(new CompatibilityCatalog(engine.functions()).assess(report.evidence()).ready());
        assertThrows(IllegalArgumentException.class,()->new FormulaReferenceVerifier(engine).verify("14.0",List.of(testCase("wrong","@Pi")),c->null));
    }
    @Test void caseSensitiveListsAndCp850Characters(){
        var c=EvaluationContext.empty();assertEquals(0,engine.evaluate("@IsMember(\"A\";\"a\")",c).value().number());
        assertEquals(0,engine.evaluate("@IsNotMember(\"a\":\"z\";\"a\":\"b\")",c).value().number());
        assertEquals("A;a",engine.evaluate("@Unique(\"A\":\"a\":\"A\")",c).value().display(";"));
        assertEquals("é",engine.evaluate("@Char(130)",c).value().string());
        assertEquals(1,engine.evaluate("REM comment;".repeat(20_000)+"1",c).value().number());
    }
}
