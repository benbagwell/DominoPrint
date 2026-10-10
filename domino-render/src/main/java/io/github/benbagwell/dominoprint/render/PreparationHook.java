package io.github.benbagwell.dominoprint.render;

import io.github.benbagwell.dominoprint.formula.*;
import java.util.Map;

/** A thread-safe Java replacement for a detected preparation dependency. */
@FunctionalInterface public interface PreparationHook {
    Map<String,FormulaValue> prepare(Map<String,FormulaValue> workingFields,EvaluationContext context);
}
