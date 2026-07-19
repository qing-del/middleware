package com.jacolp.adapter.api.media;

import com.jacolp.mapper.ImageMapper;
import com.jacolp.mapper.NoteImageMappingMapper;
import com.jacolp.middleware.module.media.api.command.ApplyMediaAuditCommand;
import com.jacolp.middleware.module.media.api.command.MediaFileLookupCommand;
import com.jacolp.middleware.module.media.api.model.MediaAuditDecision;
import com.jacolp.middleware.module.media.api.model.MediaReviewStatus;
import com.jacolp.pojo.entity.ImageEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerMediaApiAdapterTest {

    @Test
    void lookupUsesOneScopedBatchQueryAndMapsMediaSummary() {
        ImageMapper imageMapper = mock(ImageMapper.class);
        ImageEntity image = image(12L, (short) 2);
        when(imageMapper.selectByUserIdAndTopicIdAndFilenames(8L, null, List.of("cover.png")))
                .thenReturn(List.of(image));

        var summaries = new ServerMediaFileApiAdapter(imageMapper).findByOwnerTopicAndFilenames(
                new MediaFileLookupCommand(8L, null, List.of("cover.png", "cover.png")));

        assertEquals(12L, summaries.get("cover.png").id());
        assertEquals(MediaReviewStatus.APPROVED, summaries.get("cover.png").status());
        assertEquals(true, summaries.get("cover.png").publiclyVisible());
        verify(imageMapper).selectByUserIdAndTopicIdAndFilenames(8L, null, List.of("cover.png"));
    }

    @Test
    void mediaAuditMapsRejectionToLegacyImageAndRelationStatus() {
        ImageMapper imageMapper = mock(ImageMapper.class);
        NoteImageMappingMapper mappingMapper = mock(NoteImageMappingMapper.class);
        when(imageMapper.updateAuditStatusByIds(List.of(4L), (short) 3)).thenReturn(1);
        when(mappingMapper.updateByImageIds(List.of(4L), (short) 3)).thenReturn(2);

        var result = new ServerMediaAuditApplyApiAdapter(imageMapper, mappingMapper).applyMediaAudit(
                new ApplyMediaAuditCommand(List.of(4L), MediaAuditDecision.REJECTED));

        assertEquals(1, result.mediaRowsUpdated());
        assertEquals(2, result.relationRowsUpdated());
        verify(imageMapper).updateAuditStatusByIds(List.of(4L), (short) 3);
        verify(mappingMapper).updateByImageIds(List.of(4L), (short) 3);
    }

    @Test
    void usageNormalizesNullAggregateToZero() {
        ImageMapper imageMapper = mock(ImageMapper.class);
        when(imageMapper.sumImageFileSizeByUserId(9L)).thenReturn(null);

        assertEquals(0L, new ServerMediaUsageApiAdapter(imageMapper).getUserStorageUsageBytes(9L));
        verify(imageMapper).sumImageFileSizeByUserId(9L);
    }

    private static ImageEntity image(Long id, short auditStatus) {
        ImageEntity image = new ImageEntity();
        image.setId(id);
        image.setUserId(8L);
        image.setFilename("cover.png");
        image.setOssUrl("https://example.test/cover.png");
        image.setFileSize(128L);
        image.setIsPublic((short) 1);
        image.setAuditStatus(auditStatus);
        return image;
    }
}
