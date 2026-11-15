package com.cognizant.backend.entity;

/** Languages a scenario can be asked in. Stored by name (EN, TA, HI, ML), so never rename them. */
public enum Language {
    EN("English"),
    TA("Tamil"),
    HI("Hindi"),
    ML("Malayalam");

    private final String displayName;

    Language(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
