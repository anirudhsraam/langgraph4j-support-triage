package com.sas.agents.triage.graph;

import com.sas.agents.triage.state.TriageState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.bsc.langgraph4j.CompiledGraph;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the compiled graph end-to-end without any network calls: a
 * {@link ScriptedChatModel} returns queued canned responses in the same
 * order the graph's nodes call the model, so we can assert on routing
 * decisions deterministically — the agent-graph equivalent of mocking a
 * repository in a Spring @WebMvcTest.
 */
class TriageGraphFactoryTest {

    @Test
    void routesConfidentBillingMessageDirectlyToBillingHandler() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel(List.of(
                "INTENT: BILLING | CONFIDENCE: 0.92",
                "You were charged twice due to a known retry bug; a refund has been queued."
        ));

        CompiledGraph<TriageState> graph = TriageGraphFactory.build(model);

        Optional<TriageState> result = graph.invoke(
                Map.of(TriageState.KEY_INPUT, "I was charged twice for my subscription this month."));

        TriageState state = result.orElseThrow();
        assertEquals("BILLING", state.intent().map(Enum::name).orElse(null));
        assertTrue(state.resolution().orElseThrow().contains("refund"));
        assertTrue(!state.escalated());
    }

    @Test
    void lowConfidenceMessageEscalatesAfterExhaustingClarificationAttempts() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel(List.of(
                "INTENT: UNCLEAR | CONFIDENCE: 0.10",
                "INTENT: UNCLEAR | CONFIDENCE: 0.15",
                "INTENT: UNCLEAR | CONFIDENCE: 0.05"
        ));

        CompiledGraph<TriageState> graph = TriageGraphFactory.build(model);

        Optional<TriageState> result = graph.invoke(
                Map.of(TriageState.KEY_INPUT, "hey so about the thing from before"));

        TriageState state = result.orElseThrow();
        assertTrue(state.escalated());
        assertEquals(2, state.clarificationAttempts());
    }

    /**
     * Minimal fake that returns queued responses in call order.
     */
    private static final class ScriptedChatModel implements ChatLanguageModel {
        private final Deque<String> responses;

        ScriptedChatModel(List<String> scripted) {
            this.responses = new ArrayDeque<>(scripted);
        }

        @Override
        public String generate(String userMessage) {
            if (responses.isEmpty()) {
                throw new IllegalStateException("ScriptedChatModel ran out of canned responses");
            }
            return responses.poll();
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            throw new UnsupportedOperationException("Not used by these nodes");
        }
    }
}
