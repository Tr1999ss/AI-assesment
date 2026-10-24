package com.shk.generic.ingestion.extract;

import com.shk.generic.ingestion.intake.SourceFormat;

public interface ExtractorResolver {

    DocumentExtractor resolve(SourceFormat format);
}
