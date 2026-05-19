package com.ai.askquestion.service.impl;

import com.ai.askquestion.config.QaCacheProperties;
import com.ai.askquestion.domain.QuestionRecord;
import com.ai.askquestion.dto.NormalizedQuestion;
import com.ai.askquestion.mapper.QuestionRecordMapper;
import com.ai.askquestion.service.QuestionCacheHit;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionCacheServiceImplTest {

    @Test
    void shouldReturnSimilarVerifiedAnswerWhenVectorSimilarityAboveThreshold() {
        QuestionRecordMapper mapper = mock(QuestionRecordMapper.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QaCacheProperties properties = new QaCacheProperties();
        properties.setExactEnabled(true);
        properties.setSimilarEnabled(true);
        properties.setVerifiedSimilarThreshold(0.88D);
        properties.setCandidateLimit(10);

        QuestionRecord candidate = new QuestionRecord();
        candidate.setId(12L);
        candidate.setKnowledgeBaseId(1L);
        candidate.setQuestion("12345的公司在哪！");
        candidate.setNormalizedQuestion("12345的公司在哪!");
        candidate.setAnswer("北京市海淀区55号院");
        candidate.setAnswerStatus("VERIFIED");

        when(mapper.findVerifiedByQuestionHash(1L, "hash-1")).thenReturn(null);
        when(mapper.findLatestVerifiedCandidates(1L, 10)).thenReturn(List.of(candidate));
        when(embeddingModel.embed("12345的公司位置！"))
                .thenReturn(Response.from(Embedding.from(List.of(1.0F, 0.0F))));
        when(embeddingModel.embed("12345的公司在哪！"))
                .thenReturn(Response.from(Embedding.from(List.of(0.99F, 0.01F))));

        QuestionCacheServiceImpl service = new QuestionCacheServiceImpl(mapper, null, embeddingModel, properties);

        Optional<QuestionCacheHit> hit = service.findUsableAnswer(
                1L,
                "12345的公司位置！",
                new NormalizedQuestion("12345的公司位置！", "12345的公司位置!", "hash-1")
        );

        assertTrue(hit.isPresent());
        assertEquals("SIMILAR", hit.get().getHitType());
        assertEquals(12L, hit.get().getRecord().getId());
        assertTrue(hit.get().getSimilarityScore() >= 0.88D);
    }

    @Test
    void shouldShortCircuitWhenExactCacheHitFound() {
        QuestionRecordMapper mapper = mock(QuestionRecordMapper.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QaCacheProperties properties = new QaCacheProperties();
        properties.setExactEnabled(true);
        properties.setSimilarEnabled(true);

        QuestionRecord exact = new QuestionRecord();
        exact.setId(7L);
        exact.setAnswer("缓存答案");
        exact.setAnswerStatus("VERIFIED");
        when(mapper.findVerifiedByQuestionHash(1L, "hash-exact")).thenReturn(exact);

        QuestionCacheServiceImpl service = new QuestionCacheServiceImpl(mapper, null, embeddingModel, properties);
        Optional<QuestionCacheHit> hit = service.findUsableAnswer(
                1L,
                "12345的公司在哪！",
                new NormalizedQuestion("12345的公司在哪！", "12345的公司在哪!", "hash-exact")
        );

        assertTrue(hit.isPresent());
        assertEquals("EXACT", hit.get().getHitType());
        assertEquals(7L, hit.get().getRecord().getId());
        verify(embeddingModel, never()).embed(anyString());
    }
}
