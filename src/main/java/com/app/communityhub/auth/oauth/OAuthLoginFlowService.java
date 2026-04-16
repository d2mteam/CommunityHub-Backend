package com.app.communityhub.auth.oauth;

import com.app.communityhub.common.AppException;
import com.app.communityhub.config.AppProperties;
import com.app.communityhub.user.UserEntity;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class OAuthLoginFlowService {

    private final AppProperties appProperties;
    private final OAuthLoginStateStore oauthLoginStateStore;
    private final OAuthLoginTicketService oauthLoginTicketService;
    private final OAuthAccountProvisioningService oauthAccountProvisioningService;
    private final OidcClient oidcClient;
    private final OAuthTokenSupport tokenSupport;

    @Transactional
    public URI start(String providerName, String returnTo) {
        ProviderSelection provider = provider(providerName);
        String rawState = tokenSupport.randomToken();
        String codeVerifier = tokenSupport.randomToken();
        String nonce = tokenSupport.randomToken();
        oauthLoginStateStore.save(
                rawState,
                provider.name(),
                codeVerifier,
                nonce,
                sanitizeReturnTo(returnTo),
                provider.registration().getRedirectUri(),
                appProperties.getOauth().getStateTtl()
        );

        return UriComponentsBuilder.fromUriString(oidcClient.authorizationEndpoint(provider.name(), provider.registration()))
                .queryParam("response_type", "code")
                .queryParam("client_id", provider.registration().getClientId())
                .queryParam("redirect_uri", provider.registration().getRedirectUri())
                .queryParam("scope", "openid profile email")
                .queryParam("state", rawState)
                .queryParam("nonce", nonce)
                .queryParam("code_challenge", tokenSupport.codeChallenge(codeVerifier))
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUri();
    }

    @Transactional
    public URI complete(String providerName, String code, String stateToken) {
        if (code == null || code.isBlank() || stateToken == null || stateToken.isBlank()) {
            return frontendErrorRedirect("missing_oauth_code", "/");
        }

        ProviderSelection provider = provider(providerName);
        OAuthLoginStateStore.StoredState state = oauthLoginStateStore.consume(provider.name(), stateToken);
        OidcTokenResponse tokenResponse = oidcClient.exchangeAuthorizationCode(
                provider.name(),
                provider.registration(),
                code,
                state.codeVerifier()
        );
        OidcIdentity identity = oidcClient.loadIdentity(
                provider.name(),
                provider.registration(),
                tokenResponse,
                state.nonce()
        );
        UserEntity user = oauthAccountProvisioningService.findOrCreateUser(provider.name(), identity);
        String rawTicket = oauthLoginTicketService.createTicket(user, state.returnTo());
        return frontendSuccessRedirect(rawTicket, state.returnTo());
    }

    public URI frontendErrorRedirect(String error, String returnTo) {
        return UriComponentsBuilder.fromUriString(appProperties.getOauth().getFrontendCallbackUri())
                .queryParam("error", error)
                .queryParam("returnTo", sanitizeReturnTo(returnTo))
                .build()
                .encode()
                .toUri();
    }

    private URI frontendSuccessRedirect(String ticket, String returnTo) {
        return UriComponentsBuilder.fromUriString(appProperties.getOauth().getFrontendCallbackUri())
                .queryParam("ticket", ticket)
                .queryParam("returnTo", returnTo)
                .build()
                .encode()
                .toUri();
    }

    private ProviderSelection provider(String providerName) {
        String normalized = providerName == null ? "" : providerName.trim().toLowerCase(Locale.ROOT);
        Map<String, AppProperties.OAuth.Provider> providers = appProperties.getOauth().getProviders();
        AppProperties.OAuth.Provider provider = providers.get(normalized);
        if (provider == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "OAuth provider not found");
        }
        if (!provider.isEnabled()) {
            throw new AppException(HttpStatus.NOT_FOUND, "OAuth provider is disabled");
        }
        return new ProviderSelection(normalized, provider);
    }

    private String sanitizeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank() || !returnTo.startsWith("/") || returnTo.startsWith("//")) {
            return "/";
        }
        if (returnTo.length() > 250) {
            return "/";
        }
        return returnTo;
    }

    private record ProviderSelection(String name, AppProperties.OAuth.Provider registration) {
    }
}
