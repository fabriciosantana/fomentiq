package com.fomentiq.storage.infrastructure;

import com.fomentiq.storage.application.PrivateAuditArchive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PrivateAuditArchiveConfiguration {
    @Bean
    PrivateAuditArchive privateAuditArchive(
            @Value("${fomentiq.audit.archive.directory:}") String directory,
            @Value("${fomentiq.audit.archive.reader-group:}") String readerGroup) {
        return new LocalPrivateAuditArchive(directory, readerGroup);
    }
}
