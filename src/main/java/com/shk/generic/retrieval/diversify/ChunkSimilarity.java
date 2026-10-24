package com.shk.generic.retrieval.diversify;

import java.util.List;

/** Pairwise similarity between stored chunks, for diversification. */
public interface ChunkSimilarity {

    /** @return similarities for every pair of the given chunks; pairs that can't be computed are absent (treated as 0) */
    SimilarityMatrix pairwise(List<String> chunkIds);
}
