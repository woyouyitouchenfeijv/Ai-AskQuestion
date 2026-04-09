package com.ai.askquestion.config;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
import java.util.List;

/**
 * @author dx
 * @date 2026/2/26 13:47
 */


@Configuration
public class KnowledgeBaseConfig {

    /**
     * 1. 嵌入模型：将文本转为向量的工具（本地运行，无需API）
     * EmbeddingModel  向量模型 负责把文本转换成向量
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        // 这是一个完全本地运行的嵌入模型，只有几十MB
        return new AllMiniLmL6V2EmbeddingModel();
    }

    /**
     * 2. 向量存储：先使用内存存储，重启后数据会丢失，适合测试
     *    生产环境可以换成 PostgreSQL + pgvector、ChromaDB、Redis等
     * EmbeddingStore   向量模型 负责对向量进行保存、搜索
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    /**
     * 3. 文档摄入器：封装了整个"加载->分割->向量化->存储"的流水线
     * EmbeddingStoreIngestor 文档->存储向量的工作流，入库流水线
     */
    @Bean
    public EmbeddingStoreIngestor embeddingStoreIngestor(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel) {
        return EmbeddingStoreIngestor.builder()
                // 文档分割器：将长文档切成小块，每块500字符，重叠100字符（保持上下文连贯）
                .documentSplitter(DocumentSplitters.recursive(500, 100))
                //转成向量
                .embeddingModel(embeddingModel)
                //存储到库
                .embeddingStore(embeddingStore)
                .build();
    }

    /**
     * 4. 启动时加载文档
     */
    @Bean
    public Boolean loadDocuments(EmbeddingStoreIngestor ingestor) {
        // 指定文档目录路径
        String knowledgeBasePath = "src/main/resources/knowledge-base";

        // 加载该目录下的所有文档（自动识别PDF、Word、TXT等）
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(
                Paths.get(knowledgeBasePath),
                new ApacheTikaDocumentParser() // Apache Tika支持几十种格式
        );

        System.out.println("加载到 " + documents.size() + " 个文档");

        // 执行摄入流水线：分割 -> 向量化 -> 存储
        ingestor.ingest(documents);
        System.out.println("文档已处理并存入向量库！");

        return true;
    }
}