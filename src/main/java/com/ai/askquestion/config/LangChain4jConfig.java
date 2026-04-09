package com.ai.askquestion.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j Configuration openai
 */
@Configuration
public class LangChain4jConfig {

    @Value("${ai.openai.api-key}")
    private String apiKey;

    @Value("${ai.openai.model-name}")
    private String modelName;

    @Value("${ai.openai.temperature}")
    private Double temperature;

    @Value("${ai.openai.max-tokens}")
    private Integer maxTokens;

    @Value("${ai.openai.baseUrl}")
    private String baseUrl;
    @Value("${ai.openai.timeout}")
    private Long timeout;

    @Bean
    public ChatLanguageModel chatLanguageModel() {

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(timeout))
                .build();
    }

}
