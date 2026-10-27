package com.fomentiq.extraction.application;

/** New strict contract for extraction requests; v1 remains immutable for historical artifacts. */
public final class ExtractionSchemaV2 {
    public static final String VERSION = "finep-call-v2";
    public static final java.util.List<String> FIELDS = ExtractionSchemaV1.FIELDS;
    public static final java.util.Set<String> CRITICAL = ExtractionSchemaV1.CRITICAL;
    private ExtractionSchemaV2() { }
}
