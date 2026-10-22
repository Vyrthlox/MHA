# 心理健康助手（Mental Health Assistant）— 技术栈说明

本文档说明本项目使用的技术栈及版本信息。

## 一、总体架构

项目采用**前后端分离**架构：

- **前端**：`ai-vue/` — Vue 3 单页应用，通过 Vite 构建，Axios 调用后端 REST 接口。
- **后端**：`code/ai-springboot/` — Spring Boot 应用，对外提供 REST API，并接入大模型能力。
- **数据层**：MySQL 存储业务主数据，MongoDB 存储对话/日志等文档型数据，Redis 承担缓存与会话管理。

## 二、技术栈清单

| 分层 | 技术 | 版本 | 说明 |
| --- | --- | --- | --- |
| 前端框架 | Vue 3 | ^3.5.42 | 组合式 API（Composition API） |
| 构建工具 | Vite | ^8.3.0 | 开发服务器与打包 |
| UI 组件库 | Element Plus | ^2.14.6 | 含 `@element-plus/icons-vue` ^2.3.2 |
| 状态管理 | Pinia | ^4.0.3 | 全局状态管理 |
| 路由 | Vue Router | ^4.6.4 | 前端路由 |
| HTTP 客户端 | Axios | ^1.20.0 | 请求后端接口 |
| 样式 | Sass | ^1.97.2 | 样式预处理 |
| 富文本编辑器 | wangEditor | ^5.1.23 | `@wangeditor/editor-for-vue` ^5.1.12 |
| 后端语言 | Java | 17 | 见 `pom.xml` 中 `java.version` |
| 后端框架 | Spring Boot | 4.1.1 | `spring-boot-starter-parent` |
| AI 能力 | Spring AI | 2.0.1 | 聊天经 `spring-ai-starter-model-openai` 接入 OpenAI 兼容接口；Embedding 经 `spring-ai-starter-model-ollama` 调用本地 bge-m3 |
| 向量数据库 | Milvus | 2.6.23（Docker standalone） | 长期记忆向量检索，集合 `mha_long_term_memory`（1024 维、COSINE、HNSW） |
| Embedding 模型 | bge-m3 | Ollama 拉取 | 1024 维中文向量，解决 DeepSeek 无 embedding 接口的问题 |
| 关系型数据库 | MySQL | 8.4（Docker） | 库名 `MHAData`，存放账号、画像、记忆提炼任务与向量台账 |
| 文档型数据库 | MongoDB | 8.0（Docker） | 存储会话与完整消息日志 |
| 缓存/会话 | Redis | 7（Docker） | 注册验证码、会话工作记忆（`mha:wm:*`，TTL 7 天） |
| 本地数据层编排 | Docker Compose | — | `code/ai-springboot/docker/docker-compose.yml` |
| 邮件 | Spring Boot Mail | 由 Boot 4.1.1 管理（`spring-boot-starter-mail`） | 发送注册验证码；未配 `spring.mail.host` 时降级为输出到日志 |
| 构建工具 | Maven | — | 使用 `mvnw` / `mvnw.cmd` 包装器 |
| 工具库 | Lombok | 由父 POM 管理 | 简化实体类样板代码 |

## 三、后端依赖现状

`code/ai-springboot/pom.xml` 中**已声明**的依赖：

- `spring-boot-starter-webmvc`
- `spring-boot-starter-data-jdbc`
- `com.mysql:mysql-connector-j`（runtime）
- `spring-boot-starter-data-mongodb`
- `spring-boot-starter-data-redis`
- `spring-boot-starter-mail`
- `spring-ai-starter-model-openai`（版本由 `spring-ai-bom` 统一管理）
- `spring-ai-starter-model-ollama`（Embedding：bge-m3）
- `spring-ai-starter-vector-store-milvus`（长期记忆向量库）
- `org.projectlombok:lombok`（optional）
- `spring-boot-starter-webmvc-test`、`spring-boot-starter-data-jdbc-test`（test）

### 版本兼容性说明

Spring AI 的版本线必须与 Spring Boot 匹配，二者绑定的 Spring Framework 版本要保持一致：

| Spring AI | Spring Framework | 适用 Spring Boot |
| --- | --- | --- |
| 1.1.x | 6.2.x | 3.5.x |
| **2.0.x** | **7.0.x** | **4.x（本项目 Spring Boot 4.1.1，选 2.0.1）** |

本项目使用 Spring Boot 4.1.1（Spring Framework 7.0.9），因此 Spring AI 选用 **2.0.1**（`spring-ai-bom` 导入 `dependencyManagement`）。若误用 1.1.x，会引入 Spring Framework 6.2 与 7.0 的版本冲突。

### 连接配置现状

**已写入 `application.properties`**（注册验证码功能落地时补上）：

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.timeout=3000ms

mha.mail.from=${MAIL_FROM:no-reply@mha.local}

mha.email-code.expire-seconds=300    # 验证码有效期
mha.email-code.cooldown-seconds=60   # 同一邮箱重发冷却
mha.email-code.max-attempts=5        # 校验失败次数上限
```

**MongoDB 已在「对话记忆」落地时写入**（`application.properties`）：

```properties
# Spring Boot 4 起连接属性迁到 spring.mongodb.*；
# 写成旧的 spring.data.mongodb.uri 不会报错但会被忽略，Boot 退回默认的 test 库。
spring.mongodb.uri=mongodb://localhost:27017/MHAData
# auto-index-creation 仍属于 spring.data.mongodb.*
spring.data.mongodb.auto-index-creation=true

# 对话记忆窗口（消息数，16 = 8 轮）
mha.chat-memory.max-messages=16
```

**三层记忆已在「上下文工程」落地时写入**（`application.properties`）：

```properties
# 聊天用 OpenAI 兼容接口，Embedding 明确切到 Ollama
spring.ai.model.chat=openai
spring.ai.model.embedding=ollama
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.embedding.model=bge-m3

# Milvus（initialize-schema 会自动建集合，维度必须与 bge-m3 一致）
spring.ai.vectorstore.milvus.client.host=localhost
spring.ai.vectorstore.milvus.client.port=19530
spring.ai.vectorstore.milvus.collection-name=mha_long_term_memory
spring.ai.vectorstore.milvus.embedding-dimension=1024
spring.ai.vectorstore.milvus.metric-type=COSINE
spring.ai.vectorstore.milvus.index-type=HNSW

# 召回 / 工作记忆 / 异步提炼
mha.memory.recall-top-k=5
mha.memory.recall-min-score=0.55
mha.memory.recall-window-days=30
mha.memory.working-ttl-days=7
# 删除会话后 normal 记忆的保留期；important 记忆与画像不参与清理
mha.memory.retention-days=30
mha.memory.retention-enabled=true
mha.memory.retention-poll-interval-ms=3600000
mha.memory.retention-batch-size=200
mha.memory.extract.enabled=true
mha.memory.extract.debounce-seconds=90

# Milvus SDK 故障时自带几十次重试，调用方限时等待并熔断，避免拖垮聊天
mha.memory.vector-read-timeout-ms=5000
mha.memory.vector-write-timeout-ms=15000
mha.memory.vector-cooldown-seconds=30
```

MySQL / Redis / MongoDB / Milvus / Ollama 的启动方式见
`code/ai-springboot/docker/README.md`。

**大模型配置**（`application.properties`，全部来自环境变量）：

```properties
spring.ai.openai.api-key=${API_KEY:${OPENAI_API_KEY:not-configured}}
spring.ai.openai.base-url=${BASE_URL:${OPENAI_BASE_URL:https://api.openai.com}}
spring.ai.openai.chat.options.model=${MODEL_NAME:${MODEL:${OPENAI_MODEL:gpt-4o-mini}}}
```

密钥不再从 `tmp/.env` 文件读取，改为在运行环境中设置环境变量：

| 变量 | 是否必填 | 说明 |
| --- | --- | --- |
| `API_KEY` | 是 | 模型服务商的 API Key |
| `BASE_URL` | 否 | OpenAI 兼容接口地址，默认 `https://api.openai.com` |
| `MODEL_NAME` | 否 | 模型名，兼容旧名 `MODEL`，默认 `gpt-4o-mini` |

Windows 设置（`setx` 写入用户环境变量，**重开终端 / 重启 IDE 后生效**）：

```bat
setx API_KEY "sk-..."
setx BASE_URL "https://api.deepseek.com"
setx MODEL_NAME "deepseek-v4-flash"
```

Linux / macOS 可写入 `~/.bashrc` / `~/.zshrc`，或在启动命令前直接注入。
部署时也可通过 Docker `-e` / 容器编排的 `env` 传入。
换厂商只需改 `BASE_URL` 与 `MODEL_NAME`。

系统提示词 `prompt/sys_chat.md` 由 `pom.xml` 的 `<resources>` 复制到 classpath，
启动时由 `config/ChatClientConfig` 读取，并与 `MessageChatMemoryAdvisor` 一起装配成
`ChatClient` bean。

> **Redis 没起不影响应用启动。** Boot 4.1.1 的 `LettuceConnectionFactory` 默认
> `eagerInitialization=false`、`validateConnection=false`，启动时不建 socket，
> 因此注册/登录等不碰 Redis 的接口照常用，只有「发送验证码」会报连接错误。

> **邮件是双模式的。** `spring.mail.host` 未配置时 `JavaMailSender` bean 根本不会被创建，
> 应用改走 `LoggingMailDelivery`——验证码打印在后端日志里，其余流程完全一致。
> 要真发信时取消 `application.properties` 里那段 SMTP 注释并填**授权码**（不是登录密码）。

## 四、目录结构

```
Mental HealthAssistant/
├── ai-vue/                     # 前端工程（Vue 3 + Vite）
│   ├── src/
│   ├── public/
│   ├── index.html
│   └── package.json
├── code/
│   └── ai-springboot/          # 后端工程（Spring Boot）
│       ├── src/main/java/com/blog/aispringboot/
│       ├── src/main/resources/application.properties
│       ├── prompt/sys_chat.md          # AI 心理医生人设（打进 classpath）
│       ├── prompt/memory_extract.md    # 记忆提炼提示词
│       ├── docker/                     # MySQL/Redis/Mongo/Milvus/Ollama 编排
│       ├── tmp/                        # 本地运行日志/调试数据，已 gitignore
│       ├── docs/table.md               # 数据库表结构
│       └── pom.xml
└── docs/                       # 项目文档
    ├── tech-stack.md           # 本文档
    ├── 注册登录实现说明.md      # 注册/登录/JWT 鉴权/邮箱验证码的实现细节
    ├── 对话记忆实现说明.md      # 会话/消息持久化与 Spring AI ChatMemory 实现
    └── 上下文工程实现说明.md    # 三层记忆、异步提炼、Milvus 向量召回
```

## 五、相关文档

- 数据库表结构：`code/ai-springboot/docs/table.md`
- 注册登录与邮箱验证码实现：`docs/注册登录实现说明.md`
- AI 对话记忆实现：`docs/对话记忆实现说明.md`
- 上下文工程（三层记忆）：`docs/上下文工程实现说明.md`
- 本地数据层 Docker：`code/ai-springboot/docker/README.md`
- 后端配置：`code/ai-springboot/src/main/resources/application.properties`
