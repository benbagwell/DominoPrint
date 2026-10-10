package io.github.benbagwell.dominoprint.formula;

public final class FormulaSyntaxException extends IllegalArgumentException {
    private final int offset;
    public FormulaSyntaxException(String message,int offset) { super(message + " at offset " + offset); this.offset=offset; }
    public int offset() { return offset; }
}
