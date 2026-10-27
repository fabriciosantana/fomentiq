package com.fomentiq.security.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import com.fomentiq.security.infrastructure.AdminDemoSecurityConfiguration;
import com.fomentiq.security.infrastructure.ApiSecurityConfiguration;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

class AdminDemoSessionSecurityTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().setActiveProfiles("manual-demo");
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "fomentiq.admin.demo.enabled=true",
                "FOMENTIQ_ADMIN_USERNAME=demo-reviewer",
                "FOMENTIQ_ADMIN_PASSWORD=temporary-test-only-password",
                "fomentiq.admin.demo.cookie-secure=true");
        context.register(TestMvcConfiguration.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    @Test
    void loginCreatesAuthenticatedSessionAndRotatesPreAuthenticationSessionId() throws Exception {
        MockHttpSession preAuthSession = new MockHttpSession();
        String previousSessionId = preAuthSession.getId();
        MvcResult login = mvc.perform(post("/api/admin/login").session(preAuthSession).with(csrf())
                        .param("username", "demo-reviewer").param("password", "temporary-test-only-password"))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotEqualTo(previousSessionId);

        mvc.perform(get("/api/admin/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("demo-reviewer"));
    }

    @Test
    void rejectsMutationWithoutCsrfToken() throws Exception {
        mvc.perform(post("/api/admin/login").param("username", "demo-reviewer")
                        .param("password", "temporary-test-only-password"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidDemoPasswordWithoutExposingCredentialDetails() throws Exception {
        mvc.perform(post("/api/admin/login").with(csrf())
                        .param("username", "demo-reviewer").param("password", "wrong-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void doesNotRevealSessionToAnonymousUser() throws Exception {
        mvc.perform(get("/api/admin/session")).andExpect(status().isUnauthorized());
    }

    @Test
    void deniesAuthenticatedUsersWithoutTheAdminRole() throws Exception {
        mvc.perform(get("/api/admin/session").with(user("analyst").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({AdminSessionController.class, ApiSecurityConfiguration.class, AdminDemoSecurityConfiguration.class})
    static class TestMvcConfiguration { }
}
