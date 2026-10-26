package com.fomentiq.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Arrays;

/** Standalone mode for validating a factual review against a complete audit package without Spring. */
public final class FactualReviewCli {
    private FactualReviewCli() { }

    public static boolean requested(String[] args) {
        return Arrays.stream(args).anyMatch(argument -> argument.startsWith("--fomentiq.factual-review.verify="));
    }

    public static int execute(String[] args) {
        try {
            if (args.length != 2 || !args[0].startsWith("--fomentiq.factual-review.verify=")) return 2;
            String packagePath = args[0].substring("--fomentiq.factual-review.verify=".length());
            if (packagePath.isBlank() || !args[1].startsWith("--fomentiq.factual-review.record=")) return 2;
            String reviewPath = args[1].substring("--fomentiq.factual-review.record=".length());
            if (reviewPath.isBlank()) return 2;
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            var result = new FactualReviewValidator(mapper).validate(Path.of(packagePath), Path.of(reviewPath));
            System.out.println(mapper.writeValueAsString(result));
            return result.valid() ? 0 : 3;
        } catch (RuntimeException | java.io.IOException failure) {
            System.err.println("Factual review validation failed; details omitted");
            return 2;
        }
    }
}
