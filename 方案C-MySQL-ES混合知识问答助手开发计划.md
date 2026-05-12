# 方案 C：MySQL + Elasticsearch 混合知识问答助手开发计划

## 1. 项目现状

当前项目路径：

```text
/Users/dengxu/Desktop/代码/Ai-AskQuestion
```

当前技术栈：

```text
Spring Boot 3.2
Java 17
LangChain4j 0.36.2
MyBatis
MySQL
本地 knowledge-base 文档目录
```

当前已实现能力：

```text
1. 普通问答接口：直接调用大模型。
2. RAG 问答接口：从本地 knowledge-base 加载文档，然后通过 LangChain4j RAG 回答。
3. 问答记录保存到 MySQL question_record 表。
```

当前主要问题：

```text
1. 现在使用 InMemoryEmbeddingStore，重启后向量数据会丢失。
2. 启动时自动加载文档，后期文档多了会影响启动速度。
3. MySQL 只有 question_record 一张简单记录表，无法承担知识库管理和问答缓存职责。
4. 文档、附件、代码文件没有统一的元数据管理。
5. 没有 Elasticsearch，无法做稳定的全文检索和后续向量检索。
6. 相同问题或类似问题无法可靠复用历史答案。
7. AI 生成答案没有确认、修正、废弃、过期机制。
```

---

## 2. 最终目标

最终目标是做成一个自己的知识问答助手，支持：

```text
1. 附件、文档、代码作为知识库内容。
2. 使用 Elasticsearch 对知识库内容建立索引。
3. 使用 MySQL 管理知识库、文档、切片、问答历史、答案状态。
4. 相同问题优先从 MySQL 历史答案中命中。
5. 类似问题通过 ES 向量检索或相似检索找到历史答案。
6. 缓存未命中时，再走知识库检索和大模型生成。
7. 尽可能减少大模型调用次数。
8. 支持人工确认、修正、废弃答案。
9. 支持文档更新后，把相关旧答案标记为可能过期。
```

---

## 3. 推荐架构

整体架构：

```text
用户提问
  ↓
问题标准化
  ↓
MySQL 精确问题缓存
  ↓
ES 相似历史问题检索
  ↓
如果命中可靠答案，直接返回
  ↓
如果未命中，查 ES 知识库索引
  ↓
召回相关知识片段
  ↓
调用大模型生成答案
  ↓
保存问答记录、来源、状态、耗时
  ↓
写入历史问题索引
  ↓
返回用户
```

职责划分：

```text
MySQL：
- 知识库元数据
- 文档信息
- 文档切片信息
- 问答历史
- 答案状态
- 用户反馈
- 模型调用日志

Elasticsearch：
- 文档全文检索
- 文档向量检索
- 历史问题相似检索

大模型：
- 只在缓存未命中、需要基于知识库总结答案时调用
```

---

## 4. 核心流程设计

### 4.1 第一版 MVP 流程

第一版先实现稳定主链路：

```text
用户问题
  ↓
normalize + question_hash
  ↓
MySQL 查 question_hash + VERIFIED 答案
  ↓
命中：直接返回
  ↓
未命中：ES 搜 knowledge_chunk_index
  ↓
拼接 prompt
  ↓
调用大模型
  ↓
保存 question_record，状态 DRAFT
  ↓
返回答案
  ↓
用户确认 verify
  ↓
下次相同问题直接缓存命中
```

### 4.2 第二版增强流程

第二版再增加类似问题匹配：

```text
用户问题
  ↓
MySQL 精确命中
  ↓
ES 历史问题向量相似命中
  ↓
ES 知识库全文 + 向量混合检索
  ↓
大模型生成
```

---

## 5. 数据库设计

修改文件：

```text
src/main/resources/sql/mysql-schema.sql
```

### 5.1 知识库表

```sql
CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '知识库名称',
    description VARCHAR(512) DEFAULT NULL COMMENT '知识库描述',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库';
```

用途：

```text
支持多个知识库，例如：
- 项目代码知识库
- 公司文档知识库
- 个人资料知识库
- 育儿知识库
- AI 自媒体素材知识库
```

### 5.2 文档表

```sql
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
```

### 5.3 文档切片表

```sql
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_base_id BIGINT NOT NULL COMMENT '知识库ID',
    document_id BIGINT NOT NULL COMMENT '文档ID',
    chunk_index INT NOT NULL COMMENT '切片序号',
    content MEDIUMTEXT NOT NULL COMMENT '切片内容',
    content_hash VARCHAR(128) NOT NULL COMMENT '切片hash',
    es_doc_id VARCHAR(128) DEFAULT NULL COMMENT 'ES中的文档ID',
    token_count INT DEFAULT NULL COMMENT 'token数量',
    version INT NOT NULL DEFAULT 1 COMMENT '版本',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (knowledge_base_id),
    INDEX idx_document_id (document_id),
    INDEX idx_content_hash (content_hash),
    INDEX idx_es_doc_id (es_doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识切片';
```

### 5.4 问答记录表

建议重构当前 `question_record`：

```sql
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
```

答案状态说明：

```text
DRAFT：AI 生成但未确认
VERIFIED：已确认，可直接复用
STALE：知识更新后可能过期
DISABLED：废弃，不再使用
```

来源类型说明：

```text
CACHE：直接命中缓存
RAG：检索知识库后生成
LLM：直接问大模型
MANUAL：人工维护答案
```

命中类型说明：

```text
EXACT：完全命中
SIMILAR：相似问题命中
RAG：走知识库检索
NONE：无命中
```

### 5.5 问答反馈表

```sql
CREATE TABLE IF NOT EXISTS question_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_record_id BIGINT NOT NULL COMMENT '问答记录ID',
    feedback_type VARCHAR(32) NOT NULL COMMENT 'USEFUL/USELESS/CORRECTED',
    corrected_answer MEDIUMTEXT DEFAULT NULL COMMENT '修正后的答案',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_question_record_id (question_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答反馈';
```

---

## 6. Elasticsearch 设计

建议使用两个索引：

```text
knowledge_chunk_index
qa_question_index
```

### 6.1 knowledge_chunk_index

用途：知识库内容检索。

字段建议：

```json
{
  "knowledge_base_id": "long",
  "document_id": "long",
  "chunk_id": "long",
  "file_name": "text",
  "file_path": "keyword",
  "content": "text",
  "content_vector": "dense_vector",
  "version": "integer",
  "created_at": "date"
}
```

### 6.2 qa_question_index

用途：历史问题相似检索。

字段建议：

```json
{
  "question_record_id": "long",
  "knowledge_base_id": "long",
  "question": "text",
  "normalized_question": "text",
  "question_vector": "dense_vector",
  "answer_status": "keyword",
  "created_at": "date"
}
```

---

## 7. 配置文件调整

修改文件：

```text
src/main/resources/application.yml
```

建议增加：

```yaml
spring:
  application:
    name: ai-askquestion
  datasource:
    url: jdbc:mysql://localhost:3306/ai_askquestion?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
  elasticsearch:
    uris: http://localhost:9200

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.ai.askquestion.domain

knowledge:
  base-path: src/main/resources/knowledge-base
  chunk-size: 500
  chunk-overlap: 100
  retrieve:
    max-results: 5
    min-score: 0.60

qa:
  cache:
    exact-enabled: true
    similar-enabled: true
    exact-threshold: 0.98
    verified-similar-threshold: 0.88
    reference-similar-threshold: 0.75
```

---

## 8. Maven 依赖调整

修改文件：

```text
pom.xml
```

增加 Elasticsearch 依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
</dependency>
```

---

## 9. 开发任务清单

### Task 1：扩展数据库表结构

修改文件：

```text
src/main/resources/sql/mysql-schema.sql
```

完成内容：

```text
1. 新增 knowledge_base。
2. 新增 knowledge_document。
3. 新增 knowledge_chunk。
4. 重构 question_record。
5. 新增 question_feedback。
```

验收标准：

```text
MySQL 执行 schema 后，可以看到 5 张核心表：
- knowledge_base
- knowledge_document
- knowledge_chunk
- question_record
- question_feedback
```

---

### Task 2：补全 MyBatis 基础能力

新增或修改：

```text
src/main/java/com/ai/askquestion/domain/KnowledgeBase.java
src/main/java/com/ai/askquestion/domain/KnowledgeDocument.java
src/main/java/com/ai/askquestion/domain/KnowledgeChunk.java
src/main/java/com/ai/askquestion/domain/QuestionFeedback.java

src/main/java/com/ai/askquestion/mapper/KnowledgeBaseMapper.java
src/main/java/com/ai/askquestion/mapper/KnowledgeDocumentMapper.java
src/main/java/com/ai/askquestion/mapper/KnowledgeChunkMapper.java
src/main/java/com/ai/askquestion/mapper/QuestionFeedbackMapper.java

src/main/resources/mapper/KnowledgeBaseMapper.xml
src/main/resources/mapper/KnowledgeDocumentMapper.xml
src/main/resources/mapper/KnowledgeChunkMapper.xml
src/main/resources/mapper/QuestionFeedbackMapper.xml
```

扩展：

```text
QuestionRecord.java
QuestionRecordMapper.java
QuestionRecordMapper.xml
```

需要增加方法：

```text
insert
updateStatus
updateAnswer
increaseHitCount
findById
findVerifiedByQuestionHash
findByIds
markStaleByDocumentId
```

验收标准：

```text
项目能启动，Mapper 调用不报错。
```

---

### Task 3：接入 Elasticsearch

修改：

```text
pom.xml
application.yml
```

新增：

```text
src/main/java/com/ai/askquestion/config/ElasticsearchConfig.java
src/main/java/com/ai/askquestion/service/EsIndexService.java
src/main/java/com/ai/askquestion/service/impl/EsIndexServiceImpl.java
```

职责：

```text
1. 初始化 knowledge_chunk_index。
2. 初始化 qa_question_index。
3. 判断索引是否存在。
4. 不存在则创建。
```

验收标准：

```text
启动项目后，ES 中存在：
- knowledge_chunk_index
- qa_question_index
```

---

### Task 4：实现问题标准化

新增：

```text
src/main/java/com/ai/askquestion/service/QuestionNormalizeService.java
src/main/java/com/ai/askquestion/service/impl/QuestionNormalizeServiceImpl.java
src/main/java/com/ai/askquestion/dto/NormalizedQuestion.java
```

功能：

```text
输入：这个项目 怎么 启动？
输出：
- originalQuestion：原始问题
- normalizedQuestion：这个项目怎么启动
- questionHash：sha256(normalizedQuestion)
```

验收标准：

```text
空格、标点略有不同但语义相同的问题，可以生成相同 hash。
```

---

### Task 5：实现知识库文档入库

新增：

```text
src/main/java/com/ai/askquestion/service/KnowledgeIngestService.java
src/main/java/com/ai/askquestion/service/impl/KnowledgeIngestServiceImpl.java
src/main/java/com/ai/askquestion/controller/KnowledgeController.java
```

接口：

```text
POST /api/knowledge/{knowledgeBaseId}/ingest
```

流程：

```text
1. 扫描 knowledge.base-path。
2. 计算文件 hash。
3. 判断文件是否变更。
4. Apache Tika 解析内容。
5. 按 chunk-size 切片。
6. 存 MySQL knowledge_document。
7. 存 MySQL knowledge_chunk。
8. 写 ES knowledge_chunk_index。
```

验收标准：

```text
调用入库接口后：
- MySQL knowledge_document 有数据。
- MySQL knowledge_chunk 有数据。
- ES knowledge_chunk_index 有数据。
```

---

### Task 6：实现知识库检索

新增：

```text
src/main/java/com/ai/askquestion/service/KnowledgeSearchService.java
src/main/java/com/ai/askquestion/service/impl/KnowledgeSearchServiceImpl.java
src/main/java/com/ai/askquestion/dto/KnowledgeChunkHit.java
```

第一版：

```text
根据 question 检索 ES knowledge_chunk_index.content，返回 top 5 chunk。
```

第二版：

```text
全文检索 + 向量检索混合召回。
```

验收标准：

```text
输入“公司简介是什么？”，可以从 knowledge-base/公司简介.txt 中召回相关 chunk。
```

---

### Task 7：实现历史问题缓存服务

新增：

```text
src/main/java/com/ai/askquestion/service/QuestionCacheService.java
src/main/java/com/ai/askquestion/service/impl/QuestionCacheServiceImpl.java
```

职责：

```text
1. 先查 MySQL 精确命中。
2. 再查 ES 相似历史问题。
3. 判断是否能直接返回。
4. 问答生成后，把问题写入 ES qa_question_index。
```

第一版先实现：

```text
MySQL question_hash 精确命中。
```

第二版再实现：

```text
ES 向量相似问题命中。
```

验收标准：

```text
同一个问题第一次走 RAG + LLM。
确认为 VERIFIED 后，第二次同样问题直接返回缓存答案，不调用大模型。
```

---

### Task 8：改造 AiQuestionServiceImpl 主流程

修改文件：

```text
src/main/java/com/ai/askquestion/service/impl/AiQuestionServiceImpl.java
```

改成：

```text
1. 标准化问题。
2. 查缓存。
3. 缓存命中直接返回。
4. 缓存未命中，查 ES 知识库。
5. 构建 prompt。
6. 调用大模型。
7. 保存 question_record。
8. 写入 qa_question_index。
9. 返回结果。
```

伪代码：

```java
public AskQuestionResponse askQuestionRag(AskQuestionRequest request) {
    long start = System.currentTimeMillis();

    String question = request.getQuestion();
    Long knowledgeBaseId = request.getKnowledgeBaseId();

    NormalizedQuestion normalized = questionNormalizeService.normalize(question);

    Optional<QuestionRecord> cacheHit = questionCacheService.findUsableAnswer(
        knowledgeBaseId,
        question,
        normalized
    );

    if (cacheHit.isPresent()) {
        QuestionRecord record = cacheHit.get();
        questionRecordMapper.increaseHitCount(record.getId());
        return AskQuestionResponse.of(question, record.getAnswer());
    }

    List<KnowledgeChunkHit> chunks = knowledgeSearchService.search(
        knowledgeBaseId,
        question,
        5
    );

    String prompt = promptBuilder.build(question, chunks);

    String answer = chatLanguageModel.generate(prompt);

    QuestionRecord record = saveQuestionRecord(
        knowledgeBaseId,
        question,
        normalized,
        answer,
        chunks,
        "RAG",
        System.currentTimeMillis() - start
    );

    questionCacheService.indexQuestion(record);

    return AskQuestionResponse.of(question, answer);
}
```

验收标准：

```text
/api/ai/rag/ask 可以完整跑通新流程。
```

---

### Task 9：扩展返回对象

修改文件：

```text
src/main/java/com/ai/askquestion/dto/AskQuestionResponse.java
```

建议字段：

```java
private Long recordId;
private String question;
private String answer;
private String sourceType;
private String hitType;
private BigDecimal similarityScore;
private List<Long> sourceChunkIds;
private List<Long> sourceDocumentIds;
private Long costTimeMs;
```

作用：

```text
前端或调用方可以知道：
- 本次是缓存命中还是调用大模型。
- 引用了哪些知识片段。
- 耗时多少。
- recordId 是多少，方便后续确认或修正。
```

---

### Task 10：增加答案确认、废弃、修正接口

修改或新增到：

```text
AiQuestionController.java
```

接口：

```text
POST /api/ai/question/{recordId}/verify
POST /api/ai/question/{recordId}/disable
POST /api/ai/question/{recordId}/correct
```

新增 DTO：

```text
src/main/java/com/ai/askquestion/dto/CorrectAnswerRequest.java
```

逻辑：

```text
verify：
- answer_status = VERIFIED

disable：
- answer_status = DISABLED

correct：
- 保存 correctedAnswer
- answer_status 改成 VERIFIED
- 写 question_feedback
- 更新 ES qa_question_index
```

验收标准：

```text
AI 第一次生成答案后是 DRAFT。
调用 verify 后变成 VERIFIED。
同样问题再次提问，直接命中缓存。
```

---

### Task 11：文档更新后标记答案过期

当文档或代码文件变更后，旧答案可能失效。

处理逻辑：

```text
1. 文档重新解析。
2. 原 chunk 发生变化。
3. 找到引用这些 chunk 或 document 的 question_record。
4. 把 answer_status 改成 STALE。
```

第一版可以按 document_id 粗粒度处理：

```sql
UPDATE question_record
SET answer_status = 'STALE'
WHERE FIND_IN_SET(#{documentId}, source_document_ids);
```

规则：

```text
只有 VERIFIED 可以直接返回。
DRAFT 不直接返回。
STALE 不直接返回。
DISABLED 不直接返回。
```

---

## 10. 推荐开发顺序

建议严格按这个顺序做：

```text
第 1 步：改 MySQL 表结构。
第 2 步：补全 domain + mapper。
第 3 步：接 ES，先能创建索引。
第 4 步：做问题标准化 + hash。
第 5 步：做知识库入库：文件 → chunk → MySQL → ES。
第 6 步：做 ES 知识库检索。
第 7 步：改造问答流程：缓存未命中 → ES 检索 → 大模型。
第 8 步：做 MySQL 精确缓存命中。
第 9 步：做答案 verify/correct/disable。
第 10 步：做 ES 相似问题命中。
第 11 步：做文档更新后答案 STALE。
第 12 步：补日志、异常处理、接口返回字段。
```

---

## 11. 第一版 MVP 范围

第一版建议只做必要能力。

### 11.1 MVP 必做

```text
1. MySQL 保存知识库、文档、chunk、问答记录。
2. ES 保存 chunk 并支持全文检索。
3. 用户提问先查 MySQL question_hash。
4. 命中 VERIFIED 直接返回。
5. 未命中则查 ES chunk。
6. 调用大模型生成答案。
7. 保存问答记录为 DRAFT。
8. 用户可以 verify 答案。
9. verify 后，同样问题不再调用大模型。
```

### 11.2 MVP 暂缓

```text
1. ES 向量检索。
2. 历史问题语义相似命中。
3. 多知识库权限。
4. 前端页面。
5. token 计费统计。
6. 文档版本复杂对比。
7. 自动摘要。
```

---

## 12. 当前项目最优先修改的 3 个点

### 12.1 不要继续用 InMemoryEmbeddingStore

当前代码：

```java
return new InMemoryEmbeddingStore<>();
```

问题：

```text
重启后数据丢失，不适合正式知识库。
```

建议：

```text
文档 chunk 存 MySQL。
检索索引存 ES。
```

### 12.2 不要启动时自动加载所有文档

当前代码：

```java
@Bean
public Boolean loadDocuments(...)
```

问题：

```text
后期知识库大了，启动会很慢，而且不方便控制重建索引。
```

建议：

```text
改成手动接口触发：POST /api/knowledge/{knowledgeBaseId}/ingest
```

### 12.3 question_record 要升级为问答资产表

当前 `question_record` 只是日志记录。

后面应该承担：

```text
1. 缓存命中。
2. 答案复用。
3. 人工确认。
4. 答案修正。
5. 相似问题召回。
6. 减少大模型调用。
```

---

## 13. 结论

推荐采用方案 C：

```text
MySQL + Elasticsearch 混合方案
```

具体理解为：

```text
MySQL 负责存业务数据、问答资产、状态、反馈。
ES 负责知识库检索和历史问题相似检索。
大模型只负责缓存未命中后的答案生成。
```

第一阶段不要追求一次性完成所有高级功能，先完成：

```text
MySQL 精确缓存命中 + ES 文档全文检索 + 大模型生成 + 答案确认复用
```

这条链路跑通后，再逐步增加：

```text
向量检索、相似问题命中、答案过期、反馈排序、多知识库权限。
```
