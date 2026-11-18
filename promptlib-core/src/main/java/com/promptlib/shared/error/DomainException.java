package com.promptlib.shared.error;

import com.promptlib.shared.ProblemCode;

/** Base for every failure the API knows how to map. */
public class DomainException extends RuntimeException {

    private final ProblemCode code;

    public DomainException(ProblemCode code) {
        this(code, code.name());
    }

    public DomainException(ProblemCode code, String message) {
        super(message);
        this.code = code;
    }

    public ProblemCode code() {
        return code;
    }
}
