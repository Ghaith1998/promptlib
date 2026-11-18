package com.promptlib.shared.error;

import com.promptlib.shared.ProblemCode;

public class ValidationFailed extends DomainException {

    public ValidationFailed(ProblemCode code) {
        super(code);
    }

    public ValidationFailed(ProblemCode code, String message) {
        super(code, message);
    }
}
