package com.cognizant.backend.ai.dto;

/** @param assistantDailyLimit requests per day the assistant's API key allows (0 = unknown / no limit) */
public record AiStatus(Provider assistant, Provider judge, Provider investigator, int assistantDailyLimit) {

    public record Provider(String provider, String model, boolean configured) {
    }
}
