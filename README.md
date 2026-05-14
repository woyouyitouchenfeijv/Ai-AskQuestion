# Ai-AskQuestion-Java

一个基于 Spring Boot + LangChain4j 的 AI 问答系统，解决新入职员工的提问，让新员工快速开始工作。

## 技术栈

- **Spring Boot 3.2.0** - Web 框架
- **LangChain4j 0.36.2** - AI 大模型集成框架
- **OpenAI API** - 大语言模型
- **Lombok** - 简化 Java 代码
- **Maven** - 项目构建工具

## 项目结构

```
Ai-AskQuestion-Java/
├── src/
│   ├── main/
│   │   ├── java/com/ai/askquestion/
│   │   │   ├── AiAskQuestionApplication.java    # 启动类
│   │   │   ├── config/                          # 配置类
│   │   │   │   └── LangChain4jConfig.java       # LangChain4j 配置
│   │   │   ├── controller/                      # 控制器
│   │   │   │   └── AiQuestionController.java    # API 接口
│   │   │   ├── service/                         # 服务层
│   │   │   │   └── AiQuestionService.java       # AI 服务
│   │   │   └── dto/                             # 数据传输对象
│   │   │       ├── AskQuestionRequest.java      # 请求 DTO
│   │   │       └── AskQuestionResponse.java     # 响应 DTO
│   │   └── resources/
│   │       └── application.yml                   # 配置文件
│   └── test/
│       └── java/com/ai/askquestion/
├── pom.xml                                       # Maven 配置
└── README.md                                     # 项目说明
```

## 快速开始

### 1. 配置环境变量

编辑 `application.yml` 或设置环境变量：

```yaml
ai:
  openai:
    api-key: your-openai-api-key-here
```

或通过环境变量：

```bash
export OPENAI_API_KEY=your-openai-api-key-here
```

### 2. 启动项目

```bash
mvn spring-boot:run
```

或先编译再运行：

```bash
mvn clean package
java -jar target/ai-askquestion-1.0.0.jar
```

### 2.1 存储模式切换（已支持）

- `memory`（默认，测试/开发）：H2 内存库 + ES 关闭
- `prod`（生产）：MySQL + ES

切换方式：

```bash
# 默认就是 memory，可显式指定
SPRING_PROFILES_ACTIVE=memory mvn spring-boot:run

# 生产模式（需提前准备 MySQL、ES）
SPRING_PROFILES_ACTIVE=prod \
MYSQL_URL='jdbc:mysql://127.0.0.1:3306/ai_askquestion?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true' \
MYSQL_USERNAME='root' \
MYSQL_PASSWORD='123456' \
APP_ELASTICSEARCH_BASE_URL='http://127.0.0.1:9200' \
mvn spring-boot:run
```

### 3. 测试接口

服务启动后访问 `http://localhost:8080`

#### 健康检查

```bash
curl http://localhost:8080/api/ai/health
```

#### 询问问题

```bash
curl -X POST http://localhost:8080/api/ai/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "什么是 Spring Boot？"}'
```

响应示例：

```json
{
  "question": "什么是 Spring Boot？",
  "answer": "Spring Boot 是一个基于 Spring 框架的快速开发工具，它通过约定优于配置的理念，大大简化了 Spring 应用的初始搭建和开发过程..."
}
```

## API 文档

### POST /api/ai/ask

向 AI 提问

**请求体：**

```json
{
  "question": "你的问题"
}
```

**响应：**

```json
{
  "question": "你的问题",
  "answer": "AI 的回答"
}
```

### POST /api/ai/rag/ask

4.1 MVP 主链路：

1. 标准化问题并生成 `question_hash`
2. MySQL 查询 `question_hash + VERIFIED` 答案
3. 命中后直接返回缓存答案，并增加 `hit_count`
4. 未命中时检索 ES `knowledge_chunk_index`
5. 拼接知识库 prompt 调用大模型
6. 保存 `question_record`，状态为 `DRAFT`
7. 返回答案和 `recordId`

**请求体：**

```json
{
  "question": "如何申请年假？",
  "knowledgeBaseId": 1
}
```

`knowledgeBaseId` 可不传；不传时在全局范围查询。

**响应：**

```json
{
  "question": "如何申请年假？",
  "answer": "根据制度，连续工作满一年后可申请年假。",
  "recordId": 123,
  "sourceType": "RAG",
  "hitType": "RAG",
  "answerStatus": "DRAFT",
  "cacheHit": false
}
```

### POST /api/ai/question-records/{id}/verify

人工确认答案。确认后，同一个标准化问题下次会命中 MySQL 缓存。

### POST /api/es/knowledge-chunk-index

创建 ES 知识切片索引 `knowledge_chunk_index`。

可选参数：

```text
recreate=false
```

示例：

```bash
curl -X POST "http://localhost:8080/api/es/knowledge-chunk-index?recreate=false"
```

如果 `recreate=true`，会先删除旧索引再重建，慎用。

### POST /api/es/knowledge-chunk-index/init

创建索引，并从 MySQL `knowledge_chunk` 表全量初始化数据到 ES。

**请求体：**

```json
{
  "knowledgeBaseId": 1,
  "recreate": false,
  "batchSize": 200
}
```

字段说明：

- `knowledgeBaseId`：可选；为空时初始化全部知识库的切片
- `recreate`：是否重建索引
- `batchSize`：每批写入 ES 的切片数量，默认 200

### GET /api/ai/health

健康检查接口

**响应：**

```
AI Service is running!
```

## 配置说明

### application.yml 配置项

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| server.port | 服务端口 | 8080 |
| ai.openai.api-key | OpenAI API Key | 需配置 |
| ai.openai.model | 使用的模型 | gpt-3.5-turbo |
| ai.openai.temperature | 温度参数 | 0.7 |
| ai.openai.max-tokens | 最大 token 数 | 1000 |
| app.elasticsearch.enabled | 是否启用 ES 知识库检索 | false |
| app.elasticsearch.base-url | ES 地址 | http://localhost:9200 |
| app.elasticsearch.knowledge-chunk-index | 知识切片索引名 | knowledge_chunk_index |
| app.elasticsearch.top-k | 每次召回切片数量 | 3 |

## 常见问题

### 1. API Key 配置问题

确保在 `application.yml` 中正确配置了 OpenAI API Key，或通过环境变量 `OPENAI_API_KEY` 设置。

### 2. 网络连接问题

由于 OpenAI API 需要访问海外服务，请确保网络可以正常访问。

### 3. Java 版本

本项目使用 Java 17，请确保安装了 JDK 17 或更高版本。

## 扩展功能

可以考虑添加以下功能：

- 支持 Conversation（对话历史）
- 添加 RAG（检索增强生成）
- 支持多种 AI 模型（如 Claude、本地模型）
- 添加用户认证
- 问答历史记录存储
- 流式输出

## RAG模式 
```md
项目启动
↓
Spring 扫描到 KnowledgeBaseConfig
↓
创建 embeddingModel
↓
创建 embeddingStore
↓
创建 embeddingStoreIngestor
↓
执行 loadDocuments
↓
知识库文本 → 切片 → 向量化 → 存入向量库
↓
创建 Assistant（绑定 retriever）
↓
接口请求 /api/ai/rag/ask
↓
AiQuestionServiceImpl.askQuestionRag()
↓
assistant.chat(question)
↓
retriever 用问题向量去 embeddingStore 检索相似片段
↓
把检索结果拼进 prompt
↓
ChatModel 生成答案
```
## License

MIT
