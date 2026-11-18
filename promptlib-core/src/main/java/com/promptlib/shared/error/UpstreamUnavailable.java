package com.promptlib.shared.error;

import com.promptlib.shared.ProblemCode;

public class UpstreamUnavailable extends DomainException {

    public UpstreamUnavailable(ProblemCode code, String message) {
        super(code, message);
    }
}
