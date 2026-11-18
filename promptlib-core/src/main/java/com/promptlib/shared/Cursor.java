package com.promptlib.shared;

import com.promptlib.shared.error.ValidationFailed;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Keyset cursor: (created_at, id) encoded as base64url of {@code epochMillis:uuid}.
 * The id tiebreaker is what makes pagination stable while rows are inserted.
 */
public record Cursor(Instant timestamp, UUID id) {

    public static Cursor of(Instant timestamp, UUID id) {
        return new Cursor(timestamp, id);
    }

    public String encode() {
        String raw = timestamp.toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String encoded) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int split = raw.indexOf(':');
            return new Cursor(
                    Instant.ofEpochMilli(Long.parseLong(raw.substring(0, split))),
                    UUID.fromString(raw.substring(split + 1)));
        } catch (RuntimeException e) {
            throw new ValidationFailed(ProblemCode.INVALID_CURSOR);
        }
    }
}
