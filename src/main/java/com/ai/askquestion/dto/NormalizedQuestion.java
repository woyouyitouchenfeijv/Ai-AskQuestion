package com.ai.askquestion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedQuestion {
    private String originalQuestion;
    private String normalizedQuestion;
    private String questionHash;
}
