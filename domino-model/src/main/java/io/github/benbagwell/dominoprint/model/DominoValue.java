package io.github.benbagwell.dominoprint.model;

import java.math.BigDecimal;
import java.util.Objects;

public sealed interface DominoValue {
    record Text(String value) implements DominoValue { public Text { Objects.requireNonNull(value); } }
    record Numeric(BigDecimal value) implements DominoValue { public Numeric { Objects.requireNonNull(value); } }
    record DateTime(DominoDateTime value) implements DominoValue { public DateTime { Objects.requireNonNull(value); } }
    record DateRange(DominoDateTime start, DominoDateTime end) implements DominoValue { public DateRange { Objects.requireNonNull(start); Objects.requireNonNull(end); } }
    record RichText(RichTextNode root) implements DominoValue { public RichText { Objects.requireNonNull(root); } }
    record Binary(BinaryContent content) implements DominoValue { public Binary { Objects.requireNonNull(content); } }
    record Opaque(String type, String sourceXml) implements DominoValue { public Opaque { Objects.requireNonNull(type); Objects.requireNonNull(sourceXml); } }
}
