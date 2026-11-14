package com.cognizant.backend.entity;

public enum ResultVerdict {
    PASS,
    FAIL,
    /** The model call itself failed (timeout, quota), so the answer could not be checked. */
    ERROR
}
