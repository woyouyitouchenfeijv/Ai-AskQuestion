package com.ai.askquestion.config;

import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class KnowledgeBaseConfig {

    @Value("${ai.openai.api-key}")
    private String apiKey;

    @Value("${ai.openai.baseUrl}")
    private String baseUrl;

    @Value("${ai.openai.timeout:60}")
    private Long timeout;

    @Value("${ai.openai.embedding-model-name:text-embedding-3-small}")
    private String embeddingModelName;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(embeddingModelName)
                .timeout(Duration.ofSeconds(timeout))
                .build();
    }

    /**
     * 仅保留给旧版 LangChain4j Assistant Bean 使用；正式知识库内容由 MySQL + ES 持久化。
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    public EmbeddingStoreIngestor embeddingStoreIngestor(EmbeddingStore<TextSegment> embeddingStore,
                                                        EmbeddingModel embeddingModel) {
        return EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(500, 100))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
    }

    /**
     * 说明：memory 模式下会在应用启动时自动执行知识入库（见 MemoryKnowledgeBootstrap）。
     * 生产模式保持手动触发：POST /api/knowledge/{knowledgeBaseId}/ingest。
     */
}
