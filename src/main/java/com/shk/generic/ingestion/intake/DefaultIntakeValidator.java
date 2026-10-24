package com.shk.generic.ingestion.intake;

import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class DefaultIntakeValidator implements IntakeValidator {

    private final IntakeProperties properties;

    public DefaultIntakeValidator(IntakeProperties properties) {
        this.properties = properties;
    }

    @Override
    public void validate(IngestionRequest request) {
        if (request.format() == SourceFormat.UNKNOWN) {
            throw new IntakeRejectedException("Could not determine format of '" + request.filename() + "'");
        }
        if (!properties.allowedFormats().contains(request.format())) {
            throw new IntakeRejectedException("Format " + request.format() + " is not allowed");
        }
        long size = contentLength(request);
        if (size == 0) {
            throw new IntakeRejectedException("'" + request.filename() + "' is empty");
        }
        if (size > properties.maxSize().toBytes()) {
            throw new IntakeRejectedException("'" + request.filename() + "' is " + size
                    + " bytes; limit is " + properties.maxSize());
        }
    }

    private static long contentLength(IngestionRequest request) {
        try {
            return request.content().contentLength();
        } catch (IOException e) {
            throw new IntakeRejectedException("Could not read '" + request.filename() + "'", e);
        }
    }
}
