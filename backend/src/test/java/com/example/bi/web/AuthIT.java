package com.example.bi.web;

import com.example.bi.TestcontainersConfiguration;
import com.example.bi.dto.RegisterRequest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class AuthIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Registration succeeds and returns 201 Created with user info")
    void register_success() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Jane Analyst",
                "jane.analyst@example.com",
                "StrongPassword123!"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.displayName", is("Jane Analyst")))
                .andExpect(jsonPath("$.email", is("jane.analyst@example.com")))
                .andExpect(jsonPath("$.role", is("ANALYST")));
    }

    @Test
    @DisplayName("Registration with duplicate email returns 409 EMAIL_ALREADY_REGISTERED")
    void register_duplicateEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Dev User Duplicate",
                "dev@example.com", // Seeded in R__dev_fixture
                "AnotherStrongPassword123!"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.code", is("EMAIL_ALREADY_REGISTERED")))
                .andExpect(jsonPath("$.detail", containsString("already exists")));
    }

    @Test
    @DisplayName("Registration with short password returns 400 with field errors")
    void register_shortPassword_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Short Pass User",
                "short.pass@example.com",
                "short" // less than 10 bytes
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.errors[0].field", is("password")))
                .andExpect(jsonPath("$.errors[0].code", is("INVALID_PASSWORD_LENGTH")));
    }

    @Test
    @DisplayName("Registration with password equal to email returns 400")
    void register_passwordEqualsEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Matching Pass User",
                "matching.pass@example.com",
                "matching.pass@example.com"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors[0].field", is("password")))
                .andExpect(jsonPath("$.errors[0].code", is("PASSWORD_EQUALS_EMAIL")));
    }

    @Test
    @DisplayName("Login with valid credentials returns 200 and session, me returns current user, logout invalidates")
    void login_me_logout_flow() throws Exception {
        // 1. Register a fresh user
        String email = "flow.user@example.com";
        String password = "CorrectPassword123!";
        RegisterRequest regReq = new RegisterRequest("Flow User", email, password);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated());

        // 2. Login
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", is("Flow User")))
                .andExpect(jsonPath("$.email", is(email)))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotNull(session, "Session must be created on login");

        // 3. GET /api/auth/me with session
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", is("Flow User")))
                .andExpect(jsonPath("$.email", is(email)));

        // 4. Logout with session and CSRF
        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // 5. Subsequent /api/auth/me with the invalidated session returns 401
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login with invalid credentials returns 401 generic error")
    void login_invalidCredentials_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .param("email", "nonexistent@example.com")
                        .param("password", "WrongPassword123!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.code", is("INVALID_CREDENTIALS")))
                .andExpect(jsonPath("$.detail", is("Invalid email or password")));
    }

    @Test
    @DisplayName("Throttles after 5 failed login attempts with 429 and Retry-After header")
    void login_throttling_returns429() throws Exception {
        String testEmail = "throttle.target@example.com";

        // Perform 4 failed attempts
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .param("email", testEmail)
                            .param("password", "badpass" + i))
                    .andExpect(status().isUnauthorized());
        }

        // 5th attempt fails and triggers throttle
        mockMvc.perform(post("/api/auth/login")
                        .param("email", testEmail)
                        .param("password", "badpass5"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code", is("LOGIN_THROTTLED")));

        // 6th attempt immediately rejected by throttle filter before authentication
        mockMvc.perform(post("/api/auth/login")
                        .param("email", testEmail)
                        .param("password", "anypass"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code", is("LOGIN_THROTTLED")));
    }

    @Test
    @DisplayName("GET /api/auth/csrf returns token and sets cookie")
    void csrf_returnsTokenAndCookie() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.headerName", anyOf(is("X-XSRF-TOKEN"), is("X-CSRF-TOKEN"))))
                .andExpect(jsonPath("$.parameterName", is("_csrf")));
    }
}
