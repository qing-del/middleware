package com.jacolp.middleware.module.media.api;

import com.jacolp.middleware.module.media.api.command.ApplyMediaAuditCommand;
import com.jacolp.middleware.module.media.api.model.MediaAuditApplyResult;

/**
 * Applies audit decisions to media files and their note-image relation rows.
 */
public interface MediaAuditApplyApi {

    MediaAuditApplyResult applyMediaAudit(ApplyMediaAuditCommand command);
}
