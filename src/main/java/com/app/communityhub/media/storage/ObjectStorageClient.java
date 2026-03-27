package com.app.communityhub.media.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public interface ObjectStorageClient {

    PresignedUpload createUploadUrl(String objectKey, String mimeType, Duration ttl);

    PresignedRead createReadUrl(String objectKey, Duration ttl);

    ObjectInfo getObjectInfo(String objectKey);

    byte[] getObjectBytes(String objectKey);

    void deleteObject(String objectKey);

    record PresignedUpload(String url, String method, Map<String, String> headers, Instant expiresAt) {
    }

    record PresignedRead(String url, Instant expiresAt) {
    }

    record ObjectInfo(long size, String eTag, String contentType) {
    }
}
