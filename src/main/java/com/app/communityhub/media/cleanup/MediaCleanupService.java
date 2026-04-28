package com.app.communityhub.media.cleanup;

import com.app.communityhub.config.AppProperties;
import com.app.communityhub.media.MediaAssetEntity;
import com.app.communityhub.media.MediaAssetRepository;
import com.app.communityhub.media.MediaStatus;
import com.app.communityhub.media.storage.ObjectStorageClient;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaCleanupService {

    private final AppProperties appProperties;
    private final MediaAssetRepository mediaAssetRepository;
    private final ObjectStorageClient objectStorageClient;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupOrphanedMedia() {
        Instant cutoff = Instant.now().minus(appProperties.getMedia().getOrphanRetention());
        List<MediaAssetEntity> staleMedia = mediaAssetRepository.findAllByStatusInAndUpdatedAtBefore(
                List.of(MediaStatus.RESERVED, MediaStatus.UPLOADED, MediaStatus.ORPHANED),
                cutoff
        );
        int deleteFailures = 0;
        int processed = 0;
        for (MediaAssetEntity mediaAsset : staleMedia) {
            if (mediaAsset.getStatus() == MediaStatus.ATTACHED) {
                continue;
            }
            mediaAsset.markOrphaned(Instant.now());
            processed++;
            try {
                objectStorageClient.deleteObject(mediaAsset.getObjectKey());
            } catch (Exception exception) {
                deleteFailures++;
                log.warn("Could not delete orphaned object {}", mediaAsset.getObjectKey(), exception);
            }
        }
        if (processed > 0 || deleteFailures > 0) {
            log.info(
                    "Completed orphaned media cleanup [candidates={}, processed={}, deleteFailures={}]",
                    staleMedia.size(),
                    processed,
                    deleteFailures
            );
        }
    }
}
