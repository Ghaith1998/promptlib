package com.promptlib.shared.error;

import com.promptlib.shared.ProblemCode;

public class Forbidden extends DomainException {

    public Forbidden() {
        super(ProblemCode.FORBIDDEN);
    }

    public Forbidden(ProblemCode code) {
        super(code);
    }
}
