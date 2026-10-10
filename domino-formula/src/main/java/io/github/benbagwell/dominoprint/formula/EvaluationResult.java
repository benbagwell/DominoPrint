package io.github.benbagwell.dominoprint.formula;

import java.util.*;

public record EvaluationResult(FormulaValue value, Map<String,FormulaValue> workingFields, int steps) {
    public EvaluationResult { Objects.requireNonNull(value); workingFields=Map.copyOf(workingFields); }
}
