package io.github.benbagwell.dominoprint.model;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Detached bytes. Neither the constructor nor any accessor exposes the owned array. */
public final class BinaryContent {
    private final String name, mediaType, sha256;
    private final byte[] bytes;
    public BinaryContent(String name, String mediaType, byte[] bytes) {
        this.name = Objects.requireNonNullElse(name, ""); this.mediaType = Objects.requireNonNullElse(mediaType, "application/octet-stream");
        this.bytes = Objects.requireNonNull(bytes).clone();
        try { sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(this.bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public String name() { return name; }
    public String mediaType() { return mediaType; }
    public String sha256() { return sha256; }
    public int size() { return bytes.length; }
    public byte[] bytes() { return bytes.clone(); }
    public InputStream openStream() { return new ByteArrayInputStream(bytes); }
    @Override public boolean equals(Object other) { return other instanceof BinaryContent b && name.equals(b.name) && mediaType.equals(b.mediaType) && Arrays.equals(bytes, b.bytes); }
    @Override public int hashCode() { return Objects.hash(name, mediaType, sha256); }
    @Override public String toString() { return name + " (" + bytes.length + " bytes, " + mediaType + ")"; }
}
