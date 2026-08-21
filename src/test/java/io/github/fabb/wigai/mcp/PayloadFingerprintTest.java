package io.github.fabb.wigai.mcp;
import io.github.fabb.wigai.mcp.tool.*;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.never;

/**
 * Deterministic payload fingerprinting for idempotency dedupe.
 *
 * <p>Covers {@link PayloadFingerprint}: request_id exclusion, numeric normalization,
 * and digest stability across equivalent payloads.
 *
 * <p>Split out of the original 1718-line McpErrorHandlerTest along the same seams
 * as the production extraction.
 */
class PayloadFingerprintTest {

    @Test
    void testComputePayloadFingerprint_ExcludesRequestId() {
        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "test-123");
        args.put("parameter_index", 3);
        args.put("value", 0.5);

        Map<String, Object> argsDifferentReqId = new HashMap<>();
        argsDifferentReqId.put("request_id", "different-id");
        argsDifferentReqId.put("parameter_index", 3);
        argsDifferentReqId.put("value", 0.5);

        assertEquals(
            PayloadFingerprint.computePayloadFingerprint(args),
            PayloadFingerprint.computePayloadFingerprint(argsDifferentReqId),
            "Fingerprint must exclude request_id — same business args must produce same fingerprint");
    }

    @Test
    void testComputePayloadFingerprint_DifferentArgs_DifferentFingerprint() {
        Map<String, Object> args1 = new HashMap<>();
        args1.put("request_id", "test-123");
        args1.put("parameter_index", 3);

        Map<String, Object> args2 = new HashMap<>();
        args2.put("request_id", "test-123");
        args2.put("parameter_index", 7);

        assertNotEquals(
            PayloadFingerprint.computePayloadFingerprint(args1),
            PayloadFingerprint.computePayloadFingerprint(args2),
            "Different business args must produce different fingerprints");
    }

    @Test
    void testComputePayloadFingerprint_NullArgs_ReturnsEmptyString() {
        assertEquals("", PayloadFingerprint.computePayloadFingerprint(null));
    }

    @Test
    void testComputePayloadFingerprint_EmptyArgs_ReturnsEmptyString() {
        assertEquals("", PayloadFingerprint.computePayloadFingerprint(new HashMap<>()));
    }

    @Test
    void testComputePayloadFingerprint_OnlyRequestId_ReturnsEmptyString() {
        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "test-123");
        assertEquals("", PayloadFingerprint.computePayloadFingerprint(args),
            "Args with only request_id should fingerprint as empty (no business payload)");
    }

    @Test
    void testComputePayloadFingerprint_ReturnsSha256HexDigest() {
        Map<String, Object> args = new HashMap<>();
        args.put("parameter_index", 3);
        args.put("value", 0.5);

        String fingerprint = PayloadFingerprint.computePayloadFingerprint(args);

        assertNotNull(fingerprint);
        assertFalse(fingerprint.isEmpty(), "Non-empty payload must produce a non-empty fingerprint");
        assertEquals(64, fingerprint.length(), "SHA-256 hex digest must be 64 characters");
        assertTrue(fingerprint.matches("[0-9a-f]{64}"), "Must be valid hex-encoded SHA-256 digest");
    }

    @Test
    void testComputePayloadFingerprint_Deterministic() {
        Map<String, Object> args1 = new HashMap<>();
        args1.put("value", 0.5);
        args1.put("parameter_index", 3);

        Map<String, Object> args2 = new HashMap<>();
        args2.put("parameter_index", 3);
        args2.put("value", 0.5);

        assertEquals(
            PayloadFingerprint.computePayloadFingerprint(args1),
            PayloadFingerprint.computePayloadFingerprint(args2),
            "Same args in different insertion order must produce identical fingerprint");
    }

    @Test
    void testComputePayloadFingerprint_CollisionCandidatePayloads_DifferentFingerprint() {
        // Prior canonicalization could collide for these two distinct payloads:
        // {'[': ':":'} and {'[:"': ':'}
        Map<String, Object> args1 = Map.of("[", ":\":");
        Map<String, Object> args2 = Map.of("[:\"", ":");

        assertNotEquals(
            PayloadFingerprint.computePayloadFingerprint(args1),
            PayloadFingerprint.computePayloadFingerprint(args2),
            "Distinct delimiter-heavy payloads must never collide under canonicalization");
    }

    @Test
    void testComputePayloadFingerprint_NormalizesEquivalentNumbers() {
        Map<String, Object> args1 = new HashMap<>();
        args1.put("parameter_index", 1);
        args1.put("payload", Map.of(
            "value", 1,
            "grid", List.of(1, 1.50, 2)
        ));

        Map<String, Object> args2 = new HashMap<>();
        args2.put("parameter_index", 1.0);
        args2.put("payload", Map.of(
            "value", 1.0,
            "grid", List.of(1.0, 1.5, 2.0)
        ));

        assertEquals(
            PayloadFingerprint.computePayloadFingerprint(args1),
            PayloadFingerprint.computePayloadFingerprint(args2),
            "Semantically equivalent numeric payloads must produce identical fingerprints");
    }

    @Test
    void testComputePayloadFingerprint_FloatAndDoubleParity() {
        // 0.1f and 0.1d must produce identical fingerprints — both represent "0.1"
        Map<String, Object> argsFloat = new HashMap<>();
        argsFloat.put("value", 0.1f);

        Map<String, Object> argsDouble = new HashMap<>();
        argsDouble.put("value", 0.1d);

        assertEquals(
            PayloadFingerprint.computePayloadFingerprint(argsFloat),
            PayloadFingerprint.computePayloadFingerprint(argsDouble),
            "Float 0.1f and Double 0.1d must produce identical fingerprints via canonical textual form");
    }

    @Test
    void testComputePayloadFingerprint_FloatAndDoubleParityNested() {
        // Nested collections with Float vs Double must also match
        Map<String, Object> argsFloat = new HashMap<>();
        argsFloat.put("grid", List.of(0.1f, 0.25f, 1.0f));

        Map<String, Object> argsDouble = new HashMap<>();
        argsDouble.put("grid", List.of(0.1d, 0.25d, 1.0d));

        assertEquals(
            PayloadFingerprint.computePayloadFingerprint(argsFloat),
            PayloadFingerprint.computePayloadFingerprint(argsDouble),
            "Float and Double lists with same values must produce identical fingerprints");
    }
}
