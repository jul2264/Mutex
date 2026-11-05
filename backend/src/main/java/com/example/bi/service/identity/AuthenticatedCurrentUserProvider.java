package com.example.bi.service.identity;

import com.example.bi.domain.User;
import com.example.bi.repository.UserRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
@Primary
public class AuthenticatedCurrentUserProvider implements CurrentUserProvider {

    private final UserRepository userRepository;

    public AuthenticatedCurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getId();
        }

        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return userRepository.findByEmailNormalized(userDetails.getUsername().trim().toLowerCase(Locale.ROOT))
                    .map(User::getId)
                    .orElse(null);
        }

        if (principal instanceof String email && !email.isBlank() && !"anonymousUser".equals(email)) {
            return userRepository.findByEmailNormalized(email.trim().toLowerCase(Locale.ROOT))
                    .map(User::getId)
                    .orElse(null);
        }

        return null;
    }
}
