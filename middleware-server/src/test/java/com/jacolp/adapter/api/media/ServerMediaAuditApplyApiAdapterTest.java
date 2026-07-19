package com.jacolp.adapter.api.media;

import com.jacolp.mapper.NoteImageMappingMapper;
import com.jacolp.middleware.module.media.api.command.ApplyMediaAuditCommand;
import com.jacolp.middleware.module.media.api.model.MediaAuditDecision;
import com.jacolp.middleware.module.media.biz.infrastructure.persistence.mapper.ImageMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerMediaAuditApplyApiAdapterTest {

    @Test
    void rejectionUpdatesMediaAndNoteRelationsWithDeduplicatedIds() {
        ImageMapper imageMapper = mock(ImageMapper.class);
        NoteImageMappingMapper mappingMapper = mock(NoteImageMappingMapper.class);
        when(imageMapper.updateAuditStatusByIds(List.of(4L), (short) 3)).thenReturn(1);
        when(mappingMapper.updateByImageIds(List.of(4L), (short) 3)).thenReturn(2);

        var result = new ServerMediaAuditApplyApiAdapter(imageMapper, mappingMapper).applyMediaAudit(
                new ApplyMediaAuditCommand(List.of(4L, 4L), MediaAuditDecision.REJECTED));

        assertEquals(1, result.mediaRowsUpdated());
        assertEquals(2, result.relationRowsUpdated());
        verify(imageMapper).updateAuditStatusByIds(List.of(4L), (short) 3);
        verify(mappingMapper).updateByImageIds(List.of(4L), (short) 3);
    }
}
