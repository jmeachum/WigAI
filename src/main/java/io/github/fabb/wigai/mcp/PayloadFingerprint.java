package io.github.fabb.wigai.mcp;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic payload fingerprinting for idempotency dedupe.
 *
 * <p>Canonicalizes tool arguments into a stable string and digests it, so that identical
 * business payloads produce identical fingerprints regardless of correlation ID or map
 * iteration order. Pure functions with no external state.
 */
final class PayloadFingerprint {

    private PayloadFingerprint() {} // Prevent instantiation

    /**
     * Computes a collision-resistant SHA-256 digest of non-correlation arguments for payload
     * consistency enforcement. The fingerprint excludes {@code request_id} so that identical
     * business payloads produce the same digest regardless of correlation ID.
     * Arguments are canonicalized (sorted keys, deterministic value serialization) before hashing.
     *
     * @param arguments The raw tool arguments (may be null)
     * @return A hex-encoded SHA-256 digest of the non-correlation arguments, or empty string if null/empty
     */
    static String computePayloadFingerprint(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }
        TreeMap<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            if (!RequestContextExtractor.CORRELATION_KEYS.contains(entry.getKey())) {
                sorted.put(entry.getKey(), entry.getValue());
            }
        }
        if (sorted.isEmpty()) {
            return "";
        }
        String canonical = canonicalizeValue(sorted);
        return sha256Hex(canonical);
    }

    /**
     * Recursively produces a deterministic canonical string for any value.
     * Uses typed, length-delimited segments so delimiter characters inside keys/values
     * cannot create canonicalization ambiguity.
     */
    private static String canonicalizeValue(Object value) {
        if (value == null) {
            return "n;";
        }
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sorted.put(String.valueOf(e.getKey()), e.getValue());
            }
            StringBuilder sb = new StringBuilder("m{");
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                String key = entry.getKey();
                String encodedValue = canonicalizeValue(entry.getValue());
                sb.append("k").append(key.length()).append(":").append(key);
                sb.append("v").append(encodedValue.length()).append(":").append(encodedValue).append(";");
            }
            sb.append("}");
            return sb.toString();
        }
        if (value instanceof Collection<?> col) {
            StringBuilder sb = new StringBuilder("l[");
            for (Object item : col) {
                String encodedItem = canonicalizeValue(item);
                sb.append("i").append(encodedItem.length()).append(":").append(encodedItem).append(";");
            }
            sb.append("]");
            return sb.toString();
        }
        if (value.getClass().isArray()) {
            StringBuilder sb = new StringBuilder("a[");
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                String encodedItem = canonicalizeValue(Array.get(value, i));
                sb.append("i").append(encodedItem.length()).append(":").append(encodedItem).append(";");
            }
            sb.append("]");
            return sb.toString();
        }
        if (value instanceof String str) {
            return "s" + str.length() + ":" + str + ";";
        }
        if (value instanceof Number number) {
            return "d" + normalizeNumber(number) + ";";
        }
        if (value instanceof Boolean bool) {
            return bool ? "b1;" : "b0;";
        }
        String asString = String.valueOf(value);
        String className = value.getClass().getName();
        return "o" + className.length() + ":" + className
            + "v" + asString.length() + ":" + asString + ";";
    }

    private static String normalizeNumber(Number number) {
        if (number instanceof Byte || number instanceof Short
            || number instanceof Integer || number instanceof Long
            || number instanceof java.math.BigInteger) {
            return number.toString();
        }
        if (number instanceof BigDecimal bigDecimal) {
            return normalizeBigDecimal(bigDecimal);
        }
        if (number instanceof Float floatValue) {
            if (Float.isNaN(floatValue)) {
                return "NaN";
            }
            if (Float.isInfinite(floatValue)) {
                return floatValue > 0 ? "Infinity" : "-Infinity";
            }
            return normalizeBigDecimal(new BigDecimal(Float.toString(floatValue)));
        }
        if (number instanceof Double doubleValue) {
            if (Double.isNaN(doubleValue)) {
                return "NaN";
            }
            if (Double.isInfinite(doubleValue)) {
                return doubleValue > 0 ? "Infinity" : "-Infinity";
            }
            return normalizeBigDecimal(new BigDecimal(Double.toString(doubleValue)));
        }
        try {
            return normalizeBigDecimal(new BigDecimal(number.toString()));
        } catch (NumberFormatException ex) {
            return number.toString();
        }
    }

    private static String normalizeBigDecimal(BigDecimal bigDecimal) {
        BigDecimal normalized = bigDecimal.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString();
    }

    /**
     * Computes the SHA-256 hex digest of the given input string.
     */
    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in standard Java
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
