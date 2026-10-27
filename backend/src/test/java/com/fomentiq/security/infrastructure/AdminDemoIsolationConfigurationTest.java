package com.fomentiq.security.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AdminDemoIsolationConfigurationTest {
    @Test
    void acceptsOnlyDedicatedManualDemoResources() {
        MockEnvironment environment = validEnvironment();

        assertThatCode(() -> AdminDemoIsolationConfiguration.verifyConfiguration(environment))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsOperationalDatabaseBeforeApplicationStartup() {
        MockEnvironment environment = validEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.internal:5432/production");

        assertThatThrownBy(() -> AdminDemoIsolationConfiguration.verifyConfiguration(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("isolated fomentiq-manual-evaluation resources");
    }

    @Test
    void rejectsWrongObjectStorageBucket() {
        MockEnvironment environment = validEnvironment()
                .withProperty("fomentiq.storage.s3.bucket", "shared");

        assertThatThrownBy(() -> AdminDemoIsolationConfiguration.verifyConfiguration(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsOnlyTheDedicatedCurationE2eResourceTuple() {
        MockEnvironment environment = validEnvironment()
                .withProperty("FOMENTIQ_ADMIN_DEMO_COMPOSE_PROJECT", "fomentiq-curation-e2e-12345")
                .withProperty("FOMENTIQ_ADMIN_DEMO_POSTGRES_PORT", "25432")
                .withProperty("FOMENTIQ_ADMIN_DEMO_S3_PORT", "29000")
                .withProperty("FOMENTIQ_ADMIN_DEMO_S3_CONSOLE_PORT", "29001")
                .withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:25432/fomentiq_e2e")
                .withProperty("spring.datasource.username", "fomentiq_e2e")
                .withProperty("fomentiq.storage.s3.endpoint", "http://127.0.0.1:29000")
                .withProperty("fomentiq.storage.s3.bucket", "fomentiq-curation-e2e-12345");
        assertThatCode(() -> AdminDemoIsolationConfiguration.verifyConfiguration(environment))
                .doesNotThrowAnyException();
        environment.setProperty("fomentiq.storage.s3.bucket", "fomentiq-manual-evaluation");
        assertThatThrownBy(() -> AdminDemoIsolationConfiguration.verifyConfiguration(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsExactlyTheTwoIsolatedComposeServicesOnLoopbackPorts() {
        String listing = "pg-id|postgres|127.0.0.1:25432->5432/tcp\n"
                + "s3-id|object-storage|127.0.0.1:29000->9000/tcp, 127.0.0.1:29001->9001/tcp";
        assertThatCode(() -> AdminDemoIsolationConfiguration.verifyComposeListing(listing, 25432, 29000, 29001))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> AdminDemoIsolationConfiguration.verifyComposeListing(
                listing.replace("127.0.0.1:29000", "0.0.0.0:29000"), 25432, 29000, 29001))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AdminDemoIsolationConfiguration.verifyComposeListing(
                listing + "\nextra-id|other|", 25432, 29000, 29001))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAdditionalProfiles() {
        MockEnvironment environment = validEnvironment();
        environment.setActiveProfiles("manual-demo", "production");

        assertThatThrownBy(() -> AdminDemoIsolationConfiguration.verifyConfiguration(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void doesNotEnableAUserWhenDemoProfileIsAbsent() {
        MockEnvironment environment = validEnvironment();
        environment.setActiveProfiles("default");
        var users = new ApiSecurityConfiguration().applicationUsers(environment,
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder());

        assertThatThrownBy(() -> users.loadUserByUsername("reviewer"))
                .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
    }

    @Test
    void refusesEnabledDemoWithoutEnvironmentCredentials() {
        MockEnvironment environment = validEnvironment()
                .withProperty("FOMENTIQ_ADMIN_USERNAME", "")
                .withProperty("FOMENTIQ_ADMIN_PASSWORD", "");

        assertThatThrownBy(() -> new ApiSecurityConfiguration().applicationUsers(environment,
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configured through the process environment");
    }

    private static MockEnvironment validEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("FOMENTIQ_ADMIN_DEMO_COMPOSE_PROJECT", "fomentiq-manual-evaluation")
                .withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:15432/fomentiq")
                .withProperty("spring.datasource.username", "fomentiq")
                .withProperty("fomentiq.storage.s3.enabled", "true")
                .withProperty("fomentiq.storage.s3.endpoint", "http://127.0.0.1:19000")
                .withProperty("fomentiq.storage.s3.bucket", "fomentiq-manual-evaluation")
                .withProperty("FOMENTIQ_ADMIN_DEMO_S3_CONSOLE_PORT", "19001")
                .withProperty("fomentiq.admin.demo.enabled", "true");
        environment.setActiveProfiles("manual-demo");
        return environment;
    }
}
