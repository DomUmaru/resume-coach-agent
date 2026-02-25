# PostgreSQL Docker 快速启动

## 1. 启动数据库
```bash
docker compose up -d
```

## 2. 检查状态
```bash
docker compose ps
docker compose logs -f postgres
```

## 3. 本项目默认连接配置
`src/main/resources/application.yml` 已默认：
1. `DB_URL=jdbc:postgresql://localhost:5432/resume_coach`
2. `DB_USERNAME=postgres`
3. `DB_PASSWORD=postgres`

因此直接启动 Spring Boot 即可连接。

## 4. 重置数据库（谨慎）
```bash
docker compose down -v
docker compose up -d
```

说明：
1. `-v` 会删除容器卷数据。
2. 首次启动会自动执行 `sql/init_schema.sql`。

