# 本地数据层（Docker Compose）

MySQL、Redis、MongoDB、Milvus（etcd + MinIO）和 Ollama 全部由本目录的 compose 管理，
应用默认连接各服务的**宿主机默认端口**（3306 / 6379 / 27017 / 19530 / 11434）。

## 首次启动前的准备

本机原来装有原生 MySQL 9.7 与 MongoDB 8.3，会占用 3306 / 27017，必须先停掉
（需要管理员 PowerShell）：

```powershell
Stop-Service MySQL97
Stop-Service MongoDB
```

旧的 Redis 容器如果叫 `mha-redis`，会与 compose 中的服务重名，先删除：

```bash
docker rm -f mha-redis
```

## 备份与迁移旧数据

MySQL（本机 `MySQL Server 9.7` 的 bin 目录）：

```bash
"/c/Program Files/MySQL/MySQL Server 9.7/bin/mysqldump.exe" \
  -uroot -p000000 --databases mhadata --single-transaction --routines --triggers \
  --set-gtid-purged=OFF --default-character-set=utf8mb4 \
  > tmp/backup/mysql/mhadata.sql
```

MongoDB（用与容器同版本的临时客户端连接宿主机）。本机旧数据实际在 `test` 库，
`MHA` 库存的是文章/评论，两个都备份：

```bash
# Git Bash on Windows：必须用 cygpath 转成 Windows 路径，否则 -v 挂载为空目录
BACKUP_WIN="$(cygpath -w "$PWD/tmp/backup/mongo")"

docker run --rm -v "$BACKUP_WIN:/backup" mongo:8.0 \
  mongodump --uri "mongodb://host.docker.internal:27017/test" --out /backup/test
docker run --rm -v "$BACKUP_WIN:/backup" mongo:8.0 \
  mongodump --uri "mongodb://host.docker.internal:27017/MHA" --out /backup/MHA
```

`host.docker.internal` 在 Docker Desktop for Windows 上可用；Linux 请换成宿主机 IP
（并把 `cygpath` 换成实际目录）。

## 启动与验证

```bash
docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/docker-compose.yml ps
```

首次启动会自动执行 `init/01-base.sql`、`init/02-memory.sql`、`init/03-retention.sql`
（只有数据卷为空时才会执行）。

恢复旧数据（compose 启动、MySQL healthy 之后）。

`mysqldump --databases` 导出的是**小写** `mhadata`，而容器里的库是 `MHAData`。
Linux 容器区分大小写，直接导入会新建一个小写库；因此先删掉 `CREATE DATABASE` / `USE`
两行，显式指定目标库：

```bash
sed -E '/^CREATE DATABASE .*mhadata/d; /^USE `mhadata`;/d' tmp/backup/mysql/mhadata.sql \
  | docker exec -i mha-mysql mysql -uroot -p000000 MHAData
```

MongoDB：旧会话数据在 `test` 库，恢复时改用 `--db` 指定应用使用的 `MHAData`。

`mongodump --out /backup/test` 生成的目录里直接是 `.bson` 文件，不是 mongorestore
默认期望的「根目录/db/集合」结构；这种目录必须用 `--db` 指明目标库，否则会报
`don't know what to do with file ... skipping`。另外 Git Bash 会把 `/backup/test`
改写成 `C:/Program Files/Git/backup/test`，所以命令前要加 `MSYS_NO_PATHCONV=1`：

```bash
BACKUP_WIN="$(cygpath -w "$PWD/tmp/backup/mongo")"

MSYS_NO_PATHCONV=1 docker run --rm -v "$BACKUP_WIN:/backup" --network mha_default mongo:8.0 \
  mongorestore --uri "mongodb://mha-mongo:27017" --db MHAData /backup/test

# 文章/评论库保持原名
MSYS_NO_PATHCONV=1 docker run --rm -v "$BACKUP_WIN:/backup" --network mha_default mongo:8.0 \
  mongorestore --uri "mongodb://mha-mongo:27017" --db MHA /backup/MHA
```

恢复后确认：

```bash
docker exec mha-mongo mongosh MHAData --quiet --eval \
  'db.chat_sessions.countDocuments({}) + " sessions, " + db.chat_messages.countDocuments({}) + " messages"'
```

数据卷已存在时 `init/*.sql` 不会重跑，新增表需要手动执行：

```bash
docker exec -i mha-mysql mysql -uroot -p000000 MHAData < docker/init/02-memory.sql
# 敏感度 / 召回记录 / 保留期字段（幂等，已有库升级用）
docker exec -i mha-mysql mysql -uroot -p000000 MHAData < docker/init/03-retention.sql
```

验证：

```bash
docker exec mha-redis redis-cli ping                 # PONG
docker exec mha-mysql mysqladmin ping -uroot -p000000 # mysqld is alive
curl -f http://localhost:9091/healthz                # OK（Milvus）
curl http://localhost:11434/api/tags                 # 模型列表
```

Ollama 模型 `bge-m3` 由一次性容器 `mha-ollama-pull` 拉取；也可以手动补：

```bash
docker exec mha-ollama ollama pull bge-m3
docker exec mha-ollama ollama list
```

向量维度验证（应返回 1024 个数）：

```bash
curl http://localhost:11434/api/embed \
  -d '{"model":"bge-m3","input":"我最近总是失眠"}'
```

## 常用操作

```bash
# 只看状态
docker compose -f docker/docker-compose.yml ps

# 停止（保留数据）
docker compose -f docker/docker-compose.yml stop

# 停止并删除容器（保留命名卷）
docker compose -f docker/docker-compose.yml down

# 彻底重置（会删除全部数据库数据！）
docker compose -f docker/docker-compose.yml down -v
```

## 注意

- `spring.ai.vectorstore.milvus.embedding-dimension=1024` 与 bge-m3 绑定。换 embedding
  模型或维度时必须先清空 Milvus 集合/卷再重建，否则写入会因维度不一致失败。
- MinIO 控制台：<http://localhost:9001>，账号密码 `minioadmin / minioadmin`。
- MySQL root 密码 `000000` 仅用于本地开发，不要用于生产。
- Milvus standalone 常驻内存约 1~2 GB；本机 Docker 内存不足时先停掉 Ollama 以外的容器，
  或在 Docker Desktop 中调大内存限额。
