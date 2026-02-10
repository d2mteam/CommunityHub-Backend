package com.app.communityhub.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @NotNull
    private Cors cors = new Cors();

    @NotNull
    private Security security = new Security();

    @NotNull
    private Media media = new Media();

    @Getter
    @Setter
    public static class Cors {
        @NotEmpty
        private List<String> allowedOrigins = List.of("http://localhost:5173");
    }

    @Getter
    @Setter
    public static class Security {
        @NotNull
        private Jwt jwt = new Jwt();

        @Getter
        @Setter
        public static class Jwt {
            @NotBlank
            private String issuer;

            @NotBlank
            private String secret;

            @NotNull
            private Duration accessTokenTtl;

            @NotNull
            private Duration refreshTokenTtl;
        }
    }

    @Getter
    @Setter
    public static class Media {
        @NotBlank
        private String bucket;

        @NotBlank
        private String endpoint;

        @NotBlank
        private String accessKey;

        @NotBlank
        private String secretKey;

        @NotBlank
        private String region;

        private boolean pathStyleAccessEnabled = true;

        @NotNull
        private Duration uploadUrlTtl;

        @NotNull
        private Duration readUrlTtl;

        @NotNull
        private Duration reservationTtl;

        @NotNull
        private Duration orphanRetention;

        @Positive
        private long maxFileSizeBytes;

        @NotEmpty
        private List<String> allowedMimeTypes = List.of("image/jpeg", "image/png");
    }
}
