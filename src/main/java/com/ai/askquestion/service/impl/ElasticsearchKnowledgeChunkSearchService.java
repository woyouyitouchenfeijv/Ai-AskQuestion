package com.ai.askquestion.service.impl;

import com.ai.askquestion.config.ElasticsearchProperties;
import com.ai.askquestion.domain.KnowledgeChunk;
import com.ai.askquestion.mapper.KnowledgeChunkMapper;
import com.ai.askquestion.service.KnowledgeChunkSearchResult;
import com.ai.askquestion.service.KnowledgeChunkSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class ElasticsearchKnowledgeChunkSearchService implements KnowledgeChunkSearchService {

    private final RestTemplate restTemplate;
    private final ElasticsearchProperties properties;
    private final KnowledgeChunkMapper knowledgeChunkMapper;

    public ElasticsearchKnowledgeChunkSearchService(ElasticsearchProperties properties,
                                                    KnowledgeChunkMapper knowledgeChunkMapper) {
        this.restTemplate = new RestTemplate();
        this.properties = properties;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<KnowledgeChunkSearchResult> search(Long knowledgeBaseId, String question) {
        if (!properties.isEnabled()) {
            log.debug("Elasticsearch search disabled, fallback to database chunk search");
            return searchFromDatabase(knowledgeBaseId, question);
        }

        try {
            String url = trimEndSlash(properties.getBaseUrl()) + "/" + properties.getKnowledgeChunkIndex() + "/_search";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = buildSearchBody(knowledgeBaseId, question);
            Map<String, Object> response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
            return parseResults(response);
        } catch (Exception e) {
            log.error("Search knowledge_chunk_index failed, knowledgeBaseId={}, question={}, fallback to database chunk search",
                    knowledgeBaseId, question, e);
            return searchFromDatabase(knowledgeBaseId, question);
        }
    }

    private List<KnowledgeChunkSearchResult> searchFromDatabase(Long knowledgeBaseId, String question) {
        List<KnowledgeChunk> chunks = knowledgeChunkMapper.findByKnowledgeBaseId(knowledgeBaseId);
        if (chunks == null || chunks.isEmpty() || !StringUtils.hasText(question)) {
            return List.of();
        }

        List<String> searchTerms = buildSearchTerms(question);
        if (searchTerms.isEmpty()) {
            return List.of();
        }

        return chunks.stream()
                .map(chunk -> toResult(chunk, computeScore(chunk.getContent(), searchTerms)))
                .filter(result -> result.getScore() != null && result.getScore() > 0)
                .sorted(Comparator.comparing(KnowledgeChunkSearchResult::getScore, Comparator.reverseOrder())
                        .thenComparing(KnowledgeChunkSearchResult::getChunkId, Comparator.nullsLast(Long::compareTo)))
                .limit(Math.max(properties.getTopK(), 1))
                .toList();
    }

    private KnowledgeChunkSearchResult toResult(KnowledgeChunk chunk, double score) {
        return new KnowledgeChunkSearchResult(chunk.getId(), chunk.getDocumentId(), chunk.getContent(), score);
    }

    private double computeScore(String content, List<String> searchTerms) {
        if (!StringUtils.hasText(content)) {
            return 0D;
        }
        String normalizedContent = normalizeForSearch(content);
        double score = 0D;
        for (String term : searchTerms) {
            if (normalizedContent.contains(term)) {
                score += Math.max(1D, term.length());
            }
        }
        return score;
    }

    private List<String> buildSearchTerms(String question) {
        String normalized = normalizeForSearch(question);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }

        Set<String> terms = new LinkedHashSet<>();
        if (normalized.length() >= 2) {
            for (int i = 0; i < normalized.length() - 1; i++) {
                terms.add(normalized.substring(i, i + 2));
            }
        }
        if (normalized.length() <= 12) {
            terms.add(normalized);
        }
        for (String token : normalized.split("[^a-z0-9\\u4e00-\\u9fa5]+")) {
            if (token.length() >= 2) {
                terms.add(token);
            }
        }
        return new ArrayList<>(terms);
    }

    private String normalizeForSearch(String text) {
        return text == null ? "" : text
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]+", "")
                .replace("在哪儿", "在哪")
                .trim();
    }

    private Map<String, Object> buildSearchBody(Long knowledgeBaseId, String question) {
        Map<String, Object> match = Map.of("content", Map.of("query", question));
        Map<String, Object> query;
        if (knowledgeBaseId == null) {
            query = Map.of("match", match);
        } else {
            query = Map.of("bool", Map.of(
                    "must", List.of(Map.of("match", match)),
                    "filter", List.of(Map.of("term", Map.of("knowledge_base_id", knowledgeBaseId)))
            ));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("size", properties.getTopK());
        body.put("query", query);
        body.put("_source", List.of("chunk_id", "document_id", "content"));
        return body;
    }

    @SuppressWarnings("unchecked")
    private List<KnowledgeChunkSearchResult> parseResults(Map<String, Object> response) {
        if (response == null || response.get("hits") == null) {
            return List.of();
        }
        Map<String, Object> hitsObject = (Map<String, Object>) response.get("hits");
        List<Map<String, Object>> hits = (List<Map<String, Object>>) hitsObject.getOrDefault("hits", List.of());
        List<KnowledgeChunkSearchResult> results = new ArrayList<>();
        for (Map<String, Object> hit : hits) {
            Double score = toDouble(hit.get("_score"));
            if (score != null && score < properties.getMinScore()) {
                continue;
            }
            Map<String, Object> source = (Map<String, Object>) hit.getOrDefault("_source", Map.of());
            results.add(new KnowledgeChunkSearchResult(
                    toLong(source.get("chunk_id")),
                    toLong(source.get("document_id")),
                    String.valueOf(source.getOrDefault("content", "")),
                    score
            ));
        }
        return results;
    }

    private String trimEndSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:9200";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.valueOf(String.valueOf(value));
    }
}
