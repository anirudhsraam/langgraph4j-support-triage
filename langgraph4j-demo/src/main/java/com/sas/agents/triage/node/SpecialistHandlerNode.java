package com.sas.agents.triage.node;

import com.sas.agents.triage.state.TriageState;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;

/**
 * Reusable handler node parameterized by a system-prompt persona, so each
 * intent lane (billing/technical/general) is one instance of this class
 * instead of three near-duplicate node classes — same rationale as a single
 * {@code @Service} bean parameterized by strategy rather than one subclass
 * per case.
 */
@Slf4j
public class SpecialistHandlerNode implements NodeAction<TriageState> {

    private final String lane;
    private final String systemPersona;
    private final ChatLanguageModel chatModel;

    public SpecialistHandlerNode(String lane, String systemPersona, ChatLanguageModel chatModel) {
        this.lane = lane;
        this.systemPersona = systemPersona;
        this.chatModel = chatModel;
    }

    @Override
    public Map<String, Object> apply(TriageState state) {
        log.info("Routing to {} specialist handler", lane);

        String prompt = """
                %s

                Customer message: "%s"

                Write a concise, actionable reply (3-5 sentences). If you need
                information you don't have (account ID, order number, error logs,
                etc.), ask for it explicitly instead of guessing.
                """.formatted(systemPersona, state.input());

        String reply = chatModel.generate(prompt);

        return Map.of(
                TriageState.KEY_RESOLUTION, reply,
                TriageState.KEY_MESSAGES, "[" + lane + "] " + reply
        );
    }
}
