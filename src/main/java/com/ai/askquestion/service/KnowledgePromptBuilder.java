package com.ai.askquestion.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class KnowledgePromptBuilder {

    public String build(String question, List<KnowledgeChunkSearchResult> chunks) {
        String context = chunks.stream()
                .map(chunk -> String.format("[chunk_id=%s, document_id=%s]\n%s",
                        chunk.getChunkId(),
                        chunk.getDocumentId(),
                        chunk.getContent()))
                .collect(Collectors.joining("\n\n---\n\n"));

        if (context.isBlank()) {
            return "请回答用户问题。如果知识库没有相关内容，请明确说明当前知识库暂无相关资料，不要编造。\n\n用户问题：" + question;
        }

        return "你是企业知识库问答助手。请严格基于【知识库片段】回答用户问题。\n"
                + "要求：\n"
                + "1. 只使用知识库片段中的信息，不要编造。\n"
                + "2. 如果片段不足以回答，请说明知识库资料不足。\n"
                + "3. 回答要简洁、直接、可执行。\n\n"
                + "【知识库片段】\n"
                + context
                + "\n\n【用户问题】\n"
                + question;
    }
}
