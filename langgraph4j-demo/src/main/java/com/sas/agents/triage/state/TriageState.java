package com.sas.agents.triage.state;

import com.sas.agents.triage.model.Intent;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Typed view over the graph's shared state map.
 * <p>
 * LangGraph4j keeps state as a {@code Map<String, Object>} under the hood so
 * nodes can be added independently without a shared POJO; this class is the
 * read/write "DTO" layer on top of it so the rest of the codebase never
 * touches raw string keys directly (same idea as wrapping a JPA projection
 * instead of passing a raw ResultSet around).
 * <p>
 * {@code messages} uses an append channel so every node that emits a message
 * adds to the transcript instead of overwriting it; every other key is
 * last-write-wins, which is the default for any key not listed in SCHEMA.
 */
public class TriageState extends AgentState {

    public static final String KEY_INPUT = "input";
    public static final String KEY_INTENT = "intent";
    public static final String KEY_CONFIDENCE = "confidence";
    public static final String KEY_MESSAGES = "messages";
    public static final String KEY_RESOLUTION = "resolution";
    public static final String KEY_CLARIFICATION_ATTEMPTS = "clarificationAttempts";
    public static final String KEY_ESCALATED = "escalated";

    /**
     * Channel schema: how each key merges when a node returns a partial state update.
     */
    public static final Map<String, Channel<?>> SCHEMA = Map.of(KEY_MESSAGES, Channels.appender(ArrayList::new), KEY_CLARIFICATION_ATTEMPTS, Channels.<Integer>base(() -> 0));

    public TriageState(Map<String, Object> initData) {
        super(initData);
    }

    public String input() {
        return this.<String>value(KEY_INPUT).orElse("");
    }

    public Optional<Intent> intent() {
        return this.<String>value(KEY_INTENT).map(Intent::valueOf);
    }

    public double confidence() {
        return this.<Double>value(KEY_CONFIDENCE).orElse(0.0);
    }

    @SuppressWarnings("unchecked")
    public List<String> messages() {
        return (List<String>) this.value(KEY_MESSAGES).orElseGet(List::of);
    }

    public Optional<String> resolution() {
        return this.value(KEY_RESOLUTION);
    }

    public int clarificationAttempts() {
        return this.<Integer>value(KEY_CLARIFICATION_ATTEMPTS).orElse(0);
    }

    public boolean escalated() {
        return this.<Boolean>value(KEY_ESCALATED).orElse(false);
    }
}
