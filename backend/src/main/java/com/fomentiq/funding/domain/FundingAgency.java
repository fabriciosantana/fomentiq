package com.fomentiq.funding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "funding_agency", uniqueConstraints = @UniqueConstraint(name = "uk_funding_agency_acronym", columnNames = "acronym"))
public class FundingAgency {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 40)
    private String acronym;
    @Column(name = "official_url", length = 2048)
    private String officialUrl;

    protected FundingAgency() { }

    public FundingAgency(String name, String acronym, String officialUrl) {
        this.name = name;
        this.acronym = acronym;
        this.officialUrl = officialUrl;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getAcronym() { return acronym; }
    public String getOfficialUrl() { return officialUrl; }
}
