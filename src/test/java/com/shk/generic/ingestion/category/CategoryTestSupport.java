package com.shk.generic.ingestion.category;

import com.shk.generic.category.CategoryDefinition;
import com.shk.generic.category.CategoryRegistry;
import com.shk.generic.ingestion.extract.ExtractedDocument;
import com.shk.generic.ingestion.intake.IngestionRequest;
import com.shk.generic.ingestion.intake.IntakeChannel;
import com.shk.generic.ingestion.intake.NamedByteArrayResource;
import com.shk.generic.ingestion.intake.SourceFormat;
import org.springframework.ai.document.Document;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CategoryTestSupport {

    private CategoryTestSupport() {
    }

    public static CategoryRegistry registry(CategoryDefinition... definitions) {
        List<CategoryDefinition> all = Arrays.asList(definitions);
        return new CategoryRegistry() {
            @Override
            public List<CategoryDefinition> active() {
                return all.stream().filter(CategoryDefinition::active).toList();
            }

            @Override
            public Optional<CategoryDefinition> find(String name) {
                return all.stream().filter(c -> c.name().equals(name)).findFirst();
            }
        };
    }

    public static ExtractedDocument document(String text, List<String> categories, Document... sections) {
        IngestionRequest request = IngestionRequest.of(IntakeChannel.JSON_TEXT, "doc.md", SourceFormat.MARKDOWN,
                new NamedByteArrayResource(text.getBytes(StandardCharsets.UTF_8), "doc.md"), categories, Map.of());
        List<Document> docs = sections.length == 0 ? List.of(new Document(text)) : List.of(sections);
        return new ExtractedDocument(request, docs, text);
    }
}
