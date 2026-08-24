package com.jacolp.framework.elasticsearch;

import java.util.Objects;

final class DefaultElasticsearchIndexResolver implements ElasticsearchIndexResolver {

    private final ElasticsearchProperties properties;

    DefaultElasticsearchIndexResolver(ElasticsearchProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public String requireIndex(String logicalIndexName) {
        if (logicalIndexName == null || logicalIndexName.isBlank()) {
            throw new ElasticsearchOperationException("logical Elasticsearch index name must not be blank");
        }
        String index = properties.getIndex().get(logicalIndexName);
        if (index == null || index.isBlank()) {
            throw new ElasticsearchOperationException(
                    "jacolp.elasticsearch.index.%s must be configured".formatted(logicalIndexName));
        }
        return index;
    }
}
