package com.shk.generic.ingestion.intake;

public class IntakeRejectedException extends RuntimeException {

    public IntakeRejectedException(String message) {
        super(message);
    }

    public IntakeRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
