package com.sas.agents.triage.web;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public final class TriageDtos {

    private TriageDtos() {}

    public record TriageRequest(@NotBlank String message) {}

    public record TriageResponse(
            String intent,
            double confidence,
            String resolution,
            boolean escalated,
            List<String> trace
    ) {}
}
