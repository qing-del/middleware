package com.jacolp.adapter.api.note;

import com.jacolp.mapper.NoteEachMappingMapper;
import com.jacolp.mapper.NoteImageMappingMapper;
import com.jacolp.mapper.NoteMapper;
import com.jacolp.mapper.NoteTagMappingMapper;
import com.jacolp.mapper.TagMapper;
import com.jacolp.middleware.module.note.api.command.ApplyNoteAuditCommand;
import com.jacolp.middleware.module.note.api.model.AuditDecision;
import com.jacolp.middleware.module.note.api.model.NoteLifecycleStatus;
import com.jacolp.middleware.module.note.api.model.NoteSummary;
import com.jacolp.pojo.dto.image.ImageNoteCountDTO;
import com.jacolp.pojo.entity.NoteEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerNoteApiAdapterTest {

    @Test
    void noteSummariesUseOneBatchQueryAndOmitDeletedRows() {
        NoteMapper noteMapper = mock(NoteMapper.class);
        NoteEntity visible = note(2L, (short) 5);
        NoteEntity deleted = note(1L, (short) 8);
        when(noteMapper.selectByIds(List.of(2L, 1L))).thenReturn(List.of(visible, deleted));

        Map<Long, NoteSummary> summaries = new ServerNoteReadApiAdapter(noteMapper, mock(TagMapper.class),
                mock(NoteImageMappingMapper.class)).findNoteSummariesByIds(List.of(2L, 1L, 2L));

        assertEquals(new NoteSummary(2L, 9L, 3L, "readme.md", NoteLifecycleStatus.APPROVED), summaries.get(2L));
        assertEquals(1, summaries.size());
        verify(noteMapper).selectByIds(List.of(2L, 1L));
    }

    @Test
    void mediaReferenceCountsInitializeUnreferencedIdsToZeroInOneBatchCall() {
        NoteImageMappingMapper mappingMapper = mock(NoteImageMappingMapper.class);
        ImageNoteCountDTO count = new ImageNoteCountDTO(2L, null, 4);
        when(mappingMapper.countByImageIds(List.of(2L, 1L))).thenReturn(List.of(count));

        Map<Long, Long> counts = new ServerNoteReadApiAdapter(mock(NoteMapper.class), mock(TagMapper.class), mappingMapper)
                .countMediaReferencesByMediaIds(List.of(2L, 1L, 2L));

        assertEquals(Map.of(2L, 4L, 1L, 0L), counts);
        verify(mappingMapper).countByImageIds(List.of(2L, 1L));
    }

    @Test
    void noteAuditMapsApprovalToLegacyNoteAndRelationStatuses() {
        NoteMapper noteMapper = mock(NoteMapper.class);
        NoteEachMappingMapper eachMapper = mock(NoteEachMappingMapper.class);
        when(noteMapper.updateStatusByIds(List.of(7L), (short) 5)).thenReturn(1);
        when(eachMapper.updateBySourceNoteIds(List.of(7L), (short) 1)).thenReturn(3);

        var result = new ServerNoteAuditApplyApiAdapter(noteMapper, mock(TagMapper.class), eachMapper,
                mock(NoteTagMappingMapper.class)).applyNoteAudit(
                        new ApplyNoteAuditCommand(List.of(7L), AuditDecision.APPROVED));

        assertEquals(1, result.noteRowsUpdated());
        assertEquals(3, result.relationRowsUpdated());
        verify(noteMapper).updateStatusByIds(List.of(7L), (short) 5);
        verify(eachMapper).updateBySourceNoteIds(List.of(7L), (short) 1);
    }

    private static NoteEntity note(Long id, short status) {
        NoteEntity note = new NoteEntity();
        note.setId(id);
        note.setUserId(9L);
        note.setTopicId(3L);
        note.setTitle("readme.md");
        note.setStatus(status);
        return note;
    }
}
