package com.ai.askquestion.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.elasticsearch")
public class ElasticsearchProperties {
    private boolean enabled = false;
    private String baseUrl = "http://localhost:9200";
    private String knowledgeChunkIndex = "knowledge_chunk_index";
    private String qaQuestionIndex = "qa_question_index";
    private int topK = 5;
    private double minScore = 0.0D;
}
