package com.jacolp.adapter.api.media;

import com.jacolp.mapper.ImageMapper;
import com.jacolp.mapper.NoteImageMappingMapper;
import com.jacolp.middleware.module.media.api.MediaAuditApplyApi;
import com.jacolp.middleware.module.media.api.command.ApplyMediaAuditCommand;
import com.jacolp.middleware.module.media.api.model.MediaAuditApplyResult;
import com.jacolp.middleware.module.media.api.model.MediaAuditDecision;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Transitional audit writer preserving the legacy image and note-image relation status mapping.
 */
@Component
public class ServerMediaAuditApplyApiAdapter implements MediaAuditApplyApi {

    private static final short MEDIA_APPROVED = 2;
    private static final short MEDIA_REJECTED = 3;

    private final ImageMapper imageMapper;
    private final NoteImageMappingMapper noteImageMappingMapper;

    public ServerMediaAuditApplyApiAdapter(ImageMapper imageMapper,
                                           NoteImageMappingMapper noteImageMappingMapper) {
        this.imageMapper = imageMapper;
        this.noteImageMappingMapper = noteImageMappingMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MediaAuditApplyResult applyMediaAudit(ApplyMediaAuditCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        List<Long> ids = normalizeIds(command.mediaIds());
        if (ids.isEmpty()) {
            return new MediaAuditApplyResult(0, 0);
        }
        short status = command.decision() == MediaAuditDecision.APPROVED ? MEDIA_APPROVED : MEDIA_REJECTED;
        int mediaRows = imageMapper.updateAuditStatusByIds(ids, status);
        int relationRows = noteImageMappingMapper.updateByImageIds(ids, status);
        return new MediaAuditApplyResult(mediaRows, relationRows);
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        Objects.requireNonNull(ids, "mediaIds must not be null");
        return ids.stream()
                .peek(id -> {
                    if (id == null || id <= 0) {
                        throw new IllegalArgumentException("mediaIds must contain positive ids only");
                    }
                })
                .distinct()
                .toList();
    }
}
