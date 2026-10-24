package com.shk.generic.ingestion.category;

import com.shk.generic.ingestion.extract.ExtractedDocument;
import com.shk.generic.ingestion.registry.RegisteredDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Caller supplied categories → validate them; none supplied → classify. */
@Component
public class DefaultCategorizationStage implements CategorizationStage {

    private final CategoryResolver supplied;
    private final CategoryResolver classifier;

    public DefaultCategorizationStage(@Qualifier("suppliedCategoryResolver") CategoryResolver supplied,
                                      @Qualifier("aiCategoryClassifier") CategoryResolver classifier) {
        this.supplied = supplied;
        this.classifier = classifier;
    }

    @Override
    public CategorizedDocument categorize(ExtractedDocument document, RegisteredDocument registration) {
        CategoryResolver resolver = document.request().hasRequestedCategories() ? supplied : classifier;
        CategoryResolution resolution = resolver.resolve(document);
        return new CategorizedDocument(document, registration, resolution.categories(), resolution.source());
    }
}
