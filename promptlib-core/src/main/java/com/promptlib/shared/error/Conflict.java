package com.promptlib.shared.error;

import com.promptlib.shared.ProblemCode;

public class Conflict extends DomainException {

    public Conflict(ProblemCode code) {
        super(code);
    }

    public Conflict(ProblemCode code, String message) {
        super(code, message);
    }
}
