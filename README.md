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
java -jar target/ai-askquestion-java-1.0.0.jar
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
