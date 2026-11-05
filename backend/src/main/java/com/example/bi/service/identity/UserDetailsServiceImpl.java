package com.example.bi.service.identity;

import com.example.bi.domain.User;
import com.example.bi.repository.UserRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Invalid email or password");
        }
        String normalizedEmail = username.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailNormalized(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));

        if (!user.isEnabled()) {
            throw new DisabledException("User account is disabled");
        }

        return new SecurityUser(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPasswordHash(),
                user.isEnabled(),
                user.getRole()
        );
    }
}

