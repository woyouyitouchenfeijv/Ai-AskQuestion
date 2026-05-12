CREATE DATABASE IF NOT EXISTS ai_askquestion DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE ai_askquestion;

CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '知识库名称',
    description VARCHAR(512) DEFAULT NULL COMMENT '知识库描述',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库';

CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_base_id BIGINT NOT NULL COMMENT '知识库ID',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_path VARCHAR(1024) NOT NULL COMMENT '文件路径',
    file_type VARCHAR(64) DEFAULT NULL COMMENT '文件类型',
    file_hash VARCHAR(128) NOT NULL COMMENT '文件hash，用于判断是否变更',
    parse_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PARSED/FAILED',
    error_message TEXT DEFAULT NULL COMMENT '解析失败原因',
    version INT NOT NULL DEFAULT 1 COMMENT '文档版本',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (knowledge_base_id),
    INDEX idx_file_hash (file_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_base_id BIGINT NOT NULL COMMENT '知识库ID',
    document_id BIGINT NOT NULL COMMENT '文档ID',
    chunk_index INT NOT NULL COMMENT '切片序号',
    content MEDIUMTEXT NOT NULL COMMENT '切片内容',
    content_hash VARCHAR(128) NOT NULL COMMENT '切片hash',
    es_doc_id VARCHAR(128) DEFAULT NULL COMMENT 'ES中的文档ID',
    token_count INT DEFAULT NULL,
    version INT NOT NULL DEFAULT 1 COMMENT '版本',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (knowledge_base_id),
    INDEX idx_document_id (document_id),
    INDEX idx_content_hash (content_hash),
    INDEX idx_es_doc_id (es_doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识切片';

DROP TABLE IF EXISTS question_record;

CREATE TABLE IF NOT EXISTS question_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_base_id BIGINT DEFAULT NULL COMMENT '知识库ID',
    question TEXT NOT NULL COMMENT '用户原始问题',
    normalized_question VARCHAR(1024) DEFAULT NULL COMMENT '标准化问题',
    question_hash VARCHAR(128) DEFAULT NULL COMMENT '标准化问题hash',
    answer MEDIUMTEXT NOT NULL COMMENT '答案',
    answer_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/VERIFIED/STALE/DISABLED',
    source_type VARCHAR(32) NOT NULL COMMENT 'CACHE/RAG/LLM/MANUAL',
    hit_type VARCHAR(32) DEFAULT NULL COMMENT 'EXACT/SIMILAR/RAG/NONE',
    similarity_score DECIMAL(6,4) DEFAULT NULL COMMENT '相似度',
    source_chunk_ids VARCHAR(1024) DEFAULT NULL COMMENT '引用的chunk id，逗号分隔',
    source_document_ids VARCHAR(1024) DEFAULT NULL COMMENT '引用的document id，逗号分隔',
    es_question_id VARCHAR(128) DEFAULT NULL COMMENT 'ES中的历史问题索引ID',
    model_name VARCHAR(128) DEFAULT NULL COMMENT '使用的模型',
    prompt_tokens INT DEFAULT NULL,
    completion_tokens INT DEFAULT NULL,
    total_tokens INT DEFAULT NULL,
    cost_time_ms BIGINT DEFAULT NULL,
    hit_count INT NOT NULL DEFAULT 0 COMMENT '被复用次数',
    useful_count INT NOT NULL DEFAULT 0 COMMENT '有用反馈数',
    useless_count INT NOT NULL DEFAULT 0 COMMENT '无用反馈数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_question_hash (question_hash),
    INDEX idx_kb_question_hash (knowledge_base_id, question_hash),
    INDEX idx_answer_status (answer_status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答记录';

CREATE TABLE IF NOT EXISTS question_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_record_id BIGINT NOT NULL COMMENT '问答记录ID',
    feedback_type VARCHAR(32) NOT NULL COMMENT 'USEFUL/USELESS/CORRECTED',
    corrected_answer MEDIUMTEXT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_question_record_id (question_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答反馈';
