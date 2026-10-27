package com.fomentiq.security.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** Default policy keeps the read API public and denies administrative and write APIs. */
@Configuration
@EnableMethodSecurity
public class ApiSecurityConfiguration {
    @Bean
    PasswordEncoder applicationPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService applicationUsers(Environment environment, PasswordEncoder passwordEncoder) {
        boolean demoEnabled = environment.acceptsProfiles(org.springframework.core.env.Profiles.of("manual-demo"))
                && environment.getProperty("fomentiq.admin.demo.enabled", Boolean.class, false);
        if (!demoEnabled) {
            return username -> { throw new UsernameNotFoundException("No administrative user is enabled"); };
        }
        String username = environment.getProperty("FOMENTIQ_ADMIN_USERNAME", "");
        String password = environment.getProperty("FOMENTIQ_ADMIN_PASSWORD", "");
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Demo admin credentials must be configured through the process environment");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(passwordEncoder.encode(password)).roles("ADMIN").build());
    }

    @Bean
    @Order(2)
    SecurityFilterChain publicReadOnlySecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/opportunities/**", "/openapi.yaml", "/actuator/health")
                        .permitAll()
                        .requestMatchers("/api/admin/**", "/api/**").denyAll()
                        .anyRequest().permitAll())
                .build();
    }
}
