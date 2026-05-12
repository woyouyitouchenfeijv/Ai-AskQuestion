package com.ai.askquestion.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeProperties {
    private String basePath = "src/main/resources/knowledge-base";
    private int chunkSize = 500;
    private int chunkOverlap = 100;
}
