package com.sas.agents.triage.config;

import com.sas.agents.triage.graph.TriageGraphFactory;
import com.sas.agents.triage.state.TriageState;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphConfig {

    /**
     * The compiled graph is stateless and thread-safe to invoke concurrently
     * (each {@code invoke()} call gets its own state map), so it's built once
     * and shared — same lifecycle as a Spring {@code @Bean} wrapping a
     * connection pool.
     */
    @Bean
    public CompiledGraph<TriageState> triageGraph(ChatLanguageModel chatLanguageModel) throws GraphStateException {
        return TriageGraphFactory.build(chatLanguageModel);
    }
}
