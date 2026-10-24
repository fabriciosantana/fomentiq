package com.fomentiq.extraction.application;

/** Safe provider failure metadata; it must never contain request headers or document contents. */
public class StructuredExtractionFailure extends RuntimeException {
    private final String provider;
    private final String model;

    public StructuredExtractionFailure(String provider, String model, String message) {
        super(message);
        this.provider = provider;
        this.model = model;
    }

    public String provider() { return provider; }
    public String model() { return model; }
}
