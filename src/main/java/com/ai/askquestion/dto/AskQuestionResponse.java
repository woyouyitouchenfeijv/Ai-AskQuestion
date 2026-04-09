package com.ai.askquestion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ask Question Response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AskQuestionResponse {

    private String question;

    private String answer;

    public static AskQuestionResponse of(String question, String answer) {
        return new AskQuestionResponse(question, answer);
    }

}
