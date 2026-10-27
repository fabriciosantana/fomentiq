package com.fomentiq.security.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal SPA session and CSRF bootstrap for the isolated administrator demo. */
@RestController
@RequestMapping("/api/admin")
@Profile("manual-demo")
@ConditionalOnProperty(name = "fomentiq.admin.demo.enabled", havingValue = "true")
public class AdminSessionController {
    @GetMapping("/csrf")
    public CsrfTokenDto csrf(CsrfToken token) {
        return new CsrfTokenDto(token.getHeaderName(), token.getToken());
    }

    @GetMapping("/session")
    public AdminSession session(Authentication authentication) {
        return new AdminSession(authentication.getName());
    }

    public record CsrfTokenDto(String headerName, String token) { }
    public record AdminSession(String username) { }
}
