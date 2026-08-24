package com.jacolp.document.application.yjs;

import java.util.List;

import com.jacolp.document.config.YjsMergeServiceProperties;
import com.jacolp.document.metrics.DocumentMetrics;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 通过 HTTP 调用隔离 Yjs 合并服务的适配器。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class HttpYjsMergeClient implements YjsMergeClient {

    private static final String MERGE_PATH = "/internal/yjs/merge";

    private final RestClient restClient;
    private final DocumentMetrics metrics;

    public HttpYjsMergeClient(YjsMergeServiceProperties properties) {
        this(RestClient.create(properties.requireBaseUrl()), DocumentMetrics.noop());
    }

    HttpYjsMergeClient(RestClient restClient) {
        this(restClient, DocumentMetrics.noop());
    }

    @Autowired
    public HttpYjsMergeClient(YjsMergeServiceProperties properties, DocumentMetrics metrics) {
        this(RestClient.create(properties.requireBaseUrl()), metrics);
    }

    HttpYjsMergeClient(RestClient restClient, DocumentMetrics metrics) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override
    public byte[] merge(byte[] baseState, List<byte[]> updates) {
        Timer.Sample sample = metrics.startYjsMerge();
        boolean failed = true;
        try {
            YjsMergeRequest request = YjsMergeRequest.from(baseState, updates);
            YjsMergeResponse response = restClient.post()
                    .uri(MERGE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(YjsMergeResponse.class);
            if (response == null) {
                throw new YjsMergeException("Yjs merge service returned an empty response");
            }
            byte[] mergedState = response.decodeMergedState();
            failed = false;
            return mergedState;
        } catch (YjsMergeException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new YjsMergeException("Yjs merge service request failed", exception);
        } finally {
            metrics.completeYjsMerge(sample, failed);
        }
    }
}
