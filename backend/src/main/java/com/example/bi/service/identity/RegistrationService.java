package com.example.bi.service.identity;

import com.example.bi.domain.Role;
import com.example.bi.domain.User;
import com.example.bi.dto.FieldErrorDto;
import com.example.bi.dto.RegisterRequest;
import com.example.bi.dto.UserResponse;
import com.example.bi.exception.EmailAlreadyRegisteredException;
import com.example.bi.exception.ValidationException;
import com.example.bi.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        List<FieldErrorDto> errors = new ArrayList<>();

        String displayName = request.displayName() != null ? request.displayName().trim() : "";
        if (displayName.isEmpty() || displayName.length() > 80) {
            errors.add(new FieldErrorDto("displayName", "INVALID_DISPLAY_NAME",
                    "Display name must be between 1 and 80 characters"));
        }

        String rawEmail = request.email() != null ? request.email().trim() : "";
        if (rawEmail.isEmpty() || rawEmail.length() > 254 || !rawEmail.contains("@") || !rawEmail.contains(".")) {
            errors.add(new FieldErrorDto("email", "INVALID_EMAIL",
                    "A valid email address (max 254 characters) is required"));
        }

        String rawPassword = request.password() != null ? request.password() : "";
        byte[] passwordBytes = rawPassword.getBytes(StandardCharsets.UTF_8);
        if (passwordBytes.length < 10 || passwordBytes.length > 72) {
            errors.add(new FieldErrorDto("password", "INVALID_PASSWORD_LENGTH",
                    "Password must be between 10 and 72 bytes in UTF-8"));
        }

        if (!rawEmail.isEmpty() && rawPassword.equalsIgnoreCase(rawEmail)) {
            errors.add(new FieldErrorDto("password", "PASSWORD_EQUALS_EMAIL",
                    "Password cannot be the same as your email address"));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed for registration request", errors);
        }

        String emailNormalized = rawEmail.toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailNormalized(emailNormalized)) {
            throw new EmailAlreadyRegisteredException("An account with this email address already exists");
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        User user = new User(null, rawEmail, emailNormalized, displayName, passwordHash, Role.ANALYST);
        User saved = userRepository.save(user);

        return new UserResponse(saved.getId(), saved.getDisplayName(), saved.getEmail(), saved.getRole().name());
    }
}

