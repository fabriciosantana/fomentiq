package com.fomentiq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.fomentiq.ingestion.infrastructure.finep.validation.FinepLiveValidationCli;

@SpringBootApplication
public class FomentiqApplication {
    public static void main(String[] args) {
        if (FinepLiveValidationCli.requested(args)) {
            System.exit(new FinepLiveValidationCli().execute(args, System.getenv()));
        }
        SpringApplication.run(FomentiqApplication.class, args);
    }
}
