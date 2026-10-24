package com.fomentiq.extraction.infrastructure;

import com.fomentiq.extraction.application.StructuredExtractionFailure;

public class ExtractionProviderException extends StructuredExtractionFailure {
    public ExtractionProviderException(String message) { this(message, "unknown"); }
    public ExtractionProviderException(String message, String model) { super("openai", model, message); }
}
