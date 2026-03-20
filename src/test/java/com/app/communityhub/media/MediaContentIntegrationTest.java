package com.app.communityhub.media;

import com.app.communityhub.auth.dto.AuthResponse;
import com.app.communityhub.media.dto.ReadMediaUrlResponse;
import com.app.communityhub.support.IntegrationTestSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaContentIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MediaAssetRepository mediaAssetRepository;

    @Autowired
    private InMemoryObjectStorageClient objectStorageClient;

    @Autowired
    private MediaService mediaService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void avatarPostCommentAndReadUrlFlowWorks() throws Exception {
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

        JsonNode postJson = objectMapper.readTree(postResult.getResponse().getContentAsString());
        String postId = postJson.get("id").asText();

        String commentMediaKey = reserveAndCompleteImage(
                mockMvc,
                objectMapper,
                mediaAssetRepository,
                objectStorageClient,
                bearerToken,
                "comment.png"
        );
        MvcResult commentResult = mockMvc.perform(post("/api/comments")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"postId":"%s","content":"Root comment","mediaKeys":["%s"]}
                                """.formatted(postId, commentMediaKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments[0].mediaKey").value(commentMediaKey))
                .andReturn();

        String parentCommentId = objectMapper.readTree(commentResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/comments")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"postId":"%s","parentId":"%s","content":"Reply comment","mediaKeys":[]}
                                """.formatted(postId, parentCommentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(parentCommentId))
                .andExpect(jsonPath("$.depth").value(1));

        mockMvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].replyCount").value(1))
                .andExpect(jsonPath("$.hasMore").value(false));

        mockMvc.perform(get("/api/posts/" + postId + "/comments").queryParam("parentId", parentCommentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].depth").value(1))
                .andExpect(jsonPath("$.items[0].parentId").value(parentCommentId));

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
    void creatingPostWithUnuploadedMediaFails() throws Exception {
        AuthResponse authResponse = registerAndLogin(mockMvc, objectMapper, "dave", "password123");
        String bearerToken = bearer(authResponse);

        MvcResult reserveResult = mockMvc.perform(post("/api/media/reservations")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":"draft.png","mimeType":"image/png","sizeBytes":68}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String mediaKey = objectMapper.readTree(reserveResult.getResponse().getContentAsString()).get("mediaKey").asText();

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"content":"Should fail","mediaKeys":["%s"]}
                                """.formatted(mediaKey)))
                .andExpect(status().isConflict());
    }

    @Test
    void postsAndCommentsAllowUpToEightImagesButRejectMore() throws Exception {
        AuthResponse authResponse = registerAndLogin(mockMvc, objectMapper, "frank", "password123");
        String bearerToken = bearer(authResponse);

        List<String> eightPostMediaKeys = reserveAndCompleteImages(
                mockMvc,
                objectMapper,
                mediaAssetRepository,
                objectStorageClient,
                bearerToken,
                "post-eight",
                8
        );

        MvcResult postResult = mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Eight image post","mediaKeys":%s}
                                """.formatted(objectMapper.writeValueAsString(eightPostMediaKeys))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments.length()").value(8))
                .andReturn();

        String postId = objectMapper.readTree(postResult.getResponse().getContentAsString()).get("id").asText();

        List<String> ninePostMediaKeys = reserveAndCompleteImages(
                mockMvc,
                objectMapper,
                mediaAssetRepository,
                objectStorageClient,
                bearerToken,
                "post-nine",
                9
        );

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Nine image post","mediaKeys":%s}
                                """.formatted(objectMapper.writeValueAsString(ninePostMediaKeys))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A maximum of 8 images is allowed"));

        List<String> eightCommentMediaKeys = reserveAndCompleteImages(
                mockMvc,
                objectMapper,
                mediaAssetRepository,
                objectStorageClient,
                bearerToken,
                "comment-eight",
                8
        );

        mockMvc.perform(post("/api/comments")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"postId":"%s","content":"Eight image comment","mediaKeys":%s}
                                """.formatted(postId, objectMapper.writeValueAsString(eightCommentMediaKeys))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments.length()").value(8));

        List<String> nineCommentMediaKeys = reserveAndCompleteImages(
                mockMvc,
                objectMapper,
                mediaAssetRepository,
                objectStorageClient,
                bearerToken,
                "comment-nine",
                9
        );

        mockMvc.perform(post("/api/comments")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"postId":"%s","content":"Nine image comment","mediaKeys":%s}
                                """.formatted(postId, objectMapper.writeValueAsString(nineCommentMediaKeys))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A maximum of 8 images is allowed"));
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

        mediaService.cleanupOrphanedMedia();
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
