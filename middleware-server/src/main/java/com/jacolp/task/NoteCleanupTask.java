package com.jacolp.task;

import com.jacolp.mapper.NoteImageMappingMapper;
import com.jacolp.mapper.NoteTagMappingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class NoteCleanupTask {

    @Autowired private NoteTagMappingMapper noteTagMappingMapper;

    @Autowired private NoteImageMappingMapper noteImageMappingMapper;

    @Scheduled(fixedRateString = "${jacolp.note.cleanup-task-time:1440}", timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void cleanupSoftDeletedMappings() {
        int tagCount = noteTagMappingMapper.deleteSoftDeletedRows();
        int imageCount = noteImageMappingMapper.deleteSoftDeletedRows();
        log.debug("Note cleanup task finished, tagRows: {}, imageRows: {}", tagCount, imageCount);
    }
}
