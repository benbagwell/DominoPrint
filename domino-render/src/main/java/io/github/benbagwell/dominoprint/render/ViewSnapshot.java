package io.github.benbagwell.dominoprint.render;

import io.github.benbagwell.dominoprint.formula.*;
import io.github.benbagwell.dominoprint.model.*;
import java.time.ZoneId;
import java.util.*;

/** Ordered view rows with actual lookup column numbering supplied by the importer. */
public record ViewSnapshot(String server,String database,String name,List<String> aliases,List<Row> rows,ZoneId zone) {
    public record Row(FormulaValue firstSortedKey,Map<Integer,FormulaValue> columns,DocumentSnapshot document) {
        public Row { Objects.requireNonNull(firstSortedKey);columns=Map.copyOf(columns);Objects.requireNonNull(document); }
    }
    public ViewSnapshot { Objects.requireNonNull(server);Objects.requireNonNull(database);Objects.requireNonNull(name);aliases=List.copyOf(aliases);rows=List.copyOf(rows);Objects.requireNonNull(zone); }
    public FormulaValue lookup(LookupProvider.Request request){
        FormulaValue result=FormulaValue.nothing();boolean matched=false;
        for(var key:request.key().elements())for(var row:rows){if(matches(key,row.firstSortedKey(),request.key().type(),request.keywords().contains("partialmatch"))){
            matched=true;FormulaValue value;
            if(request.keywords().contains("returndocumentuniqueid"))value=FormulaValue.text(row.document().unid());
            else if(request.fieldOrColumn().type()==FormulaValue.Type.NUMBER){double c=request.fieldOrColumn().number();if(c<1||c!=Math.floor(c))return FormulaValue.error("LOOKUP_COLUMN","Invalid column number");value=row.columns().get((int)c);if(value==null)return FormulaValue.error("LOOKUP_COLUMN","Column is not cached: "+(int)c);}
            else if(request.fieldOrColumn().type()==FormulaValue.Type.TEXT)value=row.document().item(request.fieldOrColumn().string()).map(FormulaValue::from).orElse(FormulaValue.error("LOOKUP_FIELD","Field not found"));
            else return FormulaValue.error("LOOKUP_ARGUMENT","Expected field name or column number");
            result=result.append(value);if(result.isError())return result;
        }}
        if(!matched)return request.keywords().contains("failsilent")?FormulaValue.text(""):FormulaValue.error("ENTRY_NOT_FOUND","Entry not found in index");
        return enforceLimit(result);
    }
    public FormulaValue column(int number){if(number<1)return FormulaValue.error("LOOKUP_COLUMN","Column must be positive");FormulaValue result=FormulaValue.nothing();for(var row:rows){var value=row.columns().get(number);if(value==null)return FormulaValue.error("LOOKUP_COLUMN","Column is not cached: "+number);result=result.append(value);if(result.isError())return result;}return result.type()==FormulaValue.Type.NOTHING?FormulaValue.text(""):enforceLimit(result);}
    private boolean matches(Object key,FormulaValue row,FormulaValue.Type type,boolean partial){
        if(row.type()!=type)return false;
        for(var candidate:row.elements()){
            if(key instanceof String x && candidate instanceof String y && (partial?y.toLowerCase(Locale.ROOT).startsWith(x.toLowerCase(Locale.ROOT)):x.equalsIgnoreCase(y)))return true;
            if(key instanceof Double x && candidate instanceof Double y && Double.compare(x,y)==0)return true;
            if(key instanceof DominoDateTime x && candidate instanceof DominoDateTime y){if(x.date().isPresent()&&y.date().isPresent()&&(x.time().isEmpty()||y.time().isEmpty())){if(x.date().get().equals(y.date().get()))return true;}else if(x.date().isEmpty()&&y.date().isEmpty()){if(x.time().equals(y.time()))return true;}else if(x.date().isPresent()&&y.date().isPresent()&&x.toInstant(zone).equals(y.toInstant(zone)))return true;}
        }return false;
    }
    private static FormulaValue enforceLimit(FormulaValue v){long bytes=v.type()==FormulaValue.Type.TEXT?2L+2L*v.size()+v.elements().stream().mapToLong(x->((String)x).getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum():10L*v.size()+6;return bytes>65536?FormulaValue.error("LOOKUP_TOO_LARGE","Lookup exceeds Domino's 64KB result limit"):v;}
}
