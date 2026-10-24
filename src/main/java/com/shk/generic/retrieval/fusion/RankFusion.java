package com.shk.generic.retrieval.fusion;

import com.shk.generic.retrieval.pipeline.Candidate;
import com.shk.generic.retrieval.pipeline.RankedList;

import java.util.List;

/** Merges several ranked lists (legs × query variants) into one, best first. */
public interface RankFusion {

    List<Candidate> fuse(List<RankedList> lists);
}
