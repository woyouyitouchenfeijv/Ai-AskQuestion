package com.ai.askquestion.service.impl;

import com.ai.askquestion.config.ElasticsearchProperties;
import com.ai.askquestion.domain.KnowledgeChunk;
import com.ai.askquestion.domain.QuestionRecord;
import com.ai.askquestion.dto.EsInitRequest;
import com.ai.askquestion.dto.EsInitResponse;
import com.ai.askquestion.mapper.KnowledgeChunkMapper;
import com.ai.askquestion.mapper.QuestionRecordMapper;
import com.ai.askquestion.service.ElasticsearchIndexService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexServiceImpl implements ElasticsearchIndexService {
    private static final int DEFAULT_BATCH_SIZE = 200;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ElasticsearchProperties properties;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final QuestionRecordMapper questionRecordMapper;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public EsInitResponse createKnowledgeChunkIndex(boolean recreate) {
        return createIndex(properties.getKnowledgeChunkIndex(), recreate, buildKnowledgeChunkMapping());
    }

    @Override
    public EsInitResponse createQaQuestionIndex(boolean recreate) {
        return createIndex(properties.getQaQuestionIndex(), recreate, buildQaQuestionMapping());
    }

    @Override
    public EsInitResponse initializeKnowledgeChunkIndex(EsInitRequest request) {
        EsInitRequest safeRequest = request == null ? new EsInitRequest() : request;
        EsInitResponse createResponse = createKnowledgeChunkIndex(safeRequest.isRecreate());
        createQaQuestionIndex(false);
        String indexName = properties.getKnowledgeChunkIndex();
        int batchSize = safeRequest.getBatchSize() == null || safeRequest.getBatchSize() <= 0 ? DEFAULT_BATCH_SIZE : safeRequest.getBatchSize();

        int offset = 0;
        int total = 0;
        int success = 0;
        int fail = 0;
        while (true) {
            List<KnowledgeChunk> chunks = knowledgeChunkMapper.selectForEsInit(safeRequest.getKnowledgeBaseId(), offset, batchSize);
            if (chunks.isEmpty()) {
                break;
            }
            for (KnowledgeChunk chunk : chunks) {
                total++;
                String esDocId = indexKnowledgeChunk(chunk);
                if (esDocId == null) {
                    fail++;
                } else {
                    success++;
                }
            }
            offset += chunks.size();
        }
        String message = String.format("%s；初始化完成，total=%d, success=%d, fail=%d", createResponse.getMessage(), total, success, fail);
        return new EsInitResponse(indexName, createResponse.isIndexCreated(), total, success, fail, message);
    }

    @Override
    public String indexKnowledgeChunk(KnowledgeChunk chunk) {
        if (!properties.isEnabled() || chunk == null || chunk.getId() == null) {
            return null;
        }
        createKnowledgeChunkIndex(false);
        String esDocId = buildKnowledgeChunkEsDocId(chunk);
        try {
            restTemplate.exchange(indexUrl(properties.getKnowledgeChunkIndex()) + "/_doc/" + esDocId,
                    HttpMethod.PUT, jsonEntity(toKnowledgeChunkEsDocument(chunk)), String.class);
            knowledgeChunkMapper.updateEsDocId(chunk.getId(), esDocId);
            return esDocId;
        } catch (Exception e) {
            log.error("Index knowledge chunk failed, chunkId={}", chunk.getId(), e);
            return null;
        }
    }

    @Override
    public String indexQuestion(QuestionRecord record) {
        if (!properties.isEnabled() || record == null || record.getId() == null) {
            return null;
        }
        createQaQuestionIndex(false);
        String esDocId = "qa-" + record.getId();
        try {
            restTemplate.exchange(indexUrl(properties.getQaQuestionIndex()) + "/_doc/" + esDocId,
                    HttpMethod.PUT, jsonEntity(toQuestionEsDocument(record)), String.class);
            questionRecordMapper.updateEsQuestionId(record.getId(), esDocId);
            return esDocId;
        } catch (Exception e) {
            log.error("Index QA question failed, recordId={}", record.getId(), e);
            return null;
        }
    }

    private EsInitResponse createIndex(String indexName, boolean recreate, Map<String, Object> mapping) {
        String indexUrl = indexUrl(indexName);
        if (recreate) {
            deleteIndexIfExists(indexUrl, indexName);
        }
        if (indexExists(indexUrl)) {
            return EsInitResponse.indexOnly(indexName, false, "索引已存在，无需重复创建");
        }
        restTemplate.exchange(indexUrl, HttpMethod.PUT, jsonEntity(mapping), String.class);
        return EsInitResponse.indexOnly(indexName, true, "索引创建成功");
    }

    private Map<String, Object> buildKnowledgeChunkMapping() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("knowledge_base_id", Map.of("type", "long"));
        fields.put("document_id", Map.of("type", "long"));
        fields.put("chunk_id", Map.of("type", "long"));
        fields.put("chunk_index", Map.of("type", "integer"));
        fields.put("file_name", Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword", "ignore_above", 256))));
        fields.put("file_path", Map.of("type", "keyword"));
        fields.put("content", Map.of("type", "text", "analyzer", "standard"));
        fields.put("content_vector", Map.of("type", "dense_vector", "dims", 384, "index", false));
        fields.put("content_hash", Map.of("type", "keyword"));
        fields.put("version", Map.of("type", "integer"));
        fields.put("created_at", Map.of("type", "date"));
        fields.put("updated_at", Map.of("type", "date"));
        return Map.of("settings", Map.of("number_of_shards", 1, "number_of_replicas", 0), "mappings", Map.of("properties", fields));
    }

    private Map<String, Object> buildQaQuestionMapping() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("question_record_id", Map.of("type", "long"));
        fields.put("knowledge_base_id", Map.of("type", "long"));
        fields.put("question", Map.of("type", "text", "analyzer", "standard"));
        fields.put("normalized_question", Map.of("type", "text", "analyzer", "standard"));
        fields.put("question_hash", Map.of("type", "keyword"));
        fields.put("question_vector", Map.of("type", "dense_vector", "dims", 384, "index", false));
        fields.put("answer_status", Map.of("type", "keyword"));
        fields.put("created_at", Map.of("type", "date"));
        fields.put("updated_at", Map.of("type", "date"));
        return Map.of("settings", Map.of("number_of_shards", 1, "number_of_replicas", 0), "mappings", Map.of("properties", fields));
    }

    private Map<String, Object> toKnowledgeChunkEsDocument(KnowledgeChunk chunk) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("knowledge_base_id", chunk.getKnowledgeBaseId());
        doc.put("document_id", chunk.getDocumentId());
        doc.put("chunk_id", chunk.getId());
        doc.put("chunk_index", chunk.getChunkIndex());
        doc.put("file_name", chunk.getFileName());
        doc.put("file_path", chunk.getFilePath());
        doc.put("content", chunk.getContent());
        doc.put("content_hash", chunk.getContentHash());
        doc.put("version", chunk.getVersion());
        doc.put("created_at", formatDateTime(chunk.getCreatedAt()));
        doc.put("updated_at", formatDateTime(chunk.getUpdatedAt()));
        return doc;
    }

    private Map<String, Object> toQuestionEsDocument(QuestionRecord record) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("question_record_id", record.getId());
        doc.put("knowledge_base_id", record.getKnowledgeBaseId());
        doc.put("question", record.getQuestion());
        doc.put("normalized_question", record.getNormalizedQuestion());
        doc.put("question_hash", record.getQuestionHash());
        doc.put("answer_status", record.getAnswerStatus());
        doc.put("created_at", formatDateTime(record.getCreatedAt()));
        doc.put("updated_at", formatDateTime(record.getUpdatedAt()));
        return doc;
    }

    private boolean indexExists(String indexUrl) {
        try {
            restTemplate.exchange(indexUrl, HttpMethod.HEAD, HttpEntity.EMPTY, Void.class);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    private void deleteIndexIfExists(String indexUrl, String indexName) {
        if (!indexExists(indexUrl)) {
            return;
        }
        restTemplate.exchange(indexUrl, HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
        log.info("Deleted Elasticsearch index: {}", indexName);
    }

    private HttpEntity<String> jsonEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            return new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Serialize ES request failed", e);
        }
    }

    private String buildKnowledgeChunkEsDocId(KnowledgeChunk chunk) {
        return "chunk-" + chunk.getId();
    }

    private String indexUrl(String indexName) {
        return trimEndSlash(properties.getBaseUrl()) + "/" + indexName;
    }

    private String trimEndSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:9200";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).format(DATE_TIME_FORMATTER);
    }
}
