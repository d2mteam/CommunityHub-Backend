package com.app.communityhub.media;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAssetEntity, UUID> {

    Optional<MediaAssetEntity> findByMediaKey(String mediaKey);

    List<MediaAssetEntity> findAllByMediaKeyIn(Collection<String> mediaKeys);

    List<MediaAssetEntity> findAllByStatusInAndUpdatedAtBefore(Collection<MediaStatus> statuses, Instant cutoff);
}
