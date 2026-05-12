package com.ai.askquestion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ask Question Request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AskQuestionRequest {

    private String question;

    /**
     * 可选：指定知识库 ID；为空时在全局范围查询。
     */
    private Long knowledgeBaseId;

    public AskQuestionRequest(String question) {
        this.question = question;
    }
}
