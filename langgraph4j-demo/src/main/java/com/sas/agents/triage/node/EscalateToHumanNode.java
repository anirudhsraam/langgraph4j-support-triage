package com.sas.agents.triage.node;

import com.sas.agents.triage.state.TriageState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;

/**
 * Terminal node reached when the agent can't confidently resolve the
 * request on its own — hands off to a human queue rather than guessing.
 */
@Slf4j
public class EscalateToHumanNode implements NodeAction<TriageState> {

    @Override
    public Map<String, Object> apply(TriageState state) {
        log.warn("Escalating to human agent after {} clarification attempt(s)",
                state.clarificationAttempts());

        return Map.of(
                TriageState.KEY_ESCALATED, true,
                TriageState.KEY_RESOLUTION, "This has been routed to a human support agent "
                        + "who will follow up shortly.",
                TriageState.KEY_MESSAGES, "[escalation] handed off to human queue"
        );
    }
}
