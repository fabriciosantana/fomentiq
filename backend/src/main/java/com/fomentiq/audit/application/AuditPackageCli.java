package com.fomentiq.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fomentiq.FomentiqApplication;
import com.fomentiq.audit.infrastructure.AuditPackageExporter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Internal process entry point for offline package verification and explicit terminal-run export. */
public final class AuditPackageCli {
    private AuditPackageCli() { }

    public static boolean requested(String[] args) {
        return Arrays.stream(args).anyMatch(argument -> argument.startsWith("--fomentiq.audit.verify=")
                || argument.startsWith("--fomentiq.audit.export-run="));
    }

    public static int execute(String[] args) {
        try {
            if (args.length != 1) return 2;
            String[] option = args[0].split("=", 2);
            if (option.length != 2 || option[1].isBlank()) return 2;
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            if ("--fomentiq.audit.verify".equals(option[0])) {
                var result = new AuditPackageVerifier(mapper).verify(Path.of(option[1]));
                System.out.println(mapper.writeValueAsString(result));
                return result.valid() ? 0 : 3;
            }
            if (!"--fomentiq.audit.export-run".equals(option[0])) return 2;
            UUID runId = UUID.fromString(option[1]);
            try (var context = new SpringApplicationBuilder(FomentiqApplication.class)
                    .web(WebApplicationType.NONE).logStartupInfo(false).run()) {
                var result = context.getBean(AuditPackageExporter.class).export(runId);
                System.out.println(mapper.writeValueAsString(new CliExportResult(result.archiveKey(),
                        result.archiveSha256(), result.verification().integrityValid(),
                        result.verification().complete(), result.verification().filesVerified(),
                        result.verification().gaps())));
                return result.verification().valid() ? 0 : 3;
            }
        } catch (RuntimeException | java.io.IOException failure) {
            System.err.println("Audit package operation failed; details omitted");
            return 2;
        }
    }

    private record CliExportResult(String archiveKey, String archiveSha256, boolean integrityValid,
                                   boolean complete, int filesVerified, java.util.List<String> gaps) { }
}
