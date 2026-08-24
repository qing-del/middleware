package com.jacolp.framework.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class DefaultElasticsearchOperations implements ElasticsearchOperations {

    private final ElasticsearchClient client;

    DefaultElasticsearchOperations(ElasticsearchClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public boolean indexExists(String indexName) {
        validateIndexName(indexName);
        try {
            return client.indices().exists(request -> request.index(indexName)).value();
        } catch (IOException exception) {
            throw new ElasticsearchOperationException("could not determine whether Elasticsearch index exists", exception);
        }
    }

    @Override
    public void createIndex(String indexName) {
        validateIndexName(indexName);
        try {
            client.indices().create(request -> request.index(indexName));
        } catch (IOException exception) {
            throw new ElasticsearchOperationException("could not create Elasticsearch index", exception);
        }
    }

    @Override
    public <T> ElasticsearchWriteResult index(String indexName, String documentId, T document) {
        validateIndexName(indexName);
        validateDocumentId(documentId);
        Objects.requireNonNull(document, "document must not be null");
        try {
            IndexResponse response = client.index(request -> request.index(indexName).id(documentId).document(document));
            return new ElasticsearchWriteResult(response.id(), response.version(), response.result().jsonValue());
        } catch (IOException exception) {
            throw new ElasticsearchOperationException("could not index Elasticsearch document", exception);
        }
    }

    @Override
    public <T> Optional<T> get(String indexName, String documentId, Class<T> documentType) {
        validateIndexName(indexName);
        validateDocumentId(documentId);
        Objects.requireNonNull(documentType, "documentType must not be null");
        try {
            GetResponse<T> response = client.get(request -> request.index(indexName).id(documentId), documentType);
            return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();
        } catch (IOException exception) {
            throw new ElasticsearchOperationException("could not get Elasticsearch document", exception);
        }
    }

    @Override
    public boolean delete(String indexName, String documentId) {
        validateIndexName(indexName);
        validateDocumentId(documentId);
        try {
            DeleteResponse response = client.delete(request -> request.index(indexName).id(documentId));
            return response.result() == Result.Deleted;
        } catch (IOException exception) {
            throw new ElasticsearchOperationException("could not delete Elasticsearch document", exception);
        }
    }

    @Override
    public <T> ElasticsearchSearchPage<T> search(String indexName, Query query, int from, int size,
                                                   Class<T> documentType) {
        validateIndexName(indexName);
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(documentType, "documentType must not be null");
        if (from < 0) {
            throw new IllegalArgumentException("from must not be negative");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        try {
            SearchResponse<T> response = client.search(request -> request.index(indexName).query(query)
                    .from(from).size(size), documentType);
            List<ElasticsearchSearchHit<T>> hits = response.hits().hits().stream()
                    .map(hit -> new ElasticsearchSearchHit<>(hit.id(), hit.source(), hit.score()))
                    .toList();
            TotalHits total = response.hits().total();
            return new ElasticsearchSearchPage<>(total == null ? hits.size() : total.value(), hits);
        } catch (IOException exception) {
            throw new ElasticsearchOperationException("could not search Elasticsearch documents", exception);
        }
    }

    private static void validateIndexName(String indexName) {
        if (indexName == null || indexName.isBlank()) {
            throw new IllegalArgumentException("indexName must not be blank");
        }
    }

    private static void validateDocumentId(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
    }
}
