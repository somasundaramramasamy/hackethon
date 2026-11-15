package com.cognizant.backend.entity;

public enum RunType {
    /** Every active scenario, with the prompt version that is in use. */
    BASELINE,
    /** After a fix: the baseline's failures plus English and control questions, with the proposed prompt. */
    RETEST
}
