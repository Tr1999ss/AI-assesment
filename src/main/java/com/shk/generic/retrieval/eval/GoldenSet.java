package com.shk.generic.retrieval.eval;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Questions with known relevant chunks. Relevant items are keyed by source file and (optionally) section title,
 * which are stable across re-ingestion; chunk UUIDs are derived from random document ids and are not.
 */
public record GoldenSet(String name, @Nullable String description, List<CorpusFile> corpus, List<Question> questions) {

    public GoldenSet {
        corpus = corpus == null ? List.of() : List.copyOf(corpus);
        questions = List.copyOf(questions);
    }

    /** A file under {@code eval/corpus/} to ingest with these categories when seeding. */
    public record CorpusFile(String file, List<String> categories) {
    }

    public record Question(String id, @Nullable List<String> tags, String question, List<RelevantItem> relevant) {
        public Question {
            relevant = List.copyOf(relevant);
        }
    }

    /** @param section section heading; null means any chunk of the source counts */
    public record RelevantItem(String source, @Nullable String section) {
    }
}
