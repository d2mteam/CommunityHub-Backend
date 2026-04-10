package com.app.communityhub.content;

import com.app.communityhub.auth.api.AuthResponse;
import com.app.communityhub.media.MediaAssetRepository;
import com.app.communityhub.media.storage.InMemoryObjectStorageClient;
import com.app.communityhub.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommentIntegrationTest extends IntegrationTestSupport {

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
    void threadedCommentsStoreJsonbAttachmentsAndReplyCounts() throws Exception {
        AuthResponse authResponse = registerAndLogin(mockMvc, objectMapper, "commenter", "password123");
        String bearerToken = bearer(authResponse);

        String postId = createTextOnlyPost(bearerToken, "Comment target");
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
        JsonNode storedCommentAttachments = readAttachmentsJson("comments", Long.parseLong(parentCommentId));
        assertThat(storedCommentAttachments.isArray()).isTrue();
        assertThat(storedCommentAttachments.size()).isEqualTo(1);
        assertThat(storedCommentAttachments.get(0).get("mediaKey").asText()).isEqualTo(commentMediaKey);
        assertThat(tableExists("comment_attachments")).isFalse();

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
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.sort").value("newest"));
    }

    @Test
    void commentsAllowUpToEightImagesButRejectMore() throws Exception {
        AuthResponse authResponse = registerAndLogin(mockMvc, objectMapper, "commentlimit", "password123");
        String bearerToken = bearer(authResponse);
        String postId = createTextOnlyPost(bearerToken, "Comment image limits");

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
    void commentsUseCursorPaginationForRootSortsWhileRepliesStayOldestFirst() throws Exception {
        clearContentRows();
        AuthResponse authResponse = registerAndLogin(mockMvc, objectMapper, "helen", "password123");
        String bearerToken = bearer(authResponse);

        String postId = createTextOnlyPost(bearerToken, "Cursor comments post");
        String rootA = createTextOnlyComment(bearerToken, postId, null, "Root A");
        String rootB = createTextOnlyComment(bearerToken, postId, null, "Root B");
        String rootC = createTextOnlyComment(bearerToken, postId, null, "Root C");
        String replyA1 = createTextOnlyComment(bearerToken, postId, rootA, "Reply A1");
        String replyA2 = createTextOnlyComment(bearerToken, postId, rootA, "Reply A2");
        String replyA3 = createTextOnlyComment(bearerToken, postId, rootA, "Reply A3");
        assertThat(List.of(rootA, rootB, rootC, replyA1, replyA2, replyA3))
                .allMatch(id -> id.matches("\\d+"));

        MvcResult newestRootPage = mockMvc.perform(get("/api/posts/" + postId + "/comments")
                        .queryParam("sort", "newest")
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].content").value("Root C"))
                .andExpect(jsonPath("$.items[1].content").value("Root B"))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andExpect(jsonPath("$.sort").value("newest"))
                .andReturn();

        String newestRootCursor = objectMapper.readTree(newestRootPage.getResponse().getContentAsString()).get("nextCursor").asText();

        mockMvc.perform(get("/api/posts/" + postId + "/comments")
                        .queryParam("sort", "newest")
                        .queryParam("cursor", newestRootCursor)
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].content").value("Root A"))
                .andExpect(jsonPath("$.items[0].replyCount").value(3))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.sort").value("newest"));

        mockMvc.perform(get("/api/posts/" + postId + "/comments")
                        .queryParam("sort", "oldest")
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].content").value("Root A"))
                .andExpect(jsonPath("$.items[1].content").value("Root B"))
                .andExpect(jsonPath("$.sort").value("oldest"));

        MvcResult replyPage = mockMvc.perform(get("/api/posts/" + postId + "/comments")
                        .queryParam("parentId", rootA)
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].content").value("Reply A1"))
                .andExpect(jsonPath("$.items[1].content").value("Reply A2"))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andExpect(jsonPath("$.sort").value("oldest"))
                .andReturn();

        String replyCursor = objectMapper.readTree(replyPage.getResponse().getContentAsString()).get("nextCursor").asText();

        mockMvc.perform(get("/api/posts/" + postId + "/comments")
                        .queryParam("parentId", rootA)
                        .queryParam("cursor", replyCursor)
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].content").value("Reply A3"))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.sort").value("oldest"));
    }

    private JsonNode readAttachmentsJson(String tableName, long ownerId) throws Exception {
        String json = jdbcTemplate.queryForObject(
                "select attachments_jsonb::text from " + tableName + " where id = ?",
                String.class,
                ownerId
        );
        return objectMapper.readTree(json);
    }

    private boolean tableExists(String tableName) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.tables
                        where table_schema = 'public' and table_name = ?
                        """,
                Long.class,
                tableName
        );
        return count != null && count > 0;
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

    private void clearContentRows() {
        jdbcTemplate.update("delete from comments");
        jdbcTemplate.update("delete from posts");
        entityManager.clear();
    }
}
