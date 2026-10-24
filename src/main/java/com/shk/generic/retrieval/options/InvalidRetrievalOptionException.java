package com.shk.generic.retrieval.options;

import java.util.List;

/** A retrieval option is unknown, unavailable, or out of range. Always tells the caller what is valid. */
public class InvalidRetrievalOptionException extends RuntimeException {

    private final String parameter;
    private final List<String> validValues;

    public InvalidRetrievalOptionException(String parameter, String given, List<String> validValues) {
        super("Invalid " + parameter + " '" + given + "'; valid values: " + validValues);
        this.parameter = parameter;
        this.validValues = List.copyOf(validValues);
    }

    public InvalidRetrievalOptionException(String parameter, String message) {
        super(message);
        this.parameter = parameter;
        this.validValues = List.of();
    }

    public String parameter() {
        return parameter;
    }

    public List<String> validValues() {
        return validValues;
    }
}
