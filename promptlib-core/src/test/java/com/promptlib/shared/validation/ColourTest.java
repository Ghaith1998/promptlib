package com.promptlib.shared.validation;

import com.promptlib.shared.ProblemCode;
import com.promptlib.shared.error.ValidationFailed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColourTest {

    @Test
    void normalisesToUpperCase() {
        assertEquals("#4F46E5", Colour.normalise(" #4f46e5 "));
    }

    @Test
    void blankBecomesNull() {
        assertNull(Colour.normalise("  "));
        assertNull(Colour.normalise(null));
    }

    @Test
    void rejectsInvalid() {
        ValidationFailed ex = assertThrows(ValidationFailed.class, () -> Colour.normalise("red"));
        assertEquals(ProblemCode.INVALID_COLOUR, ex.code());
    }
}
