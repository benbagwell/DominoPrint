package io.github.benbagwell.dominoprint.render;

import io.github.benbagwell.dominoprint.formula.FormulaValue;
import io.github.benbagwell.dominoprint.model.DocumentSnapshot;
import java.util.*;

/** Exact code matching by default, independent of view lookup collation. */
public final class CodeTranslations {
    public record Mapping(String setField,String codeField,String translationField,boolean ignoreCodeCase) {
        public Mapping { Objects.requireNonNull(setField);Objects.requireNonNull(codeField);Objects.requireNonNull(translationField); }
        public Mapping(String setField,String codeField,String translationField){this(setField,codeField,translationField,false);}
    }
    private final Map<String,Map<String,FormulaValue>> sets;
    private final boolean ignoreCase;
    public CodeTranslations(Map<String,Map<String,FormulaValue>> sets,boolean ignoreCase){Map<String,Map<String,FormulaValue>> copy=new LinkedHashMap<>();sets.forEach((set,values)->{Map<String,FormulaValue> normalized=new LinkedHashMap<>();values.forEach((code,value)->{if(normalized.put(normalize(code,ignoreCase),value)!=null)throw new IllegalArgumentException("Duplicate code: "+set+"/"+code);});copy.put(set,Map.copyOf(normalized));});this.sets=Map.copyOf(copy);this.ignoreCase=ignoreCase;}
    public static CodeTranslations empty(){return new CodeTranslations(Map.of(),false);}
    public static CodeTranslations fromDocuments(Collection<DocumentSnapshot> documents,Mapping mapping){
        Map<String,Map<String,FormulaValue>> sets=new LinkedHashMap<>();
        for(var doc:documents){
            String set=scalar(doc,mapping.setField),code=scalar(doc,mapping.codeField);var item=doc.item(mapping.translationField).orElseThrow(()->new IllegalArgumentException("Missing translation field in "+doc.unid()));var value=FormulaValue.from(item);
            if(value.isError())throw new IllegalArgumentException("Invalid translation in "+doc.unid()+": "+value.error().message());
            var values=sets.computeIfAbsent(set,k->new LinkedHashMap<>());String key=normalize(code,mapping.ignoreCodeCase);
            if(values.putIfAbsent(key,value)!=null)throw new IllegalArgumentException("Duplicate code mapping: "+set+"/"+code);
        }
        return new CodeTranslations(sets,mapping.ignoreCodeCase);
    }
    private static String scalar(DocumentSnapshot doc,String field){var values=doc.item(field).orElseThrow(()->new IllegalArgumentException("Missing "+field+" in "+doc.unid())).textValues();if(values.size()!=1||values.get(0).isEmpty())throw new IllegalArgumentException("Expected one nonempty text value for "+field);return values.get(0);}
    private static String normalize(String code,boolean ignoreCase){return ignoreCase?code.toLowerCase(Locale.ROOT):code;}
    public Map<String,Map<String,FormulaValue>> sets(){return sets;}
    public FormulaValue translate(String set,String code){var values=sets.get(set);if(values==null)return FormulaValue.error("MISSING_CODE_SET","Code set is not cached: "+set);var result=values.get(normalize(code,ignoreCase));return result==null?FormulaValue.error("MISSING_CODE","Code is not cached: "+set+"/"+code):result;}
}
