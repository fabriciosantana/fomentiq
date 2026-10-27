package com.fomentiq.funding.domain;

import java.util.UUID;

/** Official publisher whose source-native record references a funding opportunity. */
public record SourcePublisher(UUID id, String code, String name, String officialUrl) { }
