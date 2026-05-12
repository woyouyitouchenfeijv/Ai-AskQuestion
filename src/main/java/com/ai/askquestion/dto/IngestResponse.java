package com.ai.askquestion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngestResponse {
    private Long knowledgeBaseId;
    private int scannedFiles;
    private int parsedDocuments;
    private int skippedDocuments;
    private int failedDocuments;
    private int chunksCreated;
    private int chunksIndexed;
    private String message;
}
