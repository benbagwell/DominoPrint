package io.github.benbagwell.dominoprint.formula;

import io.github.benbagwell.dominoprint.model.*;
import java.util.*;

/** Homogeneous formula values; errors and missing values remain distinct from empty text. */
public final class FormulaValue {
    public enum Type { TEXT, NUMBER, DATETIME, DATE_RANGE, KEYWORD, NOTHING, UNAVAILABLE, ERROR }
    public record Error(String code, String message) { public Error { Objects.requireNonNull(code); Objects.requireNonNull(message); } }
    private final Type type;
    private final List<Object> elements;
    private FormulaValue(Type type, List<?> elements) { this.type=type; this.elements=List.copyOf(elements); }
    public static FormulaValue text(String value) { return list(Type.TEXT,List.of(value)); }
    public static FormulaValue number(double value) { return Double.isFinite(value)?list(Type.NUMBER,List.of(value)):error("NUMERIC_ERROR","Non-finite number"); }
    public static FormulaValue bool(boolean value) { return number(value?1:0); }
    public static FormulaValue date(DominoDateTime value) { return list(Type.DATETIME,List.of(value)); }
    public static FormulaValue keyword(String value) { return list(Type.KEYWORD,List.of(value)); }
    public static FormulaValue nothing() { return new FormulaValue(Type.NOTHING,List.of()); }
    public static FormulaValue unavailable() { return new FormulaValue(Type.UNAVAILABLE,List.of()); }
    public static FormulaValue error(String code,String message) { return new FormulaValue(Type.ERROR,List.of(new Error(code,message))); }
    public static FormulaValue list(Type type,List<?> values) {
        Objects.requireNonNull(type); Objects.requireNonNull(values);
        for(var v:values) if(!switch(type) {
            case TEXT,KEYWORD -> v instanceof String; case NUMBER -> v instanceof Double d && Double.isFinite(d);
            case DATETIME -> v instanceof DominoDateTime; case DATE_RANGE -> v instanceof DominoValue.DateRange;
            case ERROR -> v instanceof Error; case NOTHING,UNAVAILABLE -> false;
        }) throw new IllegalArgumentException("Invalid " + type + " element");
        return new FormulaValue(type,values);
    }
    public static FormulaValue from(ItemSnapshot item) {
        List<Object> values=new ArrayList<>(); Type type=null;
        for(var value:item.values()) {
            Type next; Object atom;
            if(value instanceof DominoValue.Text v) { next=Type.TEXT; atom=v.value(); }
            else if(value instanceof DominoValue.Numeric v) { next=Type.NUMBER; atom=v.value().doubleValue(); }
            else if(value instanceof DominoValue.DateTime v) { next=Type.DATETIME; atom=v.value(); }
            else if(value instanceof DominoValue.DateRange v) { next=Type.DATE_RANGE; atom=v; }
            else if(value instanceof DominoValue.RichText v) { next=Type.TEXT; atom=v.root().plainText(); }
            else return error("UNSUPPORTED_ITEM_TYPE","Cannot evaluate item " + item.name() + " of type " + item.type());
            if(type!=null && type!=next) return error("MIXED_TYPES","Mixed item types: " + item.name());
            type=next; values.add(atom);
        }
        return type==null?text(""):list(type,values);
    }
    public Type type() { return type; }
    public List<Object> elements() { return elements; }
    public int size() { return elements.size(); }
    public boolean isError() { return type==Type.ERROR; }
    public Error error() { if(!isError()) throw new IllegalStateException("Not an error"); return (Error)elements.get(0); }
    public double number() { if(type!=Type.NUMBER || elements.isEmpty()) throw new IllegalArgumentException("Expected number"); return (Double)elements.get(0); }
    public String string() { if((type!=Type.TEXT && type!=Type.KEYWORD) || elements.isEmpty()) throw new IllegalArgumentException("Expected text"); return (String)elements.get(0); }
    public boolean truth() { if(type!=Type.NUMBER) throw new IllegalArgumentException("Expected numeric condition"); return elements.stream().anyMatch(v -> (Double)v!=0); }
    public FormulaValue at(int zeroBased) { if(zeroBased<0 || zeroBased>=size()) return error("SUBSCRIPT","List subscript out of range"); return list(type,List.of(elements.get(zeroBased))); }
    public FormulaValue append(FormulaValue other) {
        if(isError()) return this; if(other.isError()) return other;
        if(type==Type.NOTHING) return other; if(other.type==Type.NOTHING) return this;
        if(type!=other.type) return error("TYPE_MISMATCH","List elements must have the same type");
        var result=new ArrayList<>(elements); result.addAll(other.elements); return list(type,result);
    }
    public String display(String separator) {
        if(isError()) return error().message();
        return elements.stream().map(v -> v instanceof Double d?java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString():v.toString()).reduce((a,b)->a+separator+b).orElse("");
    }
    @Override public boolean equals(Object o) { return o instanceof FormulaValue v && type==v.type && elements.equals(v.elements); }
    @Override public int hashCode() { return Objects.hash(type,elements); }
    @Override public String toString() { return type + ":" + display(";"); }
}
