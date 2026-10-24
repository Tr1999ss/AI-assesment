package com.shk.generic.ingestion.extract;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Unicode NFC, every whitespace run collapsed to one space, trimmed. */
@Component
public class WhitespaceTextNormalizer implements TextNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @Override
    public String normalize(List<Document> sections) {
        String joined = sections.stream()
                .map(Document::getText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        String nfc = Normalizer.normalize(joined, Normalizer.Form.NFC);
        return WHITESPACE.matcher(nfc).replaceAll(" ").strip();
    }
}
