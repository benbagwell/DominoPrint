package io.github.benbagwell.dominoprint.model;

import java.util.Objects;

/** A capture, compilation or rendering issue with a stable machine-readable code. */
public record Diagnostic(Severity severity, String code, String location, String message) {
    public enum Severity { INFO, WARNING, ERROR }
    public Diagnostic {
        Objects.requireNonNull(severity); Objects.requireNonNull(code);
        location = Objects.requireNonNullElse(location, ""); Objects.requireNonNull(message);
    }
    public static Diagnostic error(String code, String location, String message) { return new Diagnostic(Severity.ERROR, code, location, message); }
    public static Diagnostic warning(String code, String location, String message) { return new Diagnostic(Severity.WARNING, code, location, message); }
}
