package com.shk.generic.retrieval.eval;

import com.shk.generic.retrieval.options.DiversifierType;
import com.shk.generic.retrieval.options.InvalidRetrievalOptionException;
import com.shk.generic.retrieval.options.QueryTransformType;
import com.shk.generic.retrieval.options.RerankerType;
import com.shk.generic.retrieval.options.RetrievalMode;
import com.shk.generic.retrieval.options.RetrievalOption;
import com.shk.generic.retrieval.options.RetrievalOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One retrieval configuration to evaluate, written as a compact spec:
 * {@code <mode>[+<transform>][+rerank:<type>][+mmr]}, e.g. {@code semantic}, {@code hybrid+rerank:llm+mmr},
 * {@code semantic+hyde}. The canonical spec is the configuration's name in reports.
 */
public record EvalConfig(String name, RetrievalOptions options) {

    public static EvalConfig parse(String spec) {
        String[] parts = spec.trim().toLowerCase(Locale.ROOT).split("\\+");
        RetrievalMode mode = RetrievalOption.parse(RetrievalMode.class, "configuration mode", parts[0]);
        QueryTransformType transform = QueryTransformType.NONE;
        RerankerType rerank = RerankerType.NONE;
        DiversifierType diversify = DiversifierType.NONE;
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.startsWith("rerank:")) {
                rerank = RetrievalOption.parse(RerankerType.class, "configuration rerank", p.substring(7));
            } else if (p.equals("mmr")) {
                diversify = DiversifierType.MMR;
            } else {
                try {
                    transform = RetrievalOption.parse(QueryTransformType.class, "configuration transform", p);
                } catch (InvalidRetrievalOptionException e) {
                    throw new InvalidRetrievalOptionException("configurations", "Unknown part '" + p + "' in '" + spec
                            + "'. Format: <mode>[+<transform>][+rerank:<type>][+mmr], modes "
                            + RetrievalOption.values(RetrievalMode.class) + ", transforms "
                            + RetrievalOption.values(QueryTransformType.class) + ", rerankers "
                            + RetrievalOption.values(RerankerType.class));
                }
            }
        }
        return of(new RetrievalOptions(mode, transform, rerank, diversify, null, null, false));
    }

    public static EvalConfig of(RetrievalOptions o) {
        List<String> parts = new ArrayList<>();
        parts.add(o.mode().name().toLowerCase(Locale.ROOT));
        if (o.queryTransform() != null && o.queryTransform() != QueryTransformType.NONE) {
            parts.add(o.queryTransform().name().toLowerCase(Locale.ROOT));
        }
        if (o.rerank() != null && o.rerank() != RerankerType.NONE) {
            parts.add("rerank:" + o.rerank().name().toLowerCase(Locale.ROOT));
        }
        if (o.diversify() == DiversifierType.MMR) {
            parts.add("mmr");
        }
        return new EvalConfig(String.join("+", parts), o);
    }
}
