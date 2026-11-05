package com.example.bi.web;

import com.example.bi.dto.CsrfResponse;
import com.example.bi.dto.RegisterRequest;
import com.example.bi.dto.UserResponse;
import com.example.bi.service.identity.RegistrationService;
import com.example.bi.service.identity.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;

    public AuthController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) request.getAttribute("_csrf");
        }
        if (csrfToken != null) {
            // Touch token to force cookie write
            csrfToken.getToken();
            return ResponseEntity.ok(new CsrfResponse(
                    csrfToken.getToken(),
                    csrfToken.getHeaderName(),
                    csrfToken.getParameterName()
            ));
        }
        return ResponseEntity.ok(new CsrfResponse("", "X-XSRF-TOKEN", "_csrf"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal SecurityUser securityUser) {
        if (securityUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new UserResponse(
                securityUser.getId(),
                securityUser.getDisplayName(),
                securityUser.getUsername(),
                securityUser.getRole().name()
        ));
    }
}

