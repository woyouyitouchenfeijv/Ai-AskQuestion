package com.ai.askquestion.service.impl;

import com.ai.askquestion.config.ElasticsearchProperties;
import com.ai.askquestion.domain.KnowledgeChunk;
import com.ai.askquestion.mapper.KnowledgeChunkMapper;
import com.ai.askquestion.service.KnowledgeChunkSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElasticsearchKnowledgeChunkSearchServiceTest {

    @Test
    void shouldFallbackToDatabaseChunksWhenElasticsearchDisabled() {
        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setEnabled(false);
        properties.setTopK(3);

        KnowledgeChunkMapper knowledgeChunkMapper = mock(KnowledgeChunkMapper.class);
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(11L);
        chunk.setDocumentId(22L);
        chunk.setKnowledgeBaseId(1L);
        chunk.setContent("公司名字：12345\n公司地址：北京市海淀区55号院\n公司电话：13333335555");
        when(knowledgeChunkMapper.findByKnowledgeBaseId(null)).thenReturn(List.of(chunk));

        ElasticsearchKnowledgeChunkSearchService service =
                new ElasticsearchKnowledgeChunkSearchService(properties, knowledgeChunkMapper);

        List<KnowledgeChunkSearchResult> results = service.search(null, "我的公司在哪！");

        assertFalse(results.isEmpty());
        assertEquals(11L, results.get(0).getChunkId());
        assertEquals(22L, results.get(0).getDocumentId());
        assertEquals(chunk.getContent(), results.get(0).getContent());
    }
}
