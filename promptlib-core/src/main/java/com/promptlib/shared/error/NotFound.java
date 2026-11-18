package com.promptlib.shared.error;

import com.promptlib.shared.ProblemCode;

public class NotFound extends DomainException {

    public NotFound() {
        super(ProblemCode.NOT_FOUND);
    }

    public NotFound(String message) {
        super(ProblemCode.NOT_FOUND, message);
    }
}
