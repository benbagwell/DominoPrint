package io.github.benbagwell.dominoprint.adapter;

import io.github.benbagwell.dominoprint.formula.*;
import io.github.benbagwell.dominoprint.model.DominoDateTime;
import lotus.domino.*;
import java.util.*;

/** Import-time verification only. The factory supplies a fresh, unsaved, caller-configured test document. */
public final class DominoFormulaReference implements FormulaReferenceVerifier.Oracle {
    @FunctionalInterface public interface DocumentFactory { Document create(FormulaReferenceVerifier.Case testCase) throws NotesException; }
    private final Session session;
    private final DocumentFactory factory;
    private final Thread owner=Thread.currentThread();
    public DominoFormulaReference(Session session,DocumentFactory factory){this.session=Objects.requireNonNull(session);this.factory=Objects.requireNonNull(factory);}
    @Override public FormulaReferenceVerifier.Observation evaluate(FormulaReferenceVerifier.Case testCase) throws NotesException {
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Reference oracle is Domino-thread confined");
        Document doc=factory.create(testCase);if(doc==null)throw new IllegalArgumentException("Reference factory returned null");
        Set<Base> owned=Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            Vector<?> values=session.evaluate(testCase.source(),doc);var result=FormulaValue.nothing();
            for(Object value:values)result=result.append(convert(value,testCase,owned));
            var captured=new DominoImporter(session).capture(doc);
            return new FormulaReferenceVerifier.Observation(result,EvaluationContext.of(captured).fields(),"Domino Session.evaluate; "+session.getNotesVersion());
        } finally {for(Base object:owned)object.recycle();doc.recycle();}
    }
    private static FormulaValue convert(Object value,FormulaReferenceVerifier.Case c,Set<Base> owned) throws NotesException {
        if(value instanceof String text)return FormulaValue.text(text);
        if(value instanceof Number n)return FormulaValue.number(n.doubleValue());
        if(value instanceof DateTime d){owned.add(d);var time=d.toJavaDate().toInstant().atZone(c.context().zone());boolean anyDate=d.getDateOnly().isBlank(),anyTime=d.getTimeOnly().isBlank();return FormulaValue.date(new DominoDateTime(anyDate?Optional.empty():Optional.of(time.toLocalDate()),anyTime?Optional.empty():Optional.of(time.toLocalTime()),anyDate||anyTime?Optional.empty():Optional.of(time.getOffset()),d.isDST(),d.getLocalTime()));}
        if(value instanceof Base b)owned.add(b);
        throw new IllegalArgumentException("Unsupported reference result: "+(value==null?"null":value.getClass().getName()));
    }
}
