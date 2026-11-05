package com.example.bi.web;

import com.example.bi.TestcontainersConfiguration;
import com.example.bi.domain.Role;
import com.example.bi.domain.User;
import com.example.bi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class AuthorizationMatrixIT {

    private static final UUID USER_A_DATASET_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setupUserB() {
        if (!userRepository.existsByEmailNormalized("user.b@example.com")) {
            User userB = new User(
                    null,
                    "user.b@example.com",
                    "user.b@example.com",
                    "User B Analyst",
                    passwordEncoder.encode("PasswordB123!"),
                    Role.ANALYST
            );
            userRepository.save(userB);
        }
    }

    @Test
    @DisplayName("Anonymous access to dataset summary returns 401 Unauthorized ProblemDetail")
    void anonymousAccess_returns401() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", USER_A_DATASET_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("User B accessing User A's dataset returns 404 DATASET_NOT_FOUND, never 403")
    void foreignDatasetAccess_returns404Not403() throws Exception {
        mockMvc.perform(get("/api/datasets/{datasetId}/analytics/summary", USER_A_DATASET_ID)
                        .with(user("user.b@example.com").roles("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.code", is("DATASET_NOT_FOUND")));
    }

    @Test
    @DisplayName("Unsafe POST request without CSRF token returns 403 Forbidden")
    void unsafeRequestWithoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .with(user("user.b@example.com").roles("ANALYST")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("Unsafe POST request with valid CSRF token succeeds")
    void unsafeRequestWithCsrf_succeeds() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .with(user("user.b@example.com").roles("ANALYST"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }
}
