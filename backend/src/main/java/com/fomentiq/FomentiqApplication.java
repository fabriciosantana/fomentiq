package com.fomentiq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.fomentiq.ingestion.infrastructure.finep.validation.FinepLiveValidationCli;
import com.fomentiq.audit.application.AuditPackageCli;

@SpringBootApplication
public class FomentiqApplication {
    public static void main(String[] args) {
        if (AuditPackageCli.requested(args)) {
            System.exit(AuditPackageCli.execute(args));
        }
        if (FinepLiveValidationCli.requested(args)) {
            System.exit(new FinepLiveValidationCli().execute(args, System.getenv()));
        }
        SpringApplication.run(FomentiqApplication.class, args);
    }
}
