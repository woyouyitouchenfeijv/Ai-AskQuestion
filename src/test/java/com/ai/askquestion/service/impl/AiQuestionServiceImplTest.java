package com.ai.askquestion.service.impl;

import com.ai.askquestion.domain.QuestionRecord;
import com.ai.askquestion.dto.AskQuestionRequest;
import com.ai.askquestion.dto.AskQuestionResponse;
import com.ai.askquestion.mapper.QuestionRecordMapper;
import com.ai.askquestion.service.KnowledgeChunkSearchResult;
import com.ai.askquestion.service.KnowledgeChunkSearchService;
import com.ai.askquestion.service.QuestionNormalizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiQuestionServiceImplTest {

    private final ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
    private final KnowledgeChunkSearchService knowledgeChunkSearchService = mock(KnowledgeChunkSearchService.class);
    private final QuestionRecordMapper questionRecordMapper = mock(QuestionRecordMapper.class);
    private final AiQuestionServiceImpl service = new AiQuestionServiceImpl(
            chatLanguageModel,
            new QuestionNormalizer(),
            knowledgeChunkSearchService,
            questionRecordMapper
    );

    @Test
    void askQuestionRagShouldReturnVerifiedCacheWhenQuestionHashMatched() {
        QuestionRecord cached = new QuestionRecord();
        cached.setId(12L);
        cached.setQuestion("Spring Boot 是什么？");
        cached.setAnswer("缓存答案");
        cached.setSourceType("RAG");
        cached.setAnswerStatus("VERIFIED");
        cached.setHitType("EXACT");
        when(questionRecordMapper.findLatestVerifiedByQuestionHash(eq(null), any())).thenReturn(cached);

        AskQuestionResponse response = service.askQuestionRag(new AskQuestionRequest("  spring boot是什么?"));

        assertEquals("缓存答案", response.getAnswer());
        assertEquals("CACHE", response.getSourceType());
        assertEquals("EXACT", response.getHitType());
        assertTrue(response.isCacheHit());
        assertEquals(12L, response.getRecordId());
        verify(questionRecordMapper).increaseHitCount(12L);
        verify(knowledgeChunkSearchService, never()).search(any(), any());
        verify(chatLanguageModel, never()).generate(any(String.class));
    }

    @Test
    void askQuestionRagShouldSearchKnowledgeGenerateDraftAndSaveRecordWhenCacheMissed() {
        when(questionRecordMapper.findLatestVerifiedByQuestionHash(eq(null), any())).thenReturn(null);
        when(knowledgeChunkSearchService.search(null, "如何申请年假？")).thenReturn(List.of(
                new KnowledgeChunkSearchResult(101L, 201L, "员工连续工作满一年后可申请年假。", 8.5D)
        ));
        when(chatLanguageModel.generate(any(String.class))).thenReturn("根据制度，连续工作满一年后可申请年假。");

        AskQuestionResponse response = service.askQuestionRag(new AskQuestionRequest("如何申请年假？"));

        assertEquals("根据制度，连续工作满一年后可申请年假。", response.getAnswer());
        assertEquals("RAG", response.getSourceType());
        assertEquals("RAG", response.getHitType());
        assertEquals("DRAFT", response.getAnswerStatus());
        assertEquals(List.of(101L), response.getSourceChunkIds());
        assertEquals(List.of(201L), response.getSourceDocumentIds());
        assertFalse(response.isCacheHit());
        verify(questionRecordMapper).insert(any(QuestionRecord.class));
    }
}
