package com.fomentiq.curation.application;

import java.util.List;
import java.time.LocalDate;
import java.util.UUID;

public record ReviewQueueItem(UUID callId, String externalId, String portalEntryId, String title,
        String agencyName, String agencyAcronym, List<String> publishers, List<String> agencies,
        long reviewFieldCount, String opportunityStatus, LocalDate deadline) { }
