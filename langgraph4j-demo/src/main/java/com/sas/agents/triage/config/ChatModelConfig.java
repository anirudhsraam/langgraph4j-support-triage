package com.sas.agents.triage.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Central place to configure the LLM backing the graph's nodes.
 * <p>
 * Kept provider-specific wiring isolated here (LangChain4j supports OpenAI,
 * Azure OpenAI, Anthropic, Ollama, Bedrock, etc. via sibling modules) so
 * swapping models later is a one-file change, same as swapping a
 * DataSource bean.
 */
@Configuration
public class ChatModelConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${openai.api-key:${OPENAI_API_KEY:demo-key}}") String apiKey,
            @Value("${openai.model-name:gpt-4o-mini}") String modelName,
            @Value("${openai.temperature:0.0}") double temperature) {

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(2)
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
