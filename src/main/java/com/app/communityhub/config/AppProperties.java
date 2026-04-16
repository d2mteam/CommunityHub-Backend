package com.app.communityhub.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Valid
    @NotNull
    private OAuth oauth = new OAuth();

    @NotNull
    private Media media = new Media();

    @Valid
    @NotNull
    private Content content = new Content();

    @NotNull
    private Ids ids = new Ids();

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
    public static class OAuth {
        @NotBlank
        private String frontendCallbackUri = "http://localhost:5173/auth/oauth/callback";

        @NotNull
        private Duration stateTtl = Duration.ofMinutes(5);

        @NotNull
        private Duration ticketTtl = Duration.ofMinutes(2);

        @Valid
        @NotEmpty
        private Map<String, Provider> providers = defaultProviders();

        private static Map<String, Provider> defaultProviders() {
            Map<String, Provider> defaults = new LinkedHashMap<>();
            defaults.put("google", new Provider(
                    false,
                    "http://localhost:9201",
                    "communityhub-web",
                    "dev-secret",
                    "http://localhost:8080/api/auth/oauth/google/callback"
            ));
            defaults.put("facebook", new Provider(
                    false,
                    "http://localhost:9202",
                    "communityhub-web",
                    "dev-secret",
                    "http://localhost:8080/api/auth/oauth/facebook/callback"
            ));
            return defaults;
        }

        @Getter
        @Setter
        public static class Provider {
            private boolean enabled;

            @NotBlank
            private String issuerUri;

            @NotBlank
            private String clientId;

            @NotBlank
            private String clientSecret;

            @NotBlank
            private String redirectUri;

            public Provider() {
            }

            public Provider(boolean enabled, String issuerUri, String clientId, String clientSecret, String redirectUri) {
                this.enabled = enabled;
                this.issuerUri = issuerUri;
                this.clientId = clientId;
                this.clientSecret = clientSecret;
                this.redirectUri = redirectUri;
            }
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

    @Getter
    @Setter
    public static class Content {
        @Valid
        @NotNull
        private Posts posts = new Posts();

        @Valid
        @NotNull
        private Comments comments = new Comments();

        @Getter
        @Setter
        public static class Posts {
            @Min(1)
            private int maxAttachments = 8;

            @Min(1)
            private int defaultPageSize = 10;

            @Min(1)
            private int maxPageSize = 30;
        }

        @Getter
        @Setter
        public static class Comments {
            @Min(1)
            private int maxAttachments = 8;

            @Min(1)
            private int defaultPageSize = 10;

            @Min(1)
            private int maxPageSize = 30;
        }
    }

    @Getter
    @Setter
    public static class Ids {
        @Min(0)
        @Max(1023)
        private long workerId = 1;
    }
}
