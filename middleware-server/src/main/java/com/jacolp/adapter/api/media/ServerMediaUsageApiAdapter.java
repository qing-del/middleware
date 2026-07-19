package com.jacolp.adapter.api.media;

import com.jacolp.mapper.ImageMapper;
import com.jacolp.middleware.module.media.api.MediaUsageApi;
import org.springframework.stereotype.Component;

/**
 * Transitional media usage reader backed by the legacy image mapper.
 */
@Component
public class ServerMediaUsageApiAdapter implements MediaUsageApi {

    private final ImageMapper imageMapper;

    public ServerMediaUsageApiAdapter(ImageMapper imageMapper) {
        this.imageMapper = imageMapper;
    }

    @Override
    public long getUserStorageUsageBytes(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Long usage = imageMapper.sumImageFileSizeByUserId(userId);
        return usage == null ? 0L : usage;
    }
}
