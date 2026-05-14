package com.ai.askquestion.service.impl;

import com.ai.askquestion.domain.QuestionFeedback;
import com.ai.askquestion.domain.QuestionRecord;
import com.ai.askquestion.dto.AskQuestionRequest;
import com.ai.askquestion.dto.AskQuestionResponse;
import com.ai.askquestion.dto.NormalizedQuestion;
import com.ai.askquestion.mapper.QuestionFeedbackMapper;
import com.ai.askquestion.mapper.QuestionRecordMapper;
import com.ai.askquestion.service.AiQuestionService;
import com.ai.askquestion.service.ElasticsearchIndexService;
import com.ai.askquestion.service.KnowledgeChunkSearchResult;
import com.ai.askquestion.service.KnowledgeChunkSearchService;
import com.ai.askquestion.service.KnowledgePromptBuilder;
import com.ai.askquestion.service.QuestionNormalizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiQuestionServiceImpl implements AiQuestionService {
    private final ChatLanguageModel chatLanguageModel;
    private final QuestionNormalizer questionNormalizer;
    private final KnowledgeChunkSearchService knowledgeChunkSearchService;
    private final QuestionRecordMapper questionRecordMapper;
    private final KnowledgePromptBuilder knowledgePromptBuilder;
    private final ElasticsearchIndexService elasticsearchIndexService;
    private final QuestionFeedbackMapper questionFeedbackMapper;

    @Autowired
    public AiQuestionServiceImpl(ChatLanguageModel chatLanguageModel,
                                 QuestionNormalizer questionNormalizer,
                                 KnowledgeChunkSearchService knowledgeChunkSearchService,
                                 QuestionRecordMapper questionRecordMapper,
                                 KnowledgePromptBuilder knowledgePromptBuilder,
                                 ElasticsearchIndexService elasticsearchIndexService,
                                 QuestionFeedbackMapper questionFeedbackMapper) {
        this.chatLanguageModel = chatLanguageModel;
        this.questionNormalizer = questionNormalizer;
        this.knowledgeChunkSearchService = knowledgeChunkSearchService;
        this.questionRecordMapper = questionRecordMapper;
        this.knowledgePromptBuilder = knowledgePromptBuilder;
        this.elasticsearchIndexService = elasticsearchIndexService;
        this.questionFeedbackMapper = questionFeedbackMapper;
    }

    /** 测试专用构造器：兼容现有单测。 */
    public AiQuestionServiceImpl(ChatLanguageModel chatLanguageModel,
                                 QuestionNormalizer questionNormalizer,
                                 KnowledgeChunkSearchService knowledgeChunkSearchService,
                                 QuestionRecordMapper questionRecordMapper) {
        this(chatLanguageModel, questionNormalizer, knowledgeChunkSearchService, questionRecordMapper,
                new KnowledgePromptBuilder(), null, null);
    }

    @Override
    public AskQuestionResponse askQuestion(AskQuestionRequest request) {
        log.info("Received question: {}", request.getQuestion());
        String answer = chatLanguageModel.generate(request.getQuestion());
        return AskQuestionResponse.of(request.getQuestion(), answer);
    }

    @Override
    public AskQuestionResponse askQuestionRag(AskQuestionRequest request) {
        long startTime = System.currentTimeMillis();
        String question = request.getQuestion();
        NormalizedQuestion normalized = normalize(question);
        Long knowledgeBaseId = request.getKnowledgeBaseId();

        QuestionRecord cached = questionRecordMapper.findVerifiedByQuestionHash(knowledgeBaseId, normalized.getQuestionHash());
        if (cached == null) {
            // 兼容旧 mapper 方法名和现有单测 mock。
            cached = questionRecordMapper.findLatestVerifiedByQuestionHash(knowledgeBaseId, normalized.getQuestionHash());
        }
        if (cached != null) {
            questionRecordMapper.increaseHitCount(cached.getId());
            log.info("RAG cache hit, recordId={}, questionHash={}", cached.getId(), normalized.getQuestionHash());
            return AskQuestionResponse.cacheHit(question, cached.getAnswer(), cached.getId());
        }

        List<KnowledgeChunkSearchResult> chunks = knowledgeChunkSearchService.search(knowledgeBaseId, question);
        String prompt = knowledgePromptBuilder.build(question, chunks);
        String answer = chatLanguageModel.generate(prompt);
        long costTimeMs = System.currentTimeMillis() - startTime;

        QuestionRecord record = buildDraftRecord(request, normalized, answer, chunks, costTimeMs);
        questionRecordMapper.insert(record);
        if (elasticsearchIndexService != null) {
            elasticsearchIndexService.indexQuestion(record);
        }
        log.info("RAG generated draft answer, recordId={}, chunks={}, cost={} ms", record.getId(), chunks.size(), costTimeMs);
        return toGeneratedResponse(record, chunks);
    }

    @Override
    public boolean verifyAnswer(Long questionRecordId) {
        boolean updated = questionRecordMapper.verify(questionRecordId) > 0;
        reindexQuestionIfPossible(questionRecordId);
        return updated;
    }

    @Override
    public boolean disableAnswer(Long questionRecordId) {
        boolean updated = questionRecordMapper.disable(questionRecordId) > 0;
        reindexQuestionIfPossible(questionRecordId);
        return updated;
    }

    @Override
    public boolean correctAnswer(Long questionRecordId, String correctedAnswer) {
        if (!StringUtils.hasText(correctedAnswer)) {
            throw new IllegalArgumentException("correctedAnswer 不能为空");
        }
        int updated = questionRecordMapper.updateAnswer(questionRecordId, correctedAnswer, "VERIFIED");
        if (updated > 0 && questionFeedbackMapper != null) {
            QuestionFeedback feedback = new QuestionFeedback();
            feedback.setQuestionRecordId(questionRecordId);
            feedback.setFeedbackType("CORRECTED");
            feedback.setCorrectedAnswer(correctedAnswer);
            questionFeedbackMapper.insert(feedback);
        }
        reindexQuestionIfPossible(questionRecordId);
        return updated > 0;
    }

    private NormalizedQuestion normalize(String question) {
        return new NormalizedQuestion(question, questionNormalizer.normalize(question), questionNormalizer.hash(question));
    }

    private QuestionRecord buildDraftRecord(AskQuestionRequest request,
                                            NormalizedQuestion normalized,
                                            String answer,
                                            List<KnowledgeChunkSearchResult> chunks,
                                            long costTimeMs) {
        QuestionRecord record = new QuestionRecord();
        record.setKnowledgeBaseId(request.getKnowledgeBaseId());
        record.setQuestion(request.getQuestion());
        record.setNormalizedQuestion(normalized.getNormalizedQuestion());
        record.setQuestionHash(normalized.getQuestionHash());
        record.setAnswer(answer);
        record.setAnswerStatus("DRAFT");
        record.setSourceType("RAG");
        record.setHitType(chunks.isEmpty() ? "NONE" : "RAG");
        record.setSimilarityScore(bestScore(chunks));
        record.setSourceChunkIds(joinIds(chunks.stream().map(KnowledgeChunkSearchResult::getChunkId).toList()));
        record.setSourceDocumentIds(joinIds(chunks.stream().map(KnowledgeChunkSearchResult::getDocumentId).toList()));
        record.setCostTimeMs(costTimeMs);
        return record;
    }

    private AskQuestionResponse toGeneratedResponse(QuestionRecord record, List<KnowledgeChunkSearchResult> chunks) {
        BigDecimal score = record.getSimilarityScore() == null ? null : BigDecimal.valueOf(record.getSimilarityScore());
        return new AskQuestionResponse(record.getId(), record.getQuestion(), record.getAnswer(), record.getSourceType(),
                record.getHitType(), score, parseIds(record.getSourceChunkIds()), parseIds(record.getSourceDocumentIds()),
                record.getCostTimeMs(), record.getAnswerStatus(), false);
    }

    private Double bestScore(List<KnowledgeChunkSearchResult> chunks) {
        return chunks.stream().map(KnowledgeChunkSearchResult::getScore).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private String joinIds(List<Long> ids) {
        String joined = ids.stream().filter(Objects::nonNull).distinct().map(String::valueOf).collect(Collectors.joining(","));
        return joined.isBlank() ? null : joined;
    }

    private List<Long> parseIds(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).filter(StringUtils::hasText).map(Long::valueOf).toList();
    }

    private void reindexQuestionIfPossible(Long questionRecordId) {
        if (elasticsearchIndexService == null) {
            return;
        }
        QuestionRecord record = questionRecordMapper.findById(questionRecordId);
        if (record != null) {
            elasticsearchIndexService.indexQuestion(record);
        }
    }
}
