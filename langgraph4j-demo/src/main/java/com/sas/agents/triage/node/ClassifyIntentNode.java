package com.sas.agents.triage.node;

import com.sas.agents.triage.model.Intent;
import com.sas.agents.triage.state.TriageState;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entry node: classifies the customer message into an {@link Intent} with a
 * confidence score, using a constrained-output prompt so the response can be
 * parsed deterministically (the LLM equivalent of validating a request DTO
 * at the controller boundary before it reaches a service).
 */
@Slf4j
@RequiredArgsConstructor
public class ClassifyIntentNode implements NodeAction<TriageState> {

    private static final Pattern RESULT_PATTERN =
            Pattern.compile("INTENT:\\s*(\\w+)\\s*\\|\\s*CONFIDENCE:\\s*([0-9.]+)");

    private final ChatLanguageModel chatModel;

    @Override
    public Map<String, Object> apply(TriageState state) {
        String userMessage = state.input();
        log.info("Classifying intent for message of length {}", userMessage.length());

        String prompt = """
                Classify the customer support message below into exactly one category:
                BILLING, TECHNICAL, GENERAL, or UNCLEAR (use UNCLEAR only if truly ambiguous).

                Respond in this EXACT format, nothing else:
                INTENT: <CATEGORY> | CONFIDENCE: <0.0-1.0>

                Message: "%s"
                """.formatted(userMessage);

        String raw = chatModel.generate(prompt);
        Matcher matcher = RESULT_PATTERN.matcher(raw.trim());

        Intent intent;
        double confidence;
        if (matcher.find()) {
            intent = parseIntentSafely(matcher.group(1));
            confidence = parseConfidenceSafely(matcher.group(2));
        } else {
            log.warn("Classifier returned unparseable output, defaulting to UNCLEAR: {}", raw);
            intent = Intent.UNCLEAR;
            confidence = 0.0;
        }

        return Map.of(
                TriageState.KEY_INTENT, intent.name(),
                TriageState.KEY_CONFIDENCE, confidence,
                TriageState.KEY_MESSAGES, "Classified as " + intent + " (confidence=" + confidence + ")"
        );
    }

    private Intent parseIntentSafely(String raw) {
        try {
            return Intent.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Intent.UNCLEAR;
        }
    }

    private double parseConfidenceSafely(String raw) {
        try {
            return Math.max(0.0, Math.min(1.0, Double.parseDouble(raw)));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
