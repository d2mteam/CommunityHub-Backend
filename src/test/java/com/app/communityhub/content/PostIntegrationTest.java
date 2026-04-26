package com.app.communityhub.content;

import com.app.communityhub.auth.api.AuthResponse;
import com.app.communityhub.media.MediaAssetRepository;
import com.app.communityhub.media.storage.InMemoryObjectStorageClient;
import com.app.communityhub.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MediaAssetRepository mediaAssetRepository;

    @Autowired
    private InMemoryObjectStorageClient objectStorageClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void contentSchemaUsesSnowflakeBigintIdsAndJsonbAttachments() {
        assertThat(columnType("posts", "id")).isEqualTo("bigint");
        assertThat(columnType("comments", "id")).isEqualTo("bigint");
        assertThat(columnType("comments", "post_id")).isEqualTo("bigint");
        assertThat(columnType("comments", "parent_id")).isEqualTo("bigint");
        assertThat(columnType("comments", "root_id")).isEqualTo("bigint");
        assertThat(columnType("posts", "attachments_jsonb")).isEqualTo("jsonb");
        assertThat(columnType("comments", "attachments_jsonb")).isEqualTo("jsonb");
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
    void postsAllowUpToEightImagesButRejectMore() throws Exception {
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

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Eight image post","mediaKeys":%s}
                                """.formatted(objectMapper.writeValueAsString(eightPostMediaKeys))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments.length()").value(8));

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
    }

    @Test
    void postFeedUsesCursorPaginationForNewestAndOldestSorts() throws Exception {
        clearContentRows();
        AuthResponse authResponse = registerAndLogin(mockMvc, objectMapper, "george", "password123");
        String bearerToken = bearer(authResponse);

        createTextOnlyPost(bearerToken, "Oldest A");
        createTextOnlyPost(bearerToken, "Oldest B");
        createTextOnlyPost(bearerToken, "Oldest C");
        createTextOnlyPost(bearerToken, "Newest A");
        createTextOnlyPost(bearerToken, "Newest B");
        createTextOnlyPost(bearerToken, "Newest C");

        MvcResult newestPage = mockMvc.perform(get("/api/posts")
                        .queryParam("sort", "newest")
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].content").value("Newest C"))
                .andExpect(jsonPath("$.items[1].content").value("Newest B"))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andExpect(jsonPath("$.sort").value("newest"))
                .andReturn();

        String newestCursor = objectMapper.readTree(newestPage.getResponse().getContentAsString()).get("nextCursor").asText();

        mockMvc.perform(get("/api/posts")
                        .queryParam("sort", "newest")
                        .queryParam("cursor", newestCursor)
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].content").value("Newest A"))
                .andExpect(jsonPath("$.sort").value("newest"));

        mockMvc.perform(get("/api/posts")
                        .queryParam("sort", "oldest")
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].content").value("Oldest A"))
                .andExpect(jsonPath("$.items[1].content").value("Oldest B"))
                .andExpect(jsonPath("$.sort").value("oldest"));
    }

    @Test
    void authorCanUpdatePostAndCreateRevisionWhileOthersReceiveForbidden() throws Exception {
        AuthResponse authorAuth = registerAndLogin(mockMvc, objectMapper, "posteditauthor", "password123");
        AuthResponse otherAuth = registerAndLogin(mockMvc, objectMapper, "posteditother", "password123");
        String authorBearer = bearer(authorAuth);
        String otherBearer = bearer(otherAuth);

        String postId = createTextOnlyPost(authorBearer, "Original post");

        mockMvc.perform(patch("/api/posts/" + postId)
                        .header("Authorization", authorBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Updated post","mediaKeys":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Updated post"))
                .andExpect(jsonPath("$.isEdited").value(true))
                .andExpect(jsonPath("$.editedAt").isString());

        mockMvc.perform(patch("/api/posts/" + postId)
                        .header("Authorization", otherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Intrusion","mediaKeys":[]}
                                """))
                .andExpect(status().isForbidden());

        assertThat(revisionCount("post_revisions", Long.parseLong(postId))).isEqualTo(2);
        assertThat(latestRevisionEvent("post_revisions", Long.parseLong(postId))).isEqualTo("UPDATED");
        assertThat(latestRevisionSource("post_revisions", Long.parseLong(postId))).isEqualTo("AUTHOR");
    }

    @Test
    void postEditSupportsAttachmentReplacementAndRejectsEmptyFinalState() throws Exception {
        clearContentRows();
        AuthResponse authResponse = registerAndLogin(mockMvc, objectMapper, "posteditmedia", "password123");
        String bearerToken = bearer(authResponse);

        String firstMediaKey = reserveAndCompleteImage(
                mockMvc,
                objectMapper,
                mediaAssetRepository,
                objectStorageClient,
                bearerToken,
                "post-edit-1.png"
        );
        String secondMediaKey = reserveAndCompleteImage(
                mockMvc,
                objectMapper,
                mediaAssetRepository,
                objectStorageClient,
                bearerToken,
                "post-edit-2.png"
        );

        MvcResult createResult = mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Post with media","mediaKeys":["%s"]}
                                """.formatted(firstMediaKey)))
                .andExpect(status().isCreated())
                .andReturn();
        String postId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/api/posts/" + postId)
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Post with replacement media","mediaKeys":["%s"]}
                                """.formatted(secondMediaKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.attachments[0].mediaKey").value(secondMediaKey));

        mockMvc.perform(patch("/api/posts/" + postId)
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"   ","mediaKeys":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Post content or images are required"));
    }

    @Test
    void deletingPostSoftDeletesItHidesItFromReadsAndMarksChildrenDeleted() throws Exception {
        clearContentRows();
        AuthResponse authResponse = registerAndLogin(mockMvc, objectMapper, "postdelete", "password123");
        String bearerToken = bearer(authResponse);

        String postId = createTextOnlyPost(bearerToken, "Delete me");
        String replyPostId = createTextOnlyPost(bearerToken, "Keep me");
        createTextOnlyComment(bearerToken, postId, null, "Root on deleted post");
        createTextOnlyComment(bearerToken, postId, null, "Another root on deleted post");

        mockMvc.perform(delete("/api/posts/" + postId)
                        .header("Authorization", bearerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/posts")
                        .queryParam("sort", "newest")
                        .queryParam("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(replyPostId));

        mockMvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(status().isNotFound());

        assertThat(revisionCount("post_revisions", Long.parseLong(postId))).isEqualTo(2);
        assertThat(latestRevisionEvent("post_revisions", Long.parseLong(postId))).isEqualTo("DELETED");
        assertThat(latestRevisionSource("post_revisions", Long.parseLong(postId))).isEqualTo("AUTHOR");
        assertThat(jdbcTemplate.queryForObject("select count(*) from comments where post_id = ? and deleted_at is not null", Integer.class, Long.parseLong(postId)))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select deleted_source from posts where id = ?", String.class, Long.parseLong(postId)))
                .isEqualTo("AUTHOR");
    }

    @Test
    void postFeedSkipsSoftDeletedRowsAcrossCursorPages() throws Exception {
        clearContentRows();
        AuthResponse authResponse = registerAndLogin(mockMvc, objectMapper, "postcursordelete", "password123");
        String bearerToken = bearer(authResponse);

        String postA = createTextOnlyPost(bearerToken, "Cursor A");
        String postB = createTextOnlyPost(bearerToken, "Cursor B");
        String postC = createTextOnlyPost(bearerToken, "Cursor C");
        String postD = createTextOnlyPost(bearerToken, "Cursor D");

        mockMvc.perform(delete("/api/posts/" + postC)
                        .header("Authorization", bearerToken))
                .andExpect(status().isNoContent());

        MvcResult firstPage = mockMvc.perform(get("/api/posts")
                        .queryParam("sort", "newest")
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(postD))
                .andExpect(jsonPath("$.items[1].id").value(postB))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn();

        String cursor = objectMapper.readTree(firstPage.getResponse().getContentAsString()).get("nextCursor").asText();
        mockMvc.perform(get("/api/posts")
                        .queryParam("sort", "newest")
                        .queryParam("cursor", cursor)
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(postA))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    private String columnType(String tableName, String columnName) {
        return jdbcTemplate.queryForObject(
                """
                        select data_type
                        from information_schema.columns
                        where table_schema = 'public'
                            and table_name = ?
                            and column_name = ?
                        """,
                String.class,
                tableName,
                columnName
        );
    }

    private String createTextOnlyPost(String bearerToken, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s","mediaKeys":[]}
                                """.formatted(content)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        assertThat(id).matches("\\d+");
        return id;
    }

    private String createTextOnlyComment(String bearerToken, String postId, String parentId, String content) throws Exception {
        String parentJson = parentId == null ? "null" : "\"%s\"".formatted(parentId);
        MvcResult result = mockMvc.perform(post("/api/comments")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"postId":"%s","parentId":%s,"content":"%s","mediaKeys":[]}
                                """.formatted(postId, parentJson, content)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        assertThat(id).matches("\\d+");
        return id;
    }

    private int revisionCount(String tableName, long entityId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where entity_id = ?",
                Integer.class,
                entityId
        );
    }

    private String latestRevisionEvent(String tableName, long entityId) {
        return jdbcTemplate.queryForObject(
                "select event_type from " + tableName + " where entity_id = ? order by revision_number desc limit 1",
                String.class,
                entityId
        );
    }

    private String latestRevisionSource(String tableName, long entityId) {
        return jdbcTemplate.queryForObject(
                "select action_source from " + tableName + " where entity_id = ? order by revision_number desc limit 1",
                String.class,
                entityId
        );
    }

    private void clearContentRows() {
        jdbcTemplate.update("delete from comments");
        jdbcTemplate.update("delete from posts");
        entityManager.clear();
    }
}
