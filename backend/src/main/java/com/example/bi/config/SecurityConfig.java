package com.example.bi.config;

import com.example.bi.dto.UserResponse;
import com.example.bi.repository.UserRepository;
import com.example.bi.service.identity.LoginAttemptService;
import com.example.bi.service.identity.SecurityUser;
import com.example.bi.web.ApiExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final LoginAttemptService loginAttemptService;
    private final UserRepository userRepository;

    public SecurityConfig(ObjectMapper objectMapper, LoginAttemptService loginAttemptService, UserRepository userRepository) {
        this.objectMapper = objectMapper;
        this.loginAttemptService = loginAttemptService;
        this.userRepository = userRepository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        tokenRepository.setHeaderName("X-XSRF-TOKEN");
        SpaCsrfTokenRequestHandler csrfRequestHandler = new SpaCsrfTokenRequestHandler();

        SecurityContextRepository securityContextRepository = new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository()
        );

        http
            .securityContext(context -> context
                .securityContextRepository(securityContextRepository)
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(tokenRepository)
                .csrfTokenRequestHandler(csrfRequestHandler)
                .ignoringRequestMatchers("/api/auth/register", "/api/auth/login", "/actuator/**")
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/csrf", "/actuator/health").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll()
            )
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                    SecurityUser principal = (SecurityUser) authentication.getPrincipal();
                    String key = loginAttemptService.buildKey(principal.getUsername(), request);
                    loginAttemptService.loginSucceeded(key);

                    userRepository.findById(principal.getId()).ifPresent(u -> {
                        u.setLastLoginAt(Instant.now());
                        userRepository.save(u);
                    });

                    UserResponse userResponse = new UserResponse(
                            principal.getId(),
                            principal.getDisplayName(),
                            principal.getUsername(),
                            principal.getRole().name()
                    );

                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getOutputStream(), userResponse);
                })
                .failureHandler((request, response, exception) -> {
                    String email = request.getParameter("email");
                    String key = loginAttemptService.buildKey(email, request);
                    loginAttemptService.loginFailed(key);

                    String requestId = ApiExceptionHandler.resolveRequestId(request);
                    if (loginAttemptService.isBlocked(key)) {
                        long retryAfter = loginAttemptService.getRetryAfterSeconds(key);
                        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                        response.setHeader("Retry-After", String.valueOf(retryAfter));
                        response.setContentType("application/problem+json");

                        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                                HttpStatus.TOO_MANY_REQUESTS,
                                "Too many failed login attempts. Please try again in " + retryAfter + " seconds."
                        );
                        problem.setTitle("Too Many Requests");
                        problem.setType(URI.create("https://example.com/errors/LOGIN_THROTTLED"));
                        problem.setProperty("code", "LOGIN_THROTTLED");
                        problem.setProperty("requestId", requestId);
                        problem.setProperty("retryAfter", retryAfter);
                        objectMapper.writeValue(response.getOutputStream(), problem);
                    } else {
                        response.setStatus(HttpStatus.UNAUTHORIZED.value());
                        response.setContentType("application/problem+json");

                        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
                        problem.setTitle("Unauthorized");
                        problem.setType(URI.create("https://example.com/errors/INVALID_CREDENTIALS"));
                        problem.setProperty("code", "INVALID_CREDENTIALS");
                        problem.setProperty("requestId", requestId);
                        objectMapper.writeValue(response.getOutputStream(), problem);
                    }
                })
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                })
                .invalidateHttpSession(true)
                .clearAuthentication(true)
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authEx) -> {
                    String requestId = ApiExceptionHandler.resolveRequestId(request);
                    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource");
                    problem.setTitle("Unauthorized");
                    problem.setType(URI.create("https://example.com/errors/UNAUTHORIZED"));
                    problem.setProperty("code", "UNAUTHORIZED");
                    problem.setProperty("requestId", requestId);

                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/problem+json");
                    objectMapper.writeValue(response.getOutputStream(), problem);
                })
                .accessDeniedHandler((request, response, accessEx) -> {
                    String requestId = ApiExceptionHandler.resolveRequestId(request);
                    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access is denied");
                    problem.setTitle("Forbidden");
                    problem.setType(URI.create("https://example.com/errors/FORBIDDEN"));
                    problem.setProperty("code", "FORBIDDEN");
                    problem.setProperty("requestId", requestId);

                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/problem+json");
                    objectMapper.writeValue(response.getOutputStream(), problem);
                })
            )
            .addFilterBefore(new JsonLoginWrapperFilter(objectMapper), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new LoginThrottleFilter(loginAttemptService, objectMapper), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    public static class LoginThrottleFilter extends OncePerRequestFilter {
        private final LoginAttemptService loginAttemptService;
        private final ObjectMapper objectMapper;

        public LoginThrottleFilter(LoginAttemptService loginAttemptService, ObjectMapper objectMapper) {
            this.loginAttemptService = loginAttemptService;
            this.objectMapper = objectMapper;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            if ("/api/auth/login".equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod())) {
                String email = request.getParameter("email");
                if (email != null && !email.isBlank()) {
                    String key = loginAttemptService.buildKey(email, request);
                    if (loginAttemptService.isBlocked(key)) {
                        long retryAfter = loginAttemptService.getRetryAfterSeconds(key);
                        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                        response.setHeader("Retry-After", String.valueOf(retryAfter));
                        response.setContentType("application/problem+json");

                        String requestId = ApiExceptionHandler.resolveRequestId(request);
                        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                                HttpStatus.TOO_MANY_REQUESTS,
                                "Too many failed login attempts. Please try again in " + retryAfter + " seconds."
                        );
                        problem.setTitle("Too Many Requests");
                        problem.setType(URI.create("https://example.com/errors/LOGIN_THROTTLED"));
                        problem.setProperty("code", "LOGIN_THROTTLED");
                        problem.setProperty("requestId", requestId);
                        problem.setProperty("retryAfter", retryAfter);
                        objectMapper.writeValue(response.getOutputStream(), problem);
                        return;
                    }
                }
            }
            filterChain.doFilter(request, response);
        }
    }

    public static class JsonLoginWrapperFilter extends OncePerRequestFilter {
        private final ObjectMapper objectMapper;

        public JsonLoginWrapperFilter(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            if ("/api/auth/login".equals(request.getRequestURI()) &&
                "POST".equalsIgnoreCase(request.getMethod()) &&
                request.getContentType() != null &&
                request.getContentType().contains(MediaType.APPLICATION_JSON_VALUE)) {

                Map<String, String[]> parameterMap = new HashMap<>(request.getParameterMap());
                try (InputStream is = request.getInputStream()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = objectMapper.readValue(is, Map.class);
                    if (body != null) {
                        if (body.get("email") != null) {
                            parameterMap.put("email", new String[]{String.valueOf(body.get("email"))});
                        }
                        if (body.get("password") != null) {
                            parameterMap.put("password", new String[]{String.valueOf(body.get("password"))});
                        }
                    }
                } catch (Exception ignored) {}

                HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
                    @Override
                    public String getParameter(String name) {
                        String[] values = parameterMap.get(name);
                        return (values != null && values.length > 0) ? values[0] : null;
                    }

                    @Override
                    public Map<String, String[]> getParameterMap() {
                        return Collections.unmodifiableMap(parameterMap);
                    }

                    @Override
                    public Enumeration<String> getParameterNames() {
                        return Collections.enumeration(parameterMap.keySet());
                    }

                    @Override
                    public String[] getParameterValues(String name) {
                        return parameterMap.get(name);
                    }
                };

                filterChain.doFilter(wrapped, response);
                return;
            }

            filterChain.doFilter(request, response);
        }
    }

    public static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            this.xor.handle(request, response, csrfToken);
            csrfToken.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String headerValue = request.getHeader(csrfToken.getHeaderName());
            return (StringUtils.hasText(headerValue))
                    ? this.plain.resolveCsrfTokenValue(request, csrfToken)
                    : this.xor.resolveCsrfTokenValue(request, csrfToken);
        }
    }
}
