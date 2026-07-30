package com.letchat.storage;

import com.letchat.entity.enums.ResponseCodeEnum;
import com.letchat.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@Slf4j
public class MinioFileStorage implements FileStorage {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioFileStorage(@Value("${letchat.storage.endpoint:http://localhost:39000}") String endpoint,
                            @Value("${letchat.storage.access-key:letchat}") String accessKey,
                            @Value("${letchat.storage.secret-key:letchat-secret}") String secretKey,
                            @Value("${letchat.storage.bucket:letchat}") String bucket) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
    }

    @PostConstruct
    void ensureBucket() {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize MinIO bucket " + bucket, exception);
        }
    }

    @Override
    public void put(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception exception) {
            log.error("Failed to upload object, key={}", objectKey, exception);
            throw new BusinessException("上传文件失败");
        }
    }

    @Override
    public StoredObject get(String objectKey) {
        try {
            GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            String contentLength = response.headers().get("Content-Length");
            long size = contentLength == null ? -1 : Long.parseLong(contentLength);
            String contentType = response.headers().get("Content-Type");
            return new StoredObject(response, size, contentType);
        } catch (Exception exception) {
            log.warn("Object not found or unavailable, key={}", objectKey, exception);
            throw new BusinessException(ResponseCodeEnum.CODE_602);
        }
    }
}
