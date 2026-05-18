package com.ai.askquestion.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeProperties {
    /**
     * 知识库目录：支持 classpath:knowledge-base 或本地绝对/相对路径。
     */
    private String basePath = "classpath:knowledge-base";
    private int chunkSize = 500;
    private int chunkOverlap = 100;

    /**
     * memory 模式启动时自动入库。
     */
    private boolean autoIngestOnStartup = true;

    /**
     * 启动自动入库使用的默认知识库 ID（不存在时会自动创建）。
     */
    private Long defaultKnowledgeBaseId = 1L;

    /**
     * 启动自动创建知识库时使用的名称。
     */
    private String defaultKnowledgeBaseName = "默认知识库";
}
