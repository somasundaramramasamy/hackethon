package com.cognizant.backend.entity;

public enum Language {
    EN("English"),
    TA("Tamil"),
    HI("Hindi");

    private final String displayName;

    Language(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
