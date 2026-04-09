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

}
