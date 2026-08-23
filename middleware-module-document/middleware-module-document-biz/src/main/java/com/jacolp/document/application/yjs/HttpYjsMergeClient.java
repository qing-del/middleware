package com.jacolp.document.application.yjs;

import java.util.List;

import com.jacolp.document.config.YjsMergeServiceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** HTTP adapter for the isolated Yjs merge service. */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class HttpYjsMergeClient implements YjsMergeClient {

    private static final String MERGE_PATH = "/internal/yjs/merge";

    private final RestClient restClient;

    public HttpYjsMergeClient(YjsMergeServiceProperties properties) {
        this(RestClient.create(properties.requireBaseUrl()));
    }

    HttpYjsMergeClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public byte[] merge(byte[] baseState, List<byte[]> updates) {
        YjsMergeRequest request = YjsMergeRequest.from(baseState, updates);
        try {
            YjsMergeResponse response = restClient.post()
                    .uri(MERGE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(YjsMergeResponse.class);
            if (response == null) {
                throw new YjsMergeException("Yjs merge service returned an empty response");
            }
            return response.decodeMergedState();
        } catch (YjsMergeException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new YjsMergeException("Yjs merge service request failed", exception);
        }
    }
}
