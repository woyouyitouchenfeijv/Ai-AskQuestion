package com.ai.askquestion.service.impl;

import com.ai.askquestion.config.QaCacheProperties;
import com.ai.askquestion.domain.QuestionRecord;
import com.ai.askquestion.dto.NormalizedQuestion;
import com.ai.askquestion.mapper.QuestionRecordMapper;
import com.ai.askquestion.service.ElasticsearchIndexService;
import com.ai.askquestion.service.QuestionCacheHit;
import com.ai.askquestion.service.QuestionCacheService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionCacheServiceImpl implements QuestionCacheService {
    private final QuestionRecordMapper questionRecordMapper;
    private final ElasticsearchIndexService elasticsearchIndexService;
    private final EmbeddingModel embeddingModel;
    private final QaCacheProperties qaCacheProperties;

    private final Map<String, List<Float>> embeddingCache = new ConcurrentHashMap<>();

    @Override
    public Optional<QuestionCacheHit> findUsableAnswer(Long knowledgeBaseId, String question, NormalizedQuestion normalizedQuestion) {
        if (normalizedQuestion == null) {
            return Optional.empty();
        }

        if (qaCacheProperties.isExactEnabled()) {
            QuestionRecord exact = questionRecordMapper.findVerifiedByQuestionHash(knowledgeBaseId, normalizedQuestion.getQuestionHash());
            if (exact != null) {
                return Optional.of(new QuestionCacheHit(exact, "EXACT", null));
            }
        }

        if (!qaCacheProperties.isSimilarEnabled() || embeddingModel == null || !StringUtils.hasText(question)) {
            return Optional.empty();
        }

        List<QuestionRecord> candidates = questionRecordMapper.findLatestVerifiedCandidates(
                knowledgeBaseId,
                Math.max(qaCacheProperties.getCandidateLimit(), 1)
        );
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        try {
            List<Float> queryVector = embeddingForText(question);
            if (queryVector.isEmpty()) {
                return Optional.empty();
            }

            return candidates.stream()
                    .map(candidate -> toSimilarHit(candidate, queryVector))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(hit -> hit.getSimilarityScore() != null
                            && hit.getSimilarityScore() >= qaCacheProperties.getVerifiedSimilarThreshold())
                    .max(Comparator.comparing(QuestionCacheHit::getSimilarityScore));
        } catch (Exception e) {
            log.warn("Question similar-cache lookup failed, fallback to exact/RAG. knowledgeBaseId={}, question={}", knowledgeBaseId, question, e);
            return Optional.empty();
        }
    }

    @Override
    public void indexQuestion(QuestionRecord record) {
        if (record == null) {
            return;
        }
        evictEmbeddingCache(record);
        if (elasticsearchIndexService == null) {
            return;
        }
        String esQuestionId = elasticsearchIndexService.indexQuestion(record);
        log.debug("Indexed question record to ES, recordId={}, esQuestionId={}", record.getId(), esQuestionId);
    }

    private Optional<QuestionCacheHit> toSimilarHit(QuestionRecord candidate, List<Float> queryVector) {
        if (candidate == null || candidate.getId() == null) {
            return Optional.empty();
        }
        String candidateText = candidateText(candidate);
        if (!StringUtils.hasText(candidateText)) {
            return Optional.empty();
        }
        List<Float> candidateVector = embeddingForText(candidateText);
        if (candidateVector.isEmpty()) {
            return Optional.empty();
        }
        double similarity = cosineSimilarity(queryVector, candidateVector);
        if (Double.isNaN(similarity) || similarity <= 0D) {
            return Optional.empty();
        }
        return Optional.of(new QuestionCacheHit(candidate, "SIMILAR", similarity));
    }

    private List<Float> embeddingForText(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return embeddingCache.computeIfAbsent(text, key -> {
            Embedding embedding = embeddingModel.embed(key).content();
            List<Float> vector = embedding == null ? List.of() : embedding.vectorAsList();
            return vector == null ? List.of() : List.copyOf(vector);
        });
    }

    private String candidateText(QuestionRecord candidate) {
        if (StringUtils.hasText(candidate.getQuestion())) {
            return candidate.getQuestion();
        }
        return candidate.getNormalizedQuestion();
    }

    private void evictEmbeddingCache(QuestionRecord record) {
        List<String> keys = new ArrayList<>();
        if (StringUtils.hasText(record.getQuestion())) {
            keys.add(record.getQuestion());
        }
        if (StringUtils.hasText(record.getNormalizedQuestion())) {
            keys.add(record.getNormalizedQuestion());
        }
        keys.forEach(embeddingCache::remove);
    }

    private double cosineSimilarity(List<Float> left, List<Float> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty() || left.size() != right.size()) {
            return Double.NaN;
        }
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int i = 0; i < left.size(); i++) {
            double l = left.get(i);
            double r = right.get(i);
            dot += l * r;
            leftNorm += l * l;
            rightNorm += r * r;
        }
        if (leftNorm == 0D || rightNorm == 0D) {
            return Double.NaN;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
