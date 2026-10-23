package com.fomentiq.funding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "funding_program")
public class FundingProgram {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private FundingAgency agency;
    @Column(nullable = false, length = 300)
    private String name;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "instrument_types", columnDefinition = "text[]")
    private String[] instrumentTypes;

    protected FundingProgram() { }

    public FundingProgram(FundingAgency agency, String name, String description, String[] instrumentTypes) {
        this.agency = agency;
        this.name = name;
        this.description = description;
        this.instrumentTypes = instrumentTypes == null ? new String[0] : instrumentTypes.clone();
    }

    public UUID getId() { return id; }
    public FundingAgency getAgency() { return agency; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String[] getInstrumentTypes() { return instrumentTypes.clone(); }
}
