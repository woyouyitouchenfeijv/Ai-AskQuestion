CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_base_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1024) NOT NULL,
    file_type VARCHAR(64),
    file_hash VARCHAR(128) NOT NULL,
    parse_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kb_id_doc ON knowledge_document(knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_file_hash ON knowledge_document(file_hash);

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_base_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content CLOB NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    es_doc_id VARCHAR(128),
    token_count INT,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kb_id_chunk ON knowledge_chunk(knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_document_id ON knowledge_chunk(document_id);
CREATE INDEX IF NOT EXISTS idx_content_hash ON knowledge_chunk(content_hash);
CREATE INDEX IF NOT EXISTS idx_es_doc_id ON knowledge_chunk(es_doc_id);

CREATE TABLE IF NOT EXISTS question_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_base_id BIGINT,
    question CLOB NOT NULL,
    normalized_question VARCHAR(1024),
    question_hash VARCHAR(128),
    answer CLOB NOT NULL,
    answer_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    source_type VARCHAR(32) NOT NULL,
    hit_type VARCHAR(32),
    similarity_score DECIMAL(6,4),
    source_chunk_ids VARCHAR(1024),
    source_document_ids VARCHAR(1024),
    es_question_id VARCHAR(128),
    model_name VARCHAR(128),
    prompt_tokens INT,
    completion_tokens INT,
    total_tokens INT,
    cost_time_ms BIGINT,
    hit_count INT NOT NULL DEFAULT 0,
    useful_count INT NOT NULL DEFAULT 0,
    useless_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_question_hash ON question_record(question_hash);
CREATE INDEX IF NOT EXISTS idx_kb_question_hash ON question_record(knowledge_base_id, question_hash);
CREATE INDEX IF NOT EXISTS idx_answer_status ON question_record(answer_status);
CREATE INDEX IF NOT EXISTS idx_created_at ON question_record(created_at);

CREATE TABLE IF NOT EXISTS question_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_record_id BIGINT NOT NULL,
    feedback_type VARCHAR(32) NOT NULL,
    corrected_answer CLOB,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_question_record_id ON question_feedback(question_record_id);
