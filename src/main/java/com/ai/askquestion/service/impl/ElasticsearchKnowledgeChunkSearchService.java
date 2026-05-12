package com.ai.askquestion.service.impl;

import com.ai.askquestion.config.ElasticsearchProperties;
import com.ai.askquestion.service.KnowledgeChunkSearchResult;
import com.ai.askquestion.service.KnowledgeChunkSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ElasticsearchKnowledgeChunkSearchService implements KnowledgeChunkSearchService {

    private final RestTemplate restTemplate;
    private final ElasticsearchProperties properties;

    public ElasticsearchKnowledgeChunkSearchService(ElasticsearchProperties properties) {
        this.restTemplate = new RestTemplate();
        this.properties = properties;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<KnowledgeChunkSearchResult> search(Long knowledgeBaseId, String question) {
        if (!properties.isEnabled()) {
            log.debug("Elasticsearch search disabled, skip knowledge_chunk_index search");
            return List.of();
        }

        try {
            String url = trimEndSlash(properties.getBaseUrl()) + "/" + properties.getKnowledgeChunkIndex() + "/_search";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = buildSearchBody(knowledgeBaseId, question);
            Map<String, Object> response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
            return parseResults(response);
        } catch (Exception e) {
            log.error("Search knowledge_chunk_index failed, knowledgeBaseId={}, question={}", knowledgeBaseId, question, e);
            return List.of();
        }
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
