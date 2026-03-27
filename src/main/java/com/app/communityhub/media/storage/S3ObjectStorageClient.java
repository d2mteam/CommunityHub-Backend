package com.app.communityhub.media.storage;

import com.app.communityhub.common.AppException;
import com.app.communityhub.config.AppProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class S3ObjectStorageClient implements ObjectStorageClient {

    private final AppProperties appProperties;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    private S3Client s3Client() {
        AppProperties.Media media = appProperties.getMedia();
        return S3Client.builder()
                .endpointOverride(URI.create(media.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(media.getAccessKey(), media.getSecretKey())
                ))
                .region(Region.of(media.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(media.isPathStyleAccessEnabled())
                        .build())
                .build();
    }

    private S3Presigner s3Presigner() {
        AppProperties.Media media = appProperties.getMedia();
        return S3Presigner.builder()
                .endpointOverride(URI.create(media.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(media.getAccessKey(), media.getSecretKey())
                ))
                .region(Region.of(media.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(media.isPathStyleAccessEnabled())
                        .build())
                .build();
    }

    @Override
    public PresignedUpload createUploadUrl(String objectKey, String mimeType, Duration ttl) {
        ensureBucketExists();
        try (S3Presigner presigner = s3Presigner()) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(appProperties.getMedia().getBucket())
                    .key(objectKey)
                    .contentType(mimeType)
                    .build();
            PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .putObjectRequest(request)
                    .build());
            return new PresignedUpload(
                    presignedRequest.url().toString(),
                    "PUT",
                    Map.of("Content-Type", mimeType),
                    Instant.now().plus(ttl)
            );
        }
    }

    @Override
    public PresignedRead createReadUrl(String objectKey, Duration ttl) {
        ensureBucketExists();
        try (S3Presigner presigner = s3Presigner()) {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(appProperties.getMedia().getBucket())
                    .key(objectKey)
                    .build();
            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(request)
                    .build());
            return new PresignedRead(presignedRequest.url().toString(), Instant.now().plus(ttl));
        }
    }

    @Override
    public ObjectInfo getObjectInfo(String objectKey) {
        ensureBucketExists();
        try (S3Client client = s3Client()) {
            var response = client.headObject(HeadObjectRequest.builder()
                    .bucket(appProperties.getMedia().getBucket())
                    .key(objectKey)
                    .build());
            return new ObjectInfo(response.contentLength(), response.eTag(), response.contentType());
        } catch (NoSuchKeyException exception) {
            throw new AppException(HttpStatus.CONFLICT, "Uploaded object not found");
        }
    }

    @Override
    public byte[] getObjectBytes(String objectKey) {
        ensureBucketExists();
        try (S3Client client = s3Client()) {
            ResponseBytes<?> response = client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(appProperties.getMedia().getBucket())
                    .key(objectKey)
                    .build());
            return response.asByteArray();
        } catch (NoSuchKeyException exception) {
            throw new AppException(HttpStatus.NOT_FOUND, "Object not found");
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        ensureBucketExists();
        try (S3Client client = s3Client()) {
            client.deleteObject(builder -> builder.bucket(appProperties.getMedia().getBucket()).key(objectKey));
        }
    }

    private void ensureBucketExists() {
        if (bucketReady.get()) {
            return;
        }
        try (S3Client client = s3Client()) {
            try {
                client.headBucket(HeadBucketRequest.builder().bucket(appProperties.getMedia().getBucket()).build());
            } catch (NoSuchBucketException exception) {
                client.createBucket(CreateBucketRequest.builder().bucket(appProperties.getMedia().getBucket()).build());
            }
            bucketReady.set(true);
        }
    }
}
