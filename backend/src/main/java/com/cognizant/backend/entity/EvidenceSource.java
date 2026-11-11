package com.cognizant.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** A trusted source the investigator cited, e.g. Heart & Stroke. */
@Embeddable
public class EvidenceSource {

    @Column(nullable = false, length = 120)
    private String organization;

    @Column(nullable = false, length = 16)
    private String shortName;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String url;

    protected EvidenceSource() {
    }

    public EvidenceSource(String organization, String shortName, String title, String url) {
        this.organization = organization;
        this.shortName = shortName;
        this.title = title;
        this.url = url;
    }

    public String getOrganization() {
        return organization;
    }

    public String getShortName() {
        return shortName;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }
}
