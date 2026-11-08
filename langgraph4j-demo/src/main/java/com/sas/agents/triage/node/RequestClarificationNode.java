package com.sas.agents.triage.node;

import com.sas.agents.triage.state.TriageState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;

/**
 * Fired when the classifier is unsure. Increments a bounded attempt counter
 * so the conditional edge in {@code TriageGraphFactory} can force an
 * escalation instead of looping forever — the graph-level equivalent of a
 * retry policy with a max-attempts cutoff.
 */
@Slf4j
public class RequestClarificationNode implements NodeAction<TriageState> {

    @Override
    public Map<String, Object> apply(TriageState state) {
        int attempts = state.clarificationAttempts() + 1;
        log.info("Requesting clarification, attempt {}", attempts);

        String clarifyingQuestion = "Could you share a bit more detail — for example, is this "
                + "about a charge/invoice, something not working, or a general question?";

        return Map.of(
                TriageState.KEY_CLARIFICATION_ATTEMPTS, attempts,
                TriageState.KEY_MESSAGES, "[clarify] " + clarifyingQuestion
        );
    }
}
