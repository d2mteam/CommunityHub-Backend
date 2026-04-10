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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private void clearContentRows() {
        jdbcTemplate.update("delete from comments");
        jdbcTemplate.update("delete from posts");
        entityManager.clear();
    }
}
