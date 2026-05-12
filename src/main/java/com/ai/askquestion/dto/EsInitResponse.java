package com.ai.askquestion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EsInitResponse {

    private String indexName;

    private boolean indexCreated;

    private int totalChunks;

    private int successCount;

    private int failCount;

    private String message;

    public static EsInitResponse indexOnly(String indexName, boolean indexCreated, String message) {
        return new EsInitResponse(indexName, indexCreated, 0, 0, 0, message);
    }
}
