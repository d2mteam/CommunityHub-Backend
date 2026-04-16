package com.app.communityhub.auth;

import com.app.communityhub.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuthFeatureFlagIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void oauthStartRejectsDisabledGoogleProvider() throws Exception {
        mockMvc.perform(get("/api/auth/oauth/google/start"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("OAuth provider is disabled"));
    }

    @Test
    void oauthStartRejectsDisabledFacebookProvider() throws Exception {
        mockMvc.perform(get("/api/auth/oauth/facebook/start"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("OAuth provider is disabled"));
    }
}
