package com.cognizant.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One judge metric, e.g. groundedness 0.92 (High). */
@Embeddable
public class EvaluationScore {

    @Column(name = "metric_key", nullable = false, length = 32)
    private String metricKey;

    @Column(nullable = false, length = 64)
    private String label;

    @Column(name = "score_value", nullable = false)
    private double value;

    @Column(nullable = false, length = 32)
    private String rating;

    /** UI tone hint: success, warning or danger. */
    @Column(nullable = false, length = 16)
    private String tone;

    protected EvaluationScore() {
    }

    public EvaluationScore(String metricKey, String label, double value, String rating, String tone) {
        this.metricKey = metricKey;
        this.label = label;
        this.value = value;
        this.rating = rating;
        this.tone = tone;
    }

    public String getMetricKey() {
        return metricKey;
    }

    public String getLabel() {
        return label;
    }

    public double getValue() {
        return value;
    }

    public String getRating() {
        return rating;
    }

    public String getTone() {
        return tone;
    }
}
