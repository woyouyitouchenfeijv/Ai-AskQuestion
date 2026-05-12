package com.ai.askquestion.service.impl;

import com.ai.askquestion.config.KnowledgeProperties;
import com.ai.askquestion.domain.KnowledgeChunk;
import com.ai.askquestion.domain.KnowledgeDocument;
import com.ai.askquestion.dto.IngestResponse;
import com.ai.askquestion.mapper.KnowledgeChunkMapper;
import com.ai.askquestion.mapper.KnowledgeDocumentMapper;
import com.ai.askquestion.mapper.QuestionRecordMapper;
import com.ai.askquestion.service.ElasticsearchIndexService;
import com.ai.askquestion.service.KnowledgeIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestServiceImpl implements KnowledgeIngestService {
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final QuestionRecordMapper questionRecordMapper;
    private final ElasticsearchIndexService elasticsearchIndexService;

    @Override
    @Transactional
    public IngestResponse ingest(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            throw new IllegalArgumentException("knowledgeBaseId 不能为空");
        }
        Path basePath = Paths.get(knowledgeProperties.getBasePath()).toAbsolutePath().normalize();
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            throw new IllegalArgumentException("知识库目录不存在: " + basePath);
        }

        Counter counter = new Counter();
        try (var stream = Files.walk(basePath)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .forEach(path -> ingestOne(knowledgeBaseId, basePath, path, counter));
        } catch (IOException e) {
            throw new IllegalStateException("扫描知识库目录失败: " + basePath, e);
        }

        return new IngestResponse(knowledgeBaseId, counter.scannedFiles, counter.parsedDocuments,
                counter.skippedDocuments, counter.failedDocuments, counter.chunksCreated, counter.chunksIndexed,
                String.format("入库完成：扫描%d个文件，解析%d个，跳过%d个，失败%d个，创建%d个chunk，索引%d个chunk",
                        counter.scannedFiles, counter.parsedDocuments, counter.skippedDocuments,
                        counter.failedDocuments, counter.chunksCreated, counter.chunksIndexed));
    }

    private void ingestOne(Long knowledgeBaseId, Path basePath, Path path, Counter counter) {
        counter.scannedFiles++;
        String relativePath = basePath.relativize(path.toAbsolutePath().normalize()).toString();
        try {
            String fileHash = sha256(Files.readAllBytes(path));
            KnowledgeDocument existing = knowledgeDocumentMapper.findByKnowledgeBaseIdAndFilePath(knowledgeBaseId, relativePath);
            if (existing != null && fileHash.equals(existing.getFileHash()) && "PARSED".equals(existing.getParseStatus())) {
                counter.skippedDocuments++;
                return;
            }

            String content = readText(path);
            if (content.isBlank()) {
                counter.skippedDocuments++;
                return;
            }

            KnowledgeDocument document = existing == null ? new KnowledgeDocument() : existing;
            document.setKnowledgeBaseId(knowledgeBaseId);
            document.setFileName(path.getFileName().toString());
            document.setFilePath(relativePath);
            document.setFileType(fileType(path));
            document.setFileHash(fileHash);
            document.setParseStatus("PARSED");
            document.setErrorMessage(null);
            if (existing == null) {
                document.setVersion(1);
                knowledgeDocumentMapper.insert(document);
            } else {
                questionRecordMapper.markStaleByDocumentId(existing.getId());
                knowledgeChunkMapper.deleteByDocumentId(existing.getId());
                knowledgeDocumentMapper.updateParseSuccess(document);
            }

            List<String> chunks = split(content, knowledgeProperties.getChunkSize(), knowledgeProperties.getChunkOverlap());
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setKnowledgeBaseId(knowledgeBaseId);
                chunk.setDocumentId(document.getId());
                chunk.setChunkIndex(i);
                chunk.setContent(chunks.get(i));
                chunk.setContentHash(sha256(chunks.get(i).getBytes(StandardCharsets.UTF_8)));
                chunk.setTokenCount(chunks.get(i).length());
                chunk.setVersion(document.getVersion() == null ? 1 : document.getVersion());
                chunk.setFileName(document.getFileName());
                chunk.setFilePath(document.getFilePath());
                knowledgeChunkMapper.insert(chunk);
                if (elasticsearchIndexService.indexKnowledgeChunk(chunk) != null) {
                    counter.chunksIndexed++;
                }
                counter.chunksCreated++;
            }
            counter.parsedDocuments++;
        } catch (Exception e) {
            counter.failedDocuments++;
            log.error("Ingest file failed, path={}", path, e);
            KnowledgeDocument existing = knowledgeDocumentMapper.findByKnowledgeBaseIdAndFilePath(knowledgeBaseId, relativePath);
            if (existing != null) {
                knowledgeDocumentMapper.updateParseFailed(existing.getId(), e.getMessage());
            }
        }
    }

    private String readText(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private List<String> split(String content, int chunkSize, int overlap) {
        int safeChunkSize = Math.max(chunkSize, 100);
        int safeOverlap = Math.max(0, Math.min(overlap, safeChunkSize / 2));
        String normalized = content.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + safeChunkSize);
            chunks.add(normalized.substring(start, end));
            if (end >= normalized.length()) {
                break;
            }
            start = end - safeOverlap;
        }
        return chunks;
    }

    private String fileType(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    private static class Counter {
        int scannedFiles;
        int parsedDocuments;
        int skippedDocuments;
        int failedDocuments;
        int chunksCreated;
        int chunksIndexed;
    }
}
