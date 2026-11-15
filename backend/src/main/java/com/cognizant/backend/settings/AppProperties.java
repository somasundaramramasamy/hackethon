package com.cognizant.backend.settings;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * App settings, bound from {@code kavasam.*}.
 *
 * @param readOnly when true (public demo), every request that changes data or calls a model is
 *                 refused, so visitors can browse recorded results without spending money
 * @param allowedOrigins comma-separated origins allowed to call the API from a browser
 */
@ConfigurationProperties(prefix = "kavasam")
public record AppProperties(Boolean readOnly, String allowedOrigins) {

    public AppProperties {
        readOnly = readOnly != null && readOnly;
        allowedOrigins = allowedOrigins == null || allowedOrigins.isBlank() ? "http://localhost:5173" : allowedOrigins;
    }

    public boolean isReadOnly() {
        return readOnly;
    }
}
