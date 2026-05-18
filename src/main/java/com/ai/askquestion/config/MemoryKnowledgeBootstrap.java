package com.ai.askquestion.config;

import com.ai.askquestion.domain.KnowledgeBase;
import com.ai.askquestion.dto.IngestResponse;
import com.ai.askquestion.mapper.KnowledgeBaseMapper;
import com.ai.askquestion.service.KnowledgeIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("memory")
@RequiredArgsConstructor
public class MemoryKnowledgeBootstrap implements ApplicationRunner {

    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeIngestService knowledgeIngestService;

    @Override
    public void run(ApplicationArguments args) {
        if (!knowledgeProperties.isAutoIngestOnStartup()) {
            log.info("memory 模式启动自动入库已关闭（knowledge.auto-ingest-on-startup=false）");
            return;
        }

        Long knowledgeBaseId = ensureKnowledgeBase();
        IngestResponse response = knowledgeIngestService.ingest(knowledgeBaseId);
        log.info("memory 模式启动自动入库完成: knowledgeBaseId={}, scannedFiles={}, parsedDocuments={}, chunksCreated={}",
                response.getKnowledgeBaseId(), response.getScannedFiles(), response.getParsedDocuments(), response.getChunksCreated());
    }

    private Long ensureKnowledgeBase() {
        Long expectedId = knowledgeProperties.getDefaultKnowledgeBaseId();
        if (expectedId != null) {
            KnowledgeBase existing = knowledgeBaseMapper.findById(expectedId);
            if (existing != null) {
                return existing.getId();
            }
        }

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setName(knowledgeProperties.getDefaultKnowledgeBaseName());
        knowledgeBase.setDescription("memory 模式启动自动创建");
        knowledgeBase.setStatus("ACTIVE");
        knowledgeBaseMapper.insert(knowledgeBase);
        log.info("memory 模式自动创建知识库: id={}, name={}", knowledgeBase.getId(), knowledgeBase.getName());
        return knowledgeBase.getId();
    }
}
