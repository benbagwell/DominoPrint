package io.github.benbagwell.dominoprint.formula;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class FormulaEngineTest {
    final FormulaEngine engine=new FormulaEngine();
    static Stream<Object[]> examples(){return Stream.of(
        new Object[]{"3 + 6 * 4 - 2","25"},new Object[]{"(5 - 3) * (6 - 4)","4"},new Object[]{"8 / 4 * 2","4"},
        new Object[]{"1:2:3 + 10:20","11;22;23"},new Object[]{"1:2 *+ 10:20","11;21;12;22"},new Object[]{"1:2 = 1:3","1"},new Object[]{"1:2 != 1:3","1"},new Object[]{"\"A\":\"B\":\"C\" + \"1\":\"2\"","A1;B2;C2"},
        new Object[]{"@If(1;\"yes\";1/0)","yes"},new Object[]{"@If(0;\"a\";0;\"b\";\"c\")","c"},new Object[]{"@IfError(1/0;\"fallback\")","fallback"},new Object[]{"@IsError(1/0)","1"},
        new Object[]{"x := 2; y := 3; @Do(x + y; x * y)","6"},new Object[]{"@Return(\"done\");1/0","done"},new Object[]{"@Eval(\"1+2\")","3"},new Object[]{"@Eval(\"1+2\":\"3+4\")","7"},
        new Object[]{"@Transform(1:2:3;\"x\";@If(x=2;@Nothing;x*2))","2;6"},new Object[]{"@Subset(\"a\":\"b\":\"c\";-2)","b;c"},new Object[]{"@Count(1:2:3)","3"},
        new Object[]{"@Trim(\"  hello   world  \")","hello world"},new Object[]{"@UpperCase(\"ab\":\"cd\")","AB;CD"},new Object[]{"@LowerCase(\"AB\")","ab"},new Object[]{"@ProperCase(\"hello WORLD\")","Hello World"},
        new Object[]{"@Left(\"abcdef\";3)","abc"},new Object[]{"@Right(\"abcdef\";3)","def"},new Object[]{"@LeftBack(\"abcdef\";2)","abcd"},new Object[]{"@RightBack(\"abcdef\";2)","cdef"},
        new Object[]{"@Left(\"first middle last\";\" \")","first"},new Object[]{"@RightBack(\"first middle last\";\" \")","last"},new Object[]{"@Middle(\"abcdef\";2;2)","cd"},new Object[]{"@Word(\"a,b,c\";\",\";2)","b"},
        new Object[]{"@ReplaceSubstring(\"first\";\"first\":\"second\";\"second\":\"third\")","third"},new Object[]{"@Replace(\"a\":\"b\":\"c\";\"a\":\"c\";\"A\":\"C\")","A;b;C"},
        new Object[]{"@Explode(\"a,b;c d\")","a;b;c;d"},new Object[]{"@Implode(\"a\":\"b\";\",\")","a,b"},new Object[]{"@Repeat(\"ab\";3)","ababab"},new Object[]{"@Length(\"abcd\")","4"},new Object[]{"@Char(65)","A"},
        new Object[]{"@Contains(\"abc\";\"b\")","1"},new Object[]{"@Begins(\"abc\";\"a\")","1"},new Object[]{"@Ends(\"abc\";\"bc\")","1"},new Object[]{"@Member(\"b\";\"a\":\"b\")","2"},
        new Object[]{"@Unique(\"a\":\"b\":\"a\")","a;b"},new Object[]{"@Sort(3:1:2;[Descending])","3;2;1"},new Object[]{"@Select(2;\"first\";\"second\")","second"},
        new Object[]{"@Abs(-3)","3"},new Object[]{"@Integer(-2.5)","-2"},new Object[]{"@Sign(-4)","-1"},new Object[]{"@Round(2.5)","3"},new Object[]{"@Round(-2.5)","-3"},new Object[]{"@Round(1.234;0.01)","1.23"},
        new Object[]{"@Log(100)","2"},new Object[]{"@Power(2;3)","8"},new Object[]{"@Modulo(7;3)","1"},new Object[]{"@Sum(1:2;3)","6"},new Object[]{"@Max(99:2:3;5:6:7:8)","99;6;7;8"},new Object[]{"@Min(3:2:1)","1"},
        new Object[]{"@Text(123.4;\"F2\")","123.40"},new Object[]{"@Text(800;\"C,2\")","$800.00"},new Object[]{"@TextToNumber(\"1,234.5\")","1234.5"},
        new Object[]{"@Year(@Date(2026;10;10))","2026"},new Object[]{"@Month(@Date(2026;10;10))","10"},new Object[]{"@Weekday(@Date(2026;10;10))","7"},new Object[]{"@Hour(@Time(12;30;0))","12"},
        new Object[]{"@Text(@Adjust(@Date(2026;10;10);0;0;1;0;0;0);\"S0\")","10/11/2026"},new Object[]{"@BusinessDays(@Date(2026;10;5);@Date(2026;10;9))","5"},
        new Object[]{"@Name([Abbreviate];\"CN=Jane Doe/OU=Apps/O=Acme\")","Jane Doe/Apps/Acme"},new Object[]{"@Name([CN];\"Jane Doe/Apps/Acme\")","Jane Doe"},
        new Object[]{"n:=0;@While(n<3;n:=n+1);n","3"},new Object[]{"n:=0;@DoWhile(n:=n+1;n<3);n","3"},new Object[]{"sum:=0;@For(n:=1;n<=3;n:=n+1;sum:=sum+n);sum","6"},
        new Object[]{"REM this is a comment;\"a\\\"b\"","a\"b"},new Object[]{"{quoted text}","quoted text"},new Object[]{"x:=\"one\":\"two\";x[2]","two"},
        new Object[]{"@IsNull(\"\")","1"},new Object[]{"@IsNull(\"\":\"\")","0"},new Object[]{"@IsNull(1/0)","0"},new Object[]{"@IsAvailable(Missing)","0"}
    );}
    @ParameterizedTest @MethodSource("examples") void offlineExamples(String formula,String expected){var value=engine.evaluate(formula,EvaluationContext.empty()).value();assertFalse(value.isError(),value.toString());assertEquals(expected,value.display(";"));}
    @Test void assignmentsDoNotChangeInputAndDefaultsRespectExistingFields(){var c=EvaluationContext.empty().withFields(Map.of("Subject",FormulaValue.text("original")));var result=engine.evaluate("DEFAULT Subject:=\"default\";FIELD New:=3;FIELD Subject:=\"changed\";Subject",c);assertEquals("changed",result.value().string());assertEquals("original",c.fields().get("subject").string());assertEquals(3,result.workingFields().get("new").number());}
    @Test void deleteFieldRemovesWorkingValueOnly(){var c=EvaluationContext.empty().withFields(Map.of("A",FormulaValue.number(1)));var result=engine.evaluate("FIELD A:=@DeleteField;@IsAvailable(A)",c);assertEquals(0,result.value().number());assertTrue(c.fields().containsKey("a"));}
    @Test void loopsAndDynamicRecursionAreBounded(){var c=new EvaluationContext(Map.of(),Map.of(),Locale.US,ZoneOffset.UTC,Instant.EPOCH,LookupProvider.empty(),100,20,100);assertEquals("EVALUATION_LIMIT",engine.evaluate("@While(1;1)",c).value().error().code());assertEquals("EVALUATION_LIMIT",engine.evaluate("x:=\"@Eval(x)\";@Eval(x)",c).value().error().code());}
    @Test void unsupportedAndUnavailableResourcesFailExplicitly(){assertEquals("UNSUPPORTED_FUNCTION",engine.evaluate("@UnknownThing",EvaluationContext.empty()).value().error().code());assertEquals("OFFLINE_UNAVAILABLE",engine.evaluate("@Command([FileSave])",EvaluationContext.empty()).value().error().code());assertEquals("MISSING_RESOURCE",engine.evaluate("@DbLookup(\"\";\"\";\"Codes\";\"a\";2)",EvaluationContext.empty()).value().error().code());}
    @Test void fixedClockAndTimezoneAreUsed(){var c=EvaluationContext.empty().withTime(Instant.parse("2026-10-10T02:00:00Z"),ZoneId.of("America/New_York"));assertEquals("10/09/2026",engine.evaluate("@Text(@Today;\"S0\")",c).value().string());}
    @Test void syntaxFailuresHaveOffsets(){var e=assertThrows(FormulaSyntaxException.class,()->engine.compile("@If(1;2"));assertTrue(e.offset()>0);assertThrows(FormulaSyntaxException.class,()->engine.compile("ENVIRONMENT x:=1"));}
    @Test void compiledFormulasAreSafeAcrossThreads()throws Exception{var compiled=engine.compile("n:=Input;@Transform(n:(n+1);\"x\";x*2)");var pool=Executors.newFixedThreadPool(8);try{List<Callable<String>> tasks=new ArrayList<>();for(int i=0;i<200;i++){int n=i;tasks.add(()->engine.evaluate(compiled,EvaluationContext.empty().withFields(Map.of("Input",FormulaValue.number(n)))).value().display(";"));}var results=pool.invokeAll(tasks);for(int i=0;i<results.size();i++)assertEquals(2*i+";"+(2*(i+1)),results.get(i).get());}finally{pool.shutdownNow();}}
    @Test void registryExtensionsAreImmutable(){var original=engine.functions();var custom=original.withFunction("Double",1,1,(a,c)->FormulaValue.number(a.get(0).number()*2));assertFalse(original.implemented("Double"));assertEquals(6,new FormulaEngine(custom).evaluate("@Double(3)",EvaluationContext.empty()).value().number());}
}
