package com.sas.agents.triage.model;

/**
 * Classified intent for an inbound support message.
 * Drives conditional routing in {@link com.sas.agents.triage.graph.TriageGraphFactory}.
 */
public enum Intent {
    BILLING,
    TECHNICAL,
    GENERAL,
    /** Classifier could not confidently assign one of the above. */
    UNCLEAR
}
