package com.app.communityhub.auth;

import com.app.communityhub.auth.api.AuthResponse;
import com.app.communityhub.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordAuthIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void registerAndMeFlowWorks() throws Exception {
        AuthResponse authResponse = registerAndLogin(mockMvc, objectMapper, "alice", "password123");

        assertThat(columnExists("users", "password_hash")).isFalse();
        assertThat(passwordCredentialCountForUsername("alice")).isEqualTo(1L);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(authResponse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.avatar").doesNotExist());
    }

    @Test
    void passwordLoginUsesPasswordCredentialRows() throws Exception {
        registerAndLogin(mockMvc, objectMapper, "charlie", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"charlie","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.user.username").value("charlie"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"charlie","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void refreshAndLogoutFlowWorks() throws Exception {
        AuthResponse authResponse = registerAndLogin(mockMvc, objectMapper, "bob", "password123");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(authResponse.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString());

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(authResponse.refreshToken())))
                .andExpect(status().isNoContent());
    }

    @Test
    void authCredentialSchemaSupportsPasswordAndOauthAccountStorage() {
        assertThat(columnExists("users", "password_hash")).isFalse();
        assertThat(columnType("password_credentials", "user_id")).isEqualTo("uuid");
        assertThat(columnType("password_credentials", "password_hash")).isEqualTo("character varying");
        assertThat(foreignKeyExists("password_credentials", "users")).isTrue();
        assertThat(foreignKeyExists("oauth_accounts", "users")).isTrue();
        assertThat(uniqueConstraintExists("oauth_accounts", "uk_oauth_accounts_provider_subject")).isTrue();
    }

    private long passwordCredentialCountForUsername(String username) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from password_credentials pc
                        join users u on u.id = pc.user_id
                        where lower(u.username) = lower(?)
                            and pc.password_hash is not null
                        """,
                Long.class,
                username
        );
        return count == null ? 0L : count;
    }

    private boolean columnExists(String tableName, String columnName) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.columns
                        where table_schema = 'public'
                            and table_name = ?
                            and column_name = ?
                        """,
                Long.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
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

    private boolean foreignKeyExists(String sourceTable, String targetTable) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.table_constraints tc
                        join information_schema.key_column_usage kcu
                            on tc.constraint_name = kcu.constraint_name
                            and tc.table_schema = kcu.table_schema
                        join information_schema.constraint_column_usage ccu
                            on ccu.constraint_name = tc.constraint_name
                            and ccu.table_schema = tc.table_schema
                        where tc.table_schema = 'public'
                            and tc.constraint_type = 'FOREIGN KEY'
                            and tc.table_name = ?
                            and ccu.table_name = ?
                        """,
                Long.class,
                sourceTable,
                targetTable
        );
        return count != null && count > 0;
    }

    private boolean uniqueConstraintExists(String tableName, String constraintName) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.table_constraints
                        where table_schema = 'public'
                            and table_name = ?
                            and constraint_name = ?
                            and constraint_type = 'UNIQUE'
                        """,
                Long.class,
                tableName,
                constraintName
        );
        return count != null && count > 0;
    }
}
