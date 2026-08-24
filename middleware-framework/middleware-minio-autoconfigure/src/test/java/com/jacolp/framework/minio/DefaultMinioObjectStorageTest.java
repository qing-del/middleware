package com.jacolp.framework.minio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class DefaultMinioObjectStorageTest {

    @Test
    void createsMissingBucketBeforeWritingObject() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        new DefaultMinioObjectStorage(minioClient).write("documents", "state.bin", new byte[] {1},
                "application/octet-stream");

        InOrder order = inOrder(minioClient);
        order.verify(minioClient).bucketExists(any(BucketExistsArgs.class));
        order.verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        order.verify(minioClient).putObject(any(PutObjectArgs.class));
        verifyNoMoreInteractions(minioClient);
    }

    @Test
    void doesNotCreateExistingBucketBeforeWritingObject() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        new DefaultMinioObjectStorage(minioClient).write("documents", "state.bin", new byte[] {1},
                "application/octet-stream");

        verify(minioClient).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient).putObject(any(PutObjectArgs.class));
        verifyNoMoreInteractions(minioClient);
    }
}
