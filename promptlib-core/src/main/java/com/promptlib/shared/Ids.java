package com.promptlib.shared;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

/** The only place identifiers are generated. UUID v7 keeps index locality. */
public final class Ids {

    private Ids() {
    }

    public static UUID newId() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
