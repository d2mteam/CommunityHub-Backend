package com.app.communityhub.media;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class InMemoryObjectStorageClient implements ObjectStorageClient {

    private final Map<String, StoredObject> objects = new HashMap<>();

    @Override
    public PresignedUpload createUploadUrl(String objectKey, String mimeType, Duration ttl) {
        return new PresignedUpload(
                "https://example.test/upload/" + objectKey + "?token=" + UUID.randomUUID(),
                "PUT",
                Map.of("Content-Type", mimeType),
                Instant.now().plus(ttl)
        );
    }

    @Override
    public PresignedRead createReadUrl(String objectKey, Duration ttl) {
        return new PresignedRead(
                "https://example.test/read/" + objectKey + "?token=" + UUID.randomUUID(),
                Instant.now().plus(ttl)
        );
    }

    @Override
    public ObjectInfo getObjectInfo(String objectKey) {
        StoredObject storedObject = objects.get(objectKey);
        if (storedObject == null) {
            throw new IllegalStateException("Object not present in test storage");
        }
        return new ObjectInfo(storedObject.bytes().length, storedObject.eTag(), storedObject.contentType());
    }

    @Override
    public byte[] getObjectBytes(String objectKey) {
        StoredObject storedObject = objects.get(objectKey);
        if (storedObject == null) {
            throw new IllegalStateException("Object not present in test storage");
        }
        return storedObject.bytes();
    }

    @Override
    public void deleteObject(String objectKey) {
        objects.remove(objectKey);
    }

    public void putObject(String objectKey, String contentType, byte[] bytes) {
        objects.put(objectKey, new StoredObject(contentType, bytes, UUID.randomUUID().toString()));
    }

    private record StoredObject(String contentType, byte[] bytes, String eTag) {
    }
}
