package com.jacolp.framework.elasticsearch;

/** Runtime exception used by the module-neutral Elasticsearch operations API. */
public class ElasticsearchOperationException extends RuntimeException {

    public ElasticsearchOperationException(String message) {
        super(message);
    }

    public ElasticsearchOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
