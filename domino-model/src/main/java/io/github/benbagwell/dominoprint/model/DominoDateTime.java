package io.github.benbagwell.dominoprint.model;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Domino's any-date/any-time values cannot faithfully be reduced to java.util.Date. */
public record DominoDateTime(Optional<LocalDate> date, Optional<LocalTime> time,
                             Optional<ZoneOffset> offset, boolean daylightSaving, String source) {
    private static final Pattern DXL = Pattern.compile("(?:(\\d{8}))?(?:T(\\d{6})(?:[,.](\\d+))?)?([+-]\\d{2}(?::?\\d{2})?|Z)?");
    public DominoDateTime { Objects.requireNonNull(date); Objects.requireNonNull(time); Objects.requireNonNull(offset); source = Objects.requireNonNullElse(source, ""); }
    public static DominoDateTime parse(String value, boolean dst) {
        var m = DXL.matcher(value.trim());
        if (!m.matches() || (m.group(1) == null && m.group(2) == null)) throw new IllegalArgumentException("Invalid DXL datetime: " + value);
        Optional<LocalDate> d = Optional.ofNullable(m.group(1)).map(s -> LocalDate.parse(s, DateTimeFormatter.BASIC_ISO_DATE));
        Optional<LocalTime> t = Optional.ofNullable(m.group(2)).map(s -> LocalTime.of(Integer.parseInt(s.substring(0,2)), Integer.parseInt(s.substring(2,4)), Integer.parseInt(s.substring(4,6)), m.group(3) == null ? 0 : Integer.parseInt((m.group(3) + "000000000").substring(0,9))));
        return new DominoDateTime(d, t, Optional.ofNullable(m.group(4)).map(ZoneOffset::of), dst, value);
    }
    public static DominoDateTime of(ZonedDateTime value) { return new DominoDateTime(Optional.of(value.toLocalDate()), Optional.of(value.toLocalTime()), Optional.of(value.getOffset()), value.getZone().getRules().isDaylightSavings(value.toInstant()), ""); }
    public static DominoDateTime of(LocalDate value) { return new DominoDateTime(Optional.of(value), Optional.empty(), Optional.empty(), false, ""); }
    public static DominoDateTime of(LocalTime value) { return new DominoDateTime(Optional.empty(), Optional.of(value), Optional.empty(), false, ""); }
    public Instant toInstant(ZoneId fallback) {
        if (date.isEmpty() || time.isEmpty()) throw new IllegalStateException("A partial datetime has no unique instant");
        var dt = LocalDateTime.of(date.get(), time.get());
        return offset.map(dt::toInstant).orElseGet(() -> dt.atZone(fallback).toInstant());
    }
    @Override public String toString() { return date.map(Object::toString).orElse("") + (time.isPresent() ? (date.isPresent() ? "T" : "") + time.get() : "") + offset.map(Object::toString).orElse(""); }
}
