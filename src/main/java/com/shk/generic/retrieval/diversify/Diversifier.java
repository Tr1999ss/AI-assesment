package com.shk.generic.retrieval.diversify;

import com.shk.generic.retrieval.options.DiversifierType;
import com.shk.generic.retrieval.pipeline.Candidate;

import java.util.List;

/** Picks the final top K from the ranked candidate pool. */
public interface Diversifier {

    DiversifierType type();

    /** @param ranked best first; @return at most {@code k} candidates, in final order */
    List<Candidate> select(List<Candidate> ranked, int k, double lambda);
}
