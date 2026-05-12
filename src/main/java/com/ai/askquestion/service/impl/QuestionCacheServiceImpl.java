package com.ai.askquestion.service.impl;

import com.ai.askquestion.domain.QuestionRecord;
import com.ai.askquestion.dto.NormalizedQuestion;
import com.ai.askquestion.mapper.QuestionRecordMapper;
import com.ai.askquestion.service.ElasticsearchIndexService;
import com.ai.askquestion.service.QuestionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionCacheServiceImpl implements QuestionCacheService {
    private final QuestionRecordMapper questionRecordMapper;
    private final ElasticsearchIndexService elasticsearchIndexService;

    @Override
    public Optional<QuestionRecord> findUsableAnswer(Long knowledgeBaseId, String question, NormalizedQuestion normalizedQuestion) {
        QuestionRecord exact = questionRecordMapper.findVerifiedByQuestionHash(knowledgeBaseId, normalizedQuestion.getQuestionHash());
        return Optional.ofNullable(exact);
    }

    @Override
    public void indexQuestion(QuestionRecord record) {
        String esQuestionId = elasticsearchIndexService.indexQuestion(record);
        log.debug("Indexed question record to ES, recordId={}, esQuestionId={}", record == null ? null : record.getId(), esQuestionId);
    }
}
