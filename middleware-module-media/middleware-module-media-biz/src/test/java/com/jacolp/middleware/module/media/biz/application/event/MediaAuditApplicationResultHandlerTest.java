package com.jacolp.middleware.module.media.biz.application.event;

import com.jacolp.middleware.messaging.service.AsyncCommandStateService;
import com.jacolp.middleware.messaging.event.AuditApplicationRequestedEvent;
import com.jacolp.middleware.messaging.event.AuditApplicationResultEvent;
import com.jacolp.module.media.biz.application.event.MediaAuditApplicationResultHandler;
import com.jacolp.module.media.biz.infrastructure.persistence.mapper.ImageMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaAuditApplicationResultHandlerTest {

    @Test
    void correlatedCancelRejectionRestoresTheAuditingImageStatus() {
        AsyncCommandStateService state = mock(AsyncCommandStateService.class);
        ImageMapper images = mock(ImageMapper.class);
        when(state.completeIfCurrent("MEDIA", "IMAGE", 7L, "command-1")).thenReturn(true);
        AuditApplicationResultEvent result = new AuditApplicationResultEvent("command-1",
                AuditApplicationRequestedEvent.TargetType.IMAGE, 7L,
                AuditApplicationResultEvent.Outcome.CANCEL_REJECTED, null, "BUSINESS_REJECTED");

        new MediaAuditApplicationResultHandler(state, images).apply(result);

        verify(images).updateAuditStatusIfCurrent(7L, (short) 0, (short) 1);
    }
}
