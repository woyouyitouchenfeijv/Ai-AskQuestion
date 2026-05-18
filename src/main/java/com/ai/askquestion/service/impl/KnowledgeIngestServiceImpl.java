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
import org.apache.tika.Tika;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestServiceImpl implements KnowledgeIngestService {
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final QuestionRecordMapper questionRecordMapper;
    private final ElasticsearchIndexService elasticsearchIndexService;

    private final ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
    private final Tika tika = new Tika();

    @Override
    @Transactional
    public IngestResponse ingest(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            throw new IllegalArgumentException("knowledgeBaseId 不能为空");
        }

        List<SourceFile> sourceFiles = resolveSourceFiles();
        if (sourceFiles.isEmpty()) {
            String message = "未发现可入库文件，请检查 knowledge.base-path 配置";
            log.warn("{}，knowledgeBaseId={}, basePath={}", message, knowledgeBaseId, knowledgeProperties.getBasePath());
            return new IngestResponse(knowledgeBaseId, 0, 0, 0, 0, 0, 0, message);
        }

        Counter counter = new Counter();
        for (SourceFile sourceFile : sourceFiles) {
            ingestOne(knowledgeBaseId, sourceFile, counter);
        }

        return new IngestResponse(knowledgeBaseId, counter.scannedFiles, counter.parsedDocuments,
                counter.skippedDocuments, counter.failedDocuments, counter.chunksCreated, counter.chunksIndexed,
                String.format("入库完成：扫描%d个文件，解析%d个，跳过%d个，失败%d个，创建%d个chunk，索引%d个chunk",
                        counter.scannedFiles, counter.parsedDocuments, counter.skippedDocuments,
                        counter.failedDocuments, counter.chunksCreated, counter.chunksIndexed));
    }

    private List<SourceFile> resolveSourceFiles() {
        String basePathConfig = knowledgeProperties.getBasePath();
        Map<String, SourceFile> uniqueFiles = new LinkedHashMap<>();

        try {
            if (StringUtils.hasText(basePathConfig) && basePathConfig.startsWith("classpath:")) {
                String classpathRoot = trimClasspathPrefix(basePathConfig);
                for (SourceFile sourceFile : readClasspathFiles(classpathRoot)) {
                    uniqueFiles.put(sourceFile.relativePath(), sourceFile);
                }
            } else if (StringUtils.hasText(basePathConfig)) {
                Path basePath = Paths.get(basePathConfig).toAbsolutePath().normalize();
                if (Files.exists(basePath) && Files.isDirectory(basePath)) {
                    for (SourceFile sourceFile : readFileSystemFiles(basePath)) {
                        uniqueFiles.put(sourceFile.relativePath(), sourceFile);
                    }
                } else {
                    log.warn("知识库目录不存在，尝试回退到 classpath:knowledge-base，basePath={}", basePath);
                }
            }

            // 回退：当文件系统目录不可用时，自动尝试 resources/knowledge-base
            if (uniqueFiles.isEmpty()) {
                for (SourceFile sourceFile : readClasspathFiles("knowledge-base")) {
                    uniqueFiles.put(sourceFile.relativePath(), sourceFile);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取知识库文件失败，basePath=" + basePathConfig, e);
        }

        return new ArrayList<>(uniqueFiles.values());
    }

    private List<SourceFile> readFileSystemFiles(Path basePath) throws IOException {
        List<SourceFile> files = new ArrayList<>();
        try (var stream = Files.walk(basePath)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .forEach(path -> {
                        try {
                            String relativePath = basePath.relativize(path.toAbsolutePath().normalize()).toString();
                            files.add(new SourceFile(path.getFileName().toString(), normalizeRelativePath(relativePath), Files.readAllBytes(path)));
                        } catch (IOException e) {
                            throw new IllegalStateException("读取文件失败: " + path, e);
                        }
                    });
        }
        return files;
    }

    private List<SourceFile> readClasspathFiles(String classpathRoot) throws IOException {
        String normalizedRoot = normalizeClassPathRoot(classpathRoot);
        String pattern = "classpath*:" + normalizedRoot + "/**/*";
        Resource[] resources = resourcePatternResolver.getResources(pattern);
        List<SourceFile> files = new ArrayList<>();
        for (Resource resource : resources) {
            if (!resource.isReadable() || resource.getFilename() == null || resource.getFilename().startsWith(".")) {
                continue;
            }
            try {
                byte[] bytes = resource.getInputStream().readAllBytes();
                if (bytes.length == 0) {
                    continue;
                }
                String relativePath = extractRelativePath(resource, normalizedRoot);
                files.add(new SourceFile(resource.getFilename(), normalizeRelativePath(relativePath), bytes));
            } catch (Exception e) {
                log.warn("跳过无法读取的 classpath 文件: {}", resource.getDescription(), e);
            }
        }
        return files;
    }

    private String extractRelativePath(Resource resource, String classpathRoot) throws IOException {
        String marker = normalizeClassPathRoot(classpathRoot) + "/";
        String url = resource.getURL().toString();
        int idx = url.indexOf(marker);
        if (idx >= 0) {
            return url.substring(idx + marker.length());
        }
        return resource.getFilename();
    }

    private String trimClasspathPrefix(String path) {
        String value = path.substring("classpath:".length());
        return normalizeClassPathRoot(value);
    }

    private String normalizeClassPathRoot(String root) {
        String value = root == null ? "knowledge-base" : root.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isBlank() ? "knowledge-base" : value;
    }

    private String normalizeRelativePath(String relativePath) {
        return relativePath.replace('\\', '/');
    }

    private void ingestOne(Long knowledgeBaseId, SourceFile sourceFile, Counter counter) {
        counter.scannedFiles++;
        String relativePath = sourceFile.relativePath();
        try {
            String fileHash = sha256(sourceFile.bytes());
            KnowledgeDocument existing = knowledgeDocumentMapper.findByKnowledgeBaseIdAndFilePath(knowledgeBaseId, relativePath);
            if (existing != null && fileHash.equals(existing.getFileHash()) && "PARSED".equals(existing.getParseStatus())) {
                counter.skippedDocuments++;
                return;
            }

            String content = readText(sourceFile);
            if (content.isBlank()) {
                counter.skippedDocuments++;
                return;
            }

            KnowledgeDocument document = existing == null ? new KnowledgeDocument() : existing;
            document.setKnowledgeBaseId(knowledgeBaseId);
            document.setFileName(sourceFile.fileName());
            document.setFilePath(relativePath);
            document.setFileType(fileType(sourceFile.fileName()));
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
            log.error("Ingest file failed, relativePath={}", relativePath, e);
            KnowledgeDocument existing = knowledgeDocumentMapper.findByKnowledgeBaseIdAndFilePath(knowledgeBaseId, relativePath);
            if (existing != null) {
                knowledgeDocumentMapper.updateParseFailed(existing.getId(), e.getMessage());
            }
        }
    }

    private String readText(SourceFile sourceFile) {
        try {
            String text = tika.parseToString(new ByteArrayInputStream(sourceFile.bytes()));
            return text == null ? "" : text.trim();
        } catch (Exception tikaException) {
            log.warn("Tika 解析失败，回退 UTF-8 文本读取, file={}", sourceFile.relativePath(), tikaException);
            return new String(sourceFile.bytes(), StandardCharsets.UTF_8).trim();
        }
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

    private String fileType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    private record SourceFile(String fileName, String relativePath, byte[] bytes) {
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
