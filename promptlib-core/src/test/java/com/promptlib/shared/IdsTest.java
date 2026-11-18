package com.promptlib.shared;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdsTest {

    @Test
    void generatesVersion7Uuids() {
        UUID id = Ids.newId();
        assertEquals(7, id.version(), "UUID v7 expected");
        assertEquals(2, id.variant());
    }

    @Test
    void isTimeOrdered() throws InterruptedException {
        UUID first = Ids.newId();
        Thread.sleep(2);
        UUID second = Ids.newId();
        assertTrue(second.toString().compareTo(first.toString()) > 0);
    }
}
