package com.app.communityhub.support;

import com.app.communityhub.auth.dto.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class IntegrationTestSupport {

    protected AuthResponse registerAndLogin(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            String username,
            String password
    ) throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Credentials(username, password))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(registerResult.getResponse().getContentAsString(), AuthResponse.class);
    }

    protected String bearer(AuthResponse authResponse) {
        return "Bearer " + authResponse.accessToken();
    }

    protected record Credentials(String username, String password) {
    }
}
