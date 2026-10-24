package com.shk.generic.ingestion.intake;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.util.Set;

@ConfigurationProperties("ingestion.intake")
public record IntakeProperties(
        @DefaultValue("20MB") DataSize maxSize,
        @DefaultValue({"PDF", "MARKDOWN", "JSON", "TEXT"}) Set<SourceFormat> allowedFormats
) {
}
