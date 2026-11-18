package com.promptlib.shared;

import com.promptlib.shared.error.ValidationFailed;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CursorTest {

    @Test
    void roundTrips() {
        Cursor original = Cursor.of(Instant.ofEpochMilli(1_700_000_000_123L), UUID.fromString("0193a000-0000-7000-8000-000000000001"));
        Cursor decoded = Cursor.decode(original.encode());

        assertEquals(original.timestamp(), decoded.timestamp());
        assertEquals(original.id(), decoded.id());
    }

    @Test
    void rejectsGarbage() {
        assertThrows(ValidationFailed.class, () -> Cursor.decode("not-a-cursor"));
    }
}
