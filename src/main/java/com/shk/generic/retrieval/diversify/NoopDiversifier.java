package com.shk.generic.retrieval.diversify;

import com.shk.generic.retrieval.options.DiversifierType;
import com.shk.generic.retrieval.pipeline.Candidate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NoopDiversifier implements Diversifier {

    @Override
    public DiversifierType type() {
        return DiversifierType.NONE;
    }

    @Override
    public List<Candidate> select(List<Candidate> ranked, int k, double lambda) {
        return ranked.subList(0, Math.min(k, ranked.size()));
    }
}
