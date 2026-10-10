package io.github.benbagwell.dominoprint.adapter;

public final class DominoImportException extends IllegalStateException {
    public DominoImportException(String message,Throwable cause){super(message,cause);}
    public DominoImportException(String message){super(message);}
}
