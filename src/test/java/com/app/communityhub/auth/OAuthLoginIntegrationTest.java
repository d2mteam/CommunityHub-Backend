package com.app.communityhub.auth;

import com.app.communityhub.auth.oauth.OAuthLoginStateStore;
import com.app.communityhub.auth.oauth.OAuthLoginTicketService;
import com.app.communityhub.auth.oauth.OAuthTokenSupport;
import com.app.communityhub.support.IntegrationTestSupport;
import com.app.communityhub.user.UserEntity;
import com.app.communityhub.user.UserRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.oauth.providers.google.enabled=true",
        "app.oauth.providers.facebook.enabled=true"
})
class OAuthLoginIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuthLoginTicketService oauthLoginTicketService;

    @Autowired
    private OAuthTokenSupport oauthTokenSupport;

    @Autowired
    private OAuthLoginStateStore oauthLoginStateStore;

    @Autowired
    private UserRepository userRepository;

    @Test
    void oauthTicketExchangeIssuesCommunityHubTokensOnce() throws Exception {
        UserEntity user = new UserEntity();
        user.setUsername("oauthuser");
        UserEntity savedUser = userRepository.saveAndFlush(user);

        String rawTicket = oauthLoginTicketService.createTicket(savedUser, "/");

        mockMvc.perform(post("/api/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticket":"%s"}
                                """.formatted(rawTicket)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.user.username").value("oauthuser"));

        mockMvc.perform(post("/api/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticket":"%s"}
                                """.formatted(rawTicket)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oauthStateStoreUsesRedisTtlAndOneTimeConsumeSemantics() {
        String rawState = oauthTokenSupport.randomToken();
        oauthLoginStateStore.save(
                rawState,
                "google",
                "verifier-123",
                "nonce-123",
                "/profile",
                "http://localhost:8080/api/auth/oauth/google/callback",
                Duration.ofMinutes(5)
        );

        assertThat(oauthLoginStateStore.exists(rawState)).isTrue();

        OAuthLoginStateStore.StoredState storedState = oauthLoginStateStore.consume("google", rawState);
        assertThat(storedState.provider()).isEqualTo("google");
        assertThat(storedState.returnTo()).isEqualTo("/profile");
        assertThat(oauthLoginStateStore.exists(rawState)).isFalse();
    }
}
