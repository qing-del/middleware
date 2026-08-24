package com.jacolp.framework.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import java.util.Optional;

/**
 * Common synchronous Elasticsearch document operations for modules that do not need the raw client.
 *
 * <p>Index mappings, analyzers, aliases, and business query construction remain the responsibility
 * of the owning module. The official {@code ElasticsearchClient} is still exposed for those advanced
 * cases.</p>
 */
public interface ElasticsearchOperations {

    boolean indexExists(String indexName);

    void createIndex(String indexName);

    <T> ElasticsearchWriteResult index(String indexName, String documentId, T document);

    <T> Optional<T> get(String indexName, String documentId, Class<T> documentType);

    boolean delete(String indexName, String documentId);

    <T> ElasticsearchSearchPage<T> search(String indexName, Query query, int from, int size,
                                           Class<T> documentType);
}
