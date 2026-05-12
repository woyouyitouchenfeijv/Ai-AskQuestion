package com.ai.askquestion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EsInitRequest {

    /**
     * 可选：只初始化指定知识库。
     */
    private Long knowledgeBaseId;

    /**
     * 是否先删除已有索引再重建。
     */
    private boolean recreate = false;

    /**
     * 每批写入 ES 的 chunk 数量。
     */
    private Integer batchSize;
}
