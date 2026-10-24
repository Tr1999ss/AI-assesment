package com.shk.generic.ingestion.intake;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;

public interface FormatDetector {

    /**
     * @param declared format the caller stated explicitly, if any
     * @return the detected format, or {@link SourceFormat#UNKNOWN}
     */
    SourceFormat detect(@Nullable SourceFormat declared, @Nullable String filename,
                        @Nullable String contentType, Resource content);
}
