package com.jacolp.framework.elasticsearch;

import java.util.List;

/** Immutable page of source-backed Elasticsearch hits. */
public record ElasticsearchSearchPage<T>(long total, List<ElasticsearchSearchHit<T>> hits) {

    public ElasticsearchSearchPage {
        hits = List.copyOf(hits);
    }
}
