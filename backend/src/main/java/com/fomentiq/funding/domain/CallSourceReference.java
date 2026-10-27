package com.fomentiq.funding.domain;

import java.util.UUID;

/** Publisher-scoped external identity for a call. Titles and content hashes are never identity. */
public record CallSourceReference(UUID id, UUID callId, UUID publisherId, String externalId,
                                  String portalEntryId, String canonicalUrl) { }
