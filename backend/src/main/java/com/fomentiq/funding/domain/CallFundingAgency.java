package com.fomentiq.funding.domain;

import java.util.UUID;

/** Documented agency association; primary identifies the agency exposed by legacy single-agency fields. */
public record CallFundingAgency(UUID callId, UUID agencyId, boolean primary) { }
