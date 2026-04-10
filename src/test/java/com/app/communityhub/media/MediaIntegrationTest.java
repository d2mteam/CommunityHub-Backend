package com.app.communityhub.media;

import com.app.communityhub.auth.api.AuthResponse;
import com.app.communityhub.media.api.ReadMediaUrlResponse;
import com.app.communityhub.media.cleanup.MediaCleanupService;
import com.app.communityhub.media.storage.InMemoryObjectStorageClient;
import com.app.communityhub.support.IntegrationTestSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MediaAssetRepository mediaAssetRepository;

    @Autowired
    private InMemoryObjectStorageClient objectStorageClient;

    @Autowired
    private MediaCleanupService mediaCleanupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void avatarAndReadUrlFlowWorksAcrossAvatarPostAndComment() throws Exception {
        AuthResponse authResponse = registerAndLogin(mockMvc, objectMapper, "carol", "password123");
        String bearerToken = bearer(authResponse);

        String avatarMediaKey = reserveAndCompleteImage(
                mockMvc,
                objectMapper,
                mediaAssetRepository,
                objectStorageClient,
                bearerToken,
                "avatar.png"
        );
        mockMvc.perform(post("/api/profile/avatar")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mediaKey":"%s"}
                                """.formatted(avatarMediaKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar.mediaKey").value(avatarMediaKey));

        String postMediaKey = reserveAndCompleteImage(
                mockMvc,
                objectMapper,
                mediaAssetRepository,
                objectStorageClient,
                bearerToken,
                "post.png"
        );
        MvcResult postResult = mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Hello from test","mediaKeys":["%s"]}
                                """.formatted(postMediaKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments[0].mediaKey").value(postMediaKey))
                .andReturn();
        String postId = objectMapper.readTree(postResult.getResponse().getContentAsString()).get("id").asText();

        String commentMediaKey = reserveAndCompleteImage(
                mockMvc,
                objectMapper,
                mediaAssetRepository,
                objectStorageClient,
                bearerToken,
                "comment.png"
        );
        mockMvc.perform(post("/api/comments")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"postId":"%s","content":"Root comment","mediaKeys":["%s"]}
                                """.formatted(postId, commentMediaKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments[0].mediaKey").value(commentMediaKey));

        MvcResult readUrlsResult = mockMvc.perform(post("/api/media/read-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mediaKeys":["%s","%s","%s"]}
                                """.formatted(avatarMediaKey, postMediaKey, commentMediaKey)))
                .andExpect(status().isOk())
                .andReturn();

        List<ReadMediaUrlResponse> readUrls = objectMapper.readValue(
                readUrlsResult.getResponse().getContentAsString(),
                new TypeReference<>() {
                }
        );
        assertThat(readUrls).hasSize(3);
        assertThat(readUrls).allSatisfy(response -> assertThat(response.readUrl()).contains("https://example.test/read/"));
    }

    @Test
    void orphanCleanupDeletesOnlyOldUnattachedMedia() throws Exception {
        AuthResponse authResponse = registerAndLogin(mockMvc, objectMapper, "erin", "password123");
        String bearerToken = bearer(authResponse);

        String attachedMediaKey = reserveAndCompleteImage(
                mockMvc,
                objectMapper,
                mediaAssetRepository,
                objectStorageClient,
                bearerToken,
                "avatar.png"
        );
        mockMvc.perform(post("/api/profile/avatar")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mediaKey":"%s"}
                                """.formatted(attachedMediaKey)))
                .andExpect(status().isOk());

        String orphanMediaKey = reserveAndCompleteImage(
                mockMvc,
                objectMapper,
                mediaAssetRepository,
                objectStorageClient,
                bearerToken,
                "orphan.png"
        );

        MediaAssetEntity attachedMedia = mediaAssetRepository.findByMediaKey(attachedMediaKey).orElseThrow();
        MediaAssetEntity orphanMedia = mediaAssetRepository.findByMediaKey(orphanMediaKey).orElseThrow();
        Timestamp staleTimestamp = Timestamp.from(Instant.now().minusSeconds(60 * 60 * 48));

        jdbcTemplate.update("update media_assets set updated_at = ? where id = ?", staleTimestamp, attachedMedia.getId());
        jdbcTemplate.update("update media_assets set updated_at = ? where id = ?", staleTimestamp, orphanMedia.getId());
        entityManager.clear();

        mediaCleanupService.cleanupOrphanedMedia();
        entityManager.clear();

        MediaAssetEntity attachedAfterCleanup = mediaAssetRepository.findByMediaKey(attachedMediaKey).orElseThrow();
        MediaAssetEntity orphanAfterCleanup = mediaAssetRepository.findByMediaKey(orphanMediaKey).orElseThrow();

        assertThat(attachedAfterCleanup.getStatus()).isEqualTo(MediaStatus.ATTACHED);
        assertThat(attachedAfterCleanup.getOrphanedAt()).isNull();
        assertThat(objectStorageClient.getObjectInfo(attachedAfterCleanup.getObjectKey()).contentType()).isEqualTo("image/png");

        assertThat(orphanAfterCleanup.getStatus()).isEqualTo(MediaStatus.ORPHANED);
        assertThat(orphanAfterCleanup.getOrphanedAt()).isNotNull();
        assertThatThrownBy(() -> objectStorageClient.getObjectInfo(orphanAfterCleanup.getObjectKey()))
                .isInstanceOf(IllegalStateException.class);
    }
}
