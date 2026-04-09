package com.ai.askquestion.config;

import com.ai.askquestion.service.Assistant;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 定义AI助手接口：LangChain4j会动态实现它
 *  @author dx
 *  @date 2026/2/26 13:58
 */
@Configuration
public class RagServiceConfig {

    /**
     * 创建RAG增强的AI助手
     */
    @Bean
    public Assistant assistant(
            ChatLanguageModel chatModel,
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel) {

        // 创建内容检索器：它负责去向量库中搜索相关文档
        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)           // 每次检索返回最多3个相关片段
                .minScore(0.6)            // 相似度低于0.6的不返回
                .build();

        // 使用AiServices将检索器绑定到Assistant接口
        return AiServices.builder(Assistant.class)
                .chatLanguageModel(chatModel)
                .contentRetriever(retriever) // 关键：注入RAG能力！
                .build();
    }
}