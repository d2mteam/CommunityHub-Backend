package com.app.communityhub.support;

import com.app.communityhub.auth.dto.AuthResponse;
import com.app.communityhub.media.InMemoryObjectStorageClient;
import com.app.communityhub.media.MediaAssetEntity;
import com.app.communityhub.media.MediaAssetRepository;
import com.app.communityhub.media.dto.CompleteMediaResponse;
import com.app.communityhub.media.dto.CreateMediaReservationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class IntegrationTestSupport {

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

    protected String reserveAndCompleteImage(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            MediaAssetRepository mediaAssetRepository,
            InMemoryObjectStorageClient objectStorageClient,
            String bearerToken,
            String fileName
    ) throws Exception {
        byte[] png = TestImageFactory.onePixelPng();
        MvcResult reserveResult = mockMvc.perform(post("/api/media/reservations")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReservationBody(fileName, "image/png", png.length))))
                .andExpect(status().isCreated())
                .andReturn();

        CreateMediaReservationResponse reservation = objectMapper.readValue(
                reserveResult.getResponse().getContentAsString(),
                CreateMediaReservationResponse.class
        );
        MediaAssetEntity mediaAsset = mediaAssetRepository.findByMediaKey(reservation.mediaKey()).orElseThrow();
        objectStorageClient.putObject(mediaAsset.getObjectKey(), "image/png", png);

        MvcResult completeResult = mockMvc.perform(post("/api/media/" + reservation.mediaKey() + "/complete")
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andReturn();
        objectMapper.readValue(completeResult.getResponse().getContentAsString(), CompleteMediaResponse.class);
        return reservation.mediaKey();
    }

    protected List<String> reserveAndCompleteImages(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            MediaAssetRepository mediaAssetRepository,
            InMemoryObjectStorageClient objectStorageClient,
            String bearerToken,
            String fileNamePrefix,
            int count
    ) throws Exception {
        List<String> mediaKeys = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            mediaKeys.add(reserveAndCompleteImage(
                    mockMvc,
                    objectMapper,
                    mediaAssetRepository,
                    objectStorageClient,
                    bearerToken,
                    "%s-%d.png".formatted(fileNamePrefix, index + 1)
            ));
        }
        return mediaKeys;
    }

    protected record Credentials(String username, String password) {
    }

    protected record ReservationBody(String fileName, String mimeType, long sizeBytes) {
    }
}
