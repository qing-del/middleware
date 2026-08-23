package com.jacolp.document.application.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.document.api.model.DocumentMetadata;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentMetadataServiceTest {

    @Test
    void createsDocumentInAuthenticatedPersonalScopeAndNormalizesTitle() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        when(mapper.insert(any(DocumentDO.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, DocumentDO.class).setId(7L);
            return 1;
        });
        DocumentMetadataService service = new DocumentMetadataService(mapper, mock(DocumentRedisRepository.class));

        DocumentMetadata metadata = service.create(42L, "  Project plan  ");

        assertThat(metadata.documentId()).isEqualTo(7L);
        assertThat(metadata.teamId()).isEqualTo(42L);
        assertThat(metadata.title()).isEqualTo("Project plan");
        ArgumentCaptor<DocumentDO> document = ArgumentCaptor.forClass(DocumentDO.class);
        verify(mapper).insert(document.capture());
        assertThat(document.getValue().getTeamId()).isEqualTo(42L);
        assertThat(document.getValue().getLastModifyUserId()).isEqualTo(42L);
        assertThat(document.getValue().getContentObjectKey()).isNull();
        assertThat(document.getValue().getPersistedLogId()).isZero();
    }

    @Test
    void listsOnlyDocumentsFromAuthenticatedPersonalScope() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        when(mapper.listActiveByTeamId(42L)).thenReturn(List.of(document(7L, 42L, "Plan")));
        DocumentMetadataService service = new DocumentMetadataService(mapper, mock(DocumentRedisRepository.class));

        assertThat(service.list(42L)).extracting(DocumentMetadata::documentId).containsExactly(7L);
        verify(mapper).listActiveByTeamId(42L);
    }

    @Test
    void updatesTitleWithinScopeAndReturnsFreshMetadata() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        when(mapper.updateTitleIfActive(eq(7L), eq(42L), eq("New title"), any(LocalDateTime.class), eq(42L)))
                .thenReturn(1);
        when(mapper.selectActiveByIdAndTeamId(7L, 42L)).thenReturn(document(7L, 42L, "New title"));
        DocumentMetadataService service = new DocumentMetadataService(mapper, mock(DocumentRedisRepository.class));

        assertThat(service.updateTitle(42L, 7L, " New title ").title()).isEqualTo("New title");
    }

    @Test
    void rejectsDeleteWhileAnyInstanceHasAnActivePresence() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        DocumentRedisRepository redis = mock(DocumentRedisRepository.class);
        when(mapper.selectActiveByIdAndTeamId(7L, 42L)).thenReturn(document(7L, 42L, "Plan"));
        when(redis.countPresence(7L)).thenReturn(1L);
        DocumentMetadataService service = new DocumentMetadataService(mapper, redis);

        assertThatThrownBy(() -> service.delete(42L, 7L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("活跃协作会话");
        verify(mapper, never()).softDeleteByIdAndTeamId(any(), any(), any(), any());
    }

    @Test
    void softDeletesInactiveDocumentWithoutCleaningItsCrdtData() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        DocumentRedisRepository redis = mock(DocumentRedisRepository.class);
        when(mapper.selectActiveByIdAndTeamId(7L, 42L)).thenReturn(document(7L, 42L, "Plan"));
        when(redis.countPresence(7L)).thenReturn(0L);
        when(mapper.softDeleteByIdAndTeamId(eq(7L), eq(42L), any(LocalDateTime.class), eq(42L))).thenReturn(1);
        DocumentMetadataService service = new DocumentMetadataService(mapper, redis);

        service.delete(42L, 7L);

        verify(mapper).softDeleteByIdAndTeamId(eq(7L), eq(42L), any(LocalDateTime.class), eq(42L));
        verify(redis, never()).deleteRoomRuntime(any(Long.class));
    }

    private static DocumentDO document(long id, long teamId, String title) {
        LocalDateTime now = LocalDateTime.now();
        return new DocumentDO(id, teamId, title, null, 0L, now, teamId, false, 0L, now, now);
    }
}
