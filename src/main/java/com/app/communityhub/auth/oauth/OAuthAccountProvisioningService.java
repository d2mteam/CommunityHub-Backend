package com.app.communityhub.auth.oauth;

import com.app.communityhub.user.UserEntity;
import com.app.communityhub.user.UserRepository;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OAuthAccountProvisioningService {

    private static final Pattern USERNAME_CHARS = Pattern.compile("[^a-z0-9_]+");

    private final OAuthAccountRepository oauthAccountRepository;
    private final UserRepository userRepository;
    private final OAuthTokenSupport tokenSupport;

    @Transactional
    public UserEntity findOrCreateUser(String providerName, OidcIdentity identity) {
        return oauthAccountRepository.findByProviderAndProviderSubject(providerName, identity.subject())
                .map(account -> {
                    account.updateEmail(identity.email());
                    return account.getUser();
                })
                .orElseGet(() -> createUserWithOAuthAccount(providerName, identity));
    }

    private UserEntity createUserWithOAuthAccount(String providerName, OidcIdentity identity) {
        UserEntity user = new UserEntity();
        user.setUsername(nextAvailableUsername(identity, providerName));
        UserEntity savedUser = userRepository.save(user);

        OAuthAccountEntity account = OAuthAccountEntity.link(
                savedUser,
                providerName,
                identity.subject(),
                identity.email()
        );
        oauthAccountRepository.save(account);
        return savedUser;
    }

    private String nextAvailableUsername(OidcIdentity identity, String providerName) {
        String base = normalizeUsername(
                firstNonBlank(
                        identity.preferredUsername(),
                        firstNonBlank(emailLocalPart(identity.email()), identity.name())
                )
        );
        if (base == null) {
            base = providerName + "_user";
        }
        if (base.length() > 24) {
            base = base.substring(0, 24);
        }

        for (int index = 0; index < 100; index++) {
            String candidate = index == 0 ? base : base + "_" + (index + 1);
            if (!userRepository.existsByUsernameIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return base.substring(0, Math.min(base.length(), 18)) + "_" + tokenSupport.randomToken().substring(0, 8);
    }

    private String normalizeUsername(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = USERNAME_CHARS.matcher(value.toLowerCase(Locale.ROOT).trim()).replaceAll("_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        if (normalized.length() < 3) {
            return null;
        }
        return normalized;
    }

    private String emailLocalPart(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return null;
        }
        return email.substring(0, email.indexOf('@'));
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
