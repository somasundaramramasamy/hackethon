package com.cognizant.backend.entity;

/** Outcome of one check, or the combined outcome of a result. Stored by name. */
public enum ResultVerdict {
    PASS,
    FAIL,
    /** The checks disagree or are unsure, so a person decides. */
    NEEDS_REVIEW,
    /** The model call itself failed (timeout, rate limit), so the answer could not be checked. */
    ERROR
}
