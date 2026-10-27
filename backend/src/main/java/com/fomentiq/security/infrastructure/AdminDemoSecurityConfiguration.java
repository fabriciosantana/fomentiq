package com.fomentiq.security.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/** Session authentication exists only in the explicit, isolated manual-demo profile. */
@Configuration
@Profile("manual-demo")
@ConditionalOnProperty(name = "fomentiq.admin.demo.enabled", havingValue = "true")
public class AdminDemoSecurityConfiguration {
    @Bean
    @Order(1)
    SecurityFilterChain adminSessionSecurity(HttpSecurity http,
            @Value("${fomentiq.admin.demo.cookie-secure:true}") boolean secureCookies) throws Exception {
        CookieCsrfTokenRepository csrf = new CookieCsrfTokenRepository();
        csrf.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax").secure(secureCookies));
        var requestHandler = new CsrfTokenRequestAttributeHandler();
        return http.securityMatcher("/api/admin/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/admin/csrf", "/api/admin/login").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .csrf(configurer -> configurer.csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(requestHandler))
                .formLogin(form -> form.loginPage("/api/admin/login")
                        .loginProcessingUrl("/api/admin/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .failureHandler((request, response, exception) -> response.setStatus(HttpStatus.UNAUTHORIZED.value())))
                .logout(logout -> logout.logoutUrl("/api/admin/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value())))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }
}
