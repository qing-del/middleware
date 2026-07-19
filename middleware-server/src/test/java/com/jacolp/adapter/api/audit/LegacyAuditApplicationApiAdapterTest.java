package com.jacolp.adapter.api.audit;

import com.jacolp.mapper.ImageAuditMapper;
import com.jacolp.mapper.MetaAuditMapper;
import com.jacolp.mapper.NoteAuditMapper;
import com.jacolp.middleware.module.audit.api.AuditApplicationResult;
import com.jacolp.middleware.module.audit.api.AuditTargetType;
import com.jacolp.middleware.module.audit.api.CancelAuditApplicationCommand;
import com.jacolp.middleware.module.audit.api.CancelAuditApplicationResult;
import com.jacolp.middleware.module.audit.api.CreateAuditApplicationCommand;
import com.jacolp.middleware.module.audit.api.PendingAuditApplicationQuery;
import com.jacolp.pojo.entity.ImageAuditRecordEntity;
import com.jacolp.pojo.entity.MetaAuditRecordEntity;
import com.jacolp.pojo.entity.NoteAuditRecordEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyAuditApplicationApiAdapterTest {

    private MetaAuditMapper metaAuditMapper;
    private ImageAuditMapper imageAuditMapper;
    private NoteAuditMapper noteAuditMapper;
    private LegacyAuditApplicationApiAdapter adapter;

    @BeforeEach
    void setUp() {
        metaAuditMapper = mock(MetaAuditMapper.class);
        imageAuditMapper = mock(ImageAuditMapper.class);
        noteAuditMapper = mock(NoteAuditMapper.class);
        adapter = new LegacyAuditApplicationApiAdapter(metaAuditMapper, imageAuditMapper, noteAuditMapper);
    }

    @Test
    void tagMapsToMetaAuditAndUsesLegacyUpdateCancellation() {
        when(metaAuditMapper.countPendingAuditByApplyTypeAndTargetId((short) 2, 11L)).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<MetaAuditRecordEntity>getArgument(0).setId(101L);
            return 1;
        }).when(metaAuditMapper).insertAuditRecord(any(MetaAuditRecordEntity.class));
        when(metaAuditMapper.selectPendingByApplyTypeAndTargetId((short) 2, 11L))
                .thenReturn(metaRecord(101L, 22L, (short) 1));
        when(metaAuditMapper.deletePendingByApplyTypeAndTargetId((short) 2, 11L)).thenReturn(1);

        assertThat(adapter.hasPendingApplication(new PendingAuditApplicationQuery(AuditTargetType.TAG, 11L))).isTrue();
        AuditApplicationResult created = adapter.createApplication(
                new CreateAuditApplicationCommand(AuditTargetType.TAG, 11L, 22L, "tag reason"));
        CancelAuditApplicationResult cancelled = adapter.cancelApplication(
                new CancelAuditApplicationCommand(AuditTargetType.TAG, 11L, 22L));

        ArgumentCaptor<MetaAuditRecordEntity> record = ArgumentCaptor.forClass(MetaAuditRecordEntity.class);
        verify(metaAuditMapper).insertAuditRecord(record.capture());
        assertThat(record.getValue().getApplyType()).isEqualTo((short) 2);
        assertThat(record.getValue().getApplicantUserId()).isEqualTo(22L);
        assertThat(created.auditApplicationId()).isEqualTo(101L);
        assertThat(cancelled.cancelledCount()).isEqualTo(1);
        verify(metaAuditMapper).deletePendingByApplyTypeAndTargetId((short) 2, 11L);
        verify(imageAuditMapper, never()).deletePendingByImageId(any());
        verify(noteAuditMapper, never()).deletePendingByNoteId(any());
    }

    @Test
    void imageMapsToImageAuditAndUsesLegacyUpdateCancellation() {
        when(imageAuditMapper.countPendingAuditByImageId(12L)).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<ImageAuditRecordEntity>getArgument(0).setId(102L);
            return 1;
        }).when(imageAuditMapper).insertAuditRecord(any(ImageAuditRecordEntity.class));
        when(imageAuditMapper.selectPendingByImageId(12L)).thenReturn(imageRecord(102L, 23L, (short) 1));
        when(imageAuditMapper.deletePendingByImageId(12L)).thenReturn(1);

        assertThat(adapter.hasPendingApplication(new PendingAuditApplicationQuery(AuditTargetType.IMAGE, 12L))).isTrue();
        AuditApplicationResult created = adapter.createApplication(
                new CreateAuditApplicationCommand(AuditTargetType.IMAGE, 12L, 23L, "image reason"));
        CancelAuditApplicationResult cancelled = adapter.cancelApplication(
                new CancelAuditApplicationCommand(AuditTargetType.IMAGE, 12L, 23L));

        ArgumentCaptor<ImageAuditRecordEntity> record = ArgumentCaptor.forClass(ImageAuditRecordEntity.class);
        verify(imageAuditMapper).insertAuditRecord(record.capture());
        assertThat(record.getValue().getImageId()).isEqualTo(12L);
        assertThat(record.getValue().getApplicantUserId()).isEqualTo(23L);
        assertThat(created.auditApplicationId()).isEqualTo(102L);
        assertThat(cancelled.cancelledCount()).isEqualTo(1);
        verify(imageAuditMapper).deletePendingByImageId(12L);
    }

    @Test
    void noteMapsToNoteAuditAndUsesLegacyDeleteCancellation() {
        when(noteAuditMapper.countPendingAuditByNoteId(13L)).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<NoteAuditRecordEntity>getArgument(0).setId(103L);
            return 1;
        }).when(noteAuditMapper).insertAuditRecord(any(NoteAuditRecordEntity.class));
        when(noteAuditMapper.selectPendingByNoteId(13L)).thenReturn(noteRecord(103L, 24L, (short) 0));
        when(noteAuditMapper.deletePendingByNoteId(13L)).thenReturn(1);

        assertThat(adapter.hasPendingApplication(new PendingAuditApplicationQuery(AuditTargetType.NOTE, 13L))).isTrue();
        AuditApplicationResult created = adapter.createApplication(
                new CreateAuditApplicationCommand(AuditTargetType.NOTE, 13L, 24L, "note reason"));
        CancelAuditApplicationResult cancelled = adapter.cancelApplication(
                new CancelAuditApplicationCommand(AuditTargetType.NOTE, 13L, 24L));

        ArgumentCaptor<NoteAuditRecordEntity> record = ArgumentCaptor.forClass(NoteAuditRecordEntity.class);
        verify(noteAuditMapper).insertAuditRecord(record.capture());
        assertThat(record.getValue().getNoteId()).isEqualTo(13L);
        assertThat(record.getValue().getApplicantUserId()).isEqualTo(24L);
        assertThat(created.auditApplicationId()).isEqualTo(103L);
        assertThat(cancelled.cancelledCount()).isEqualTo(1);
        verify(noteAuditMapper).deletePendingByNoteId(13L);
    }

    @Test
    void cancellationRejectsAnActorOtherThanTheApplicant() {
        when(imageAuditMapper.selectPendingByImageId(12L)).thenReturn(imageRecord(102L, 23L, (short) 1));

        assertThatThrownBy(() -> adapter.cancelApplication(
                new CancelAuditApplicationCommand(AuditTargetType.IMAGE, 12L, 99L)))
                .hasMessage("只能撤销自己的审核申请");

        verify(imageAuditMapper, never()).deletePendingByImageId(eq(12L));
    }

    private static MetaAuditRecordEntity metaRecord(Long id, Long applicantUserId, Short status) {
        MetaAuditRecordEntity record = new MetaAuditRecordEntity();
        record.setId(id);
        record.setApplicantUserId(applicantUserId);
        record.setStatus(status);
        return record;
    }

    private static ImageAuditRecordEntity imageRecord(Long id, Long applicantUserId, Short status) {
        ImageAuditRecordEntity record = new ImageAuditRecordEntity();
        record.setId(id);
        record.setApplicantUserId(applicantUserId);
        record.setStatus(status);
        return record;
    }

    private static NoteAuditRecordEntity noteRecord(Long id, Long applicantUserId, Short status) {
        NoteAuditRecordEntity record = new NoteAuditRecordEntity();
        record.setId(id);
        record.setApplicantUserId(applicantUserId);
        record.setStatus(status);
        return record;
    }
}
