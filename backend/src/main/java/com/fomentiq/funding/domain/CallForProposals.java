package com.fomentiq.funding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "call_for_proposals", uniqueConstraints = @UniqueConstraint(
        name = "uk_call_agency_external_id", columnNames = {"agency_id", "external_id"}))
public class CallForProposals {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private FundingProgram program;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private FundingAgency agency;
    @Column(name = "external_id", nullable = false, length = 160)
    private String externalId;
    @Column(nullable = false, length = 500)
    private String title;
    @Column(columnDefinition = "text")
    private String summary;
    @Column(name = "instrument_type", length = 100)
    private String instrumentType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CallStatus status;
    @Column(name = "publication_date")
    private LocalDate publicationDate;
    @Column(name = "opening_date")
    private LocalDate openingDate;
    private LocalDate deadline;
    @Column(name = "official_url", length = 2048)
    private String officialUrl;
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;
    @Column(name = "last_checked_at", nullable = false)
    private Instant lastCheckedAt;

    protected CallForProposals() { }

    public CallForProposals(FundingAgency agency, FundingProgram program, String externalId, String title,
                            String summary, String instrumentType, CallStatus status, LocalDate publicationDate,
                            LocalDate openingDate, LocalDate deadline, String officialUrl,
                            Instant detectedAt, Instant lastCheckedAt) {
        this.agency = agency;
        this.program = program;
        this.externalId = externalId;
        this.title = title;
        this.summary = summary;
        this.instrumentType = instrumentType;
        this.status = status;
        this.publicationDate = publicationDate;
        this.openingDate = openingDate;
        this.deadline = deadline;
        this.officialUrl = officialUrl;
        this.detectedAt = detectedAt;
        this.lastCheckedAt = lastCheckedAt;
    }

    public UUID getId() { return id; }
    public String getExternalId() { return externalId; }
    public String getTitle() { return title; }
    public CallStatus getStatus() { return status; }
}
