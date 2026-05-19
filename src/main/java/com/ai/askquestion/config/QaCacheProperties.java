package com.ai.askquestion.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "qa.cache")
public class QaCacheProperties {
    private boolean exactEnabled = true;
    private boolean similarEnabled = true;
    private double exactThreshold = 0.98D;
    private double verifiedSimilarThreshold = 0.88D;
    private double referenceSimilarThreshold = 0.75D;
    private int candidateLimit = 200;
}
