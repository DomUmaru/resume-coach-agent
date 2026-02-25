-- 中文说明：Resume Coach Agent 初版数据库结构（PostgreSQL）

CREATE TABLE IF NOT EXISTS resume_document (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    doc_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1024),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_resume_document_user_id ON resume_document(user_id);

CREATE TABLE IF NOT EXISTS resume_chunk (
    id VARCHAR(64) PRIMARY KEY,
    doc_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    parent_id VARCHAR(64),
    section VARCHAR(32) NOT NULL,
    chunk_type VARCHAR(32) NOT NULL,
    source_page INT,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_resume_chunk_doc_id ON resume_chunk(doc_id);
CREATE INDEX IF NOT EXISTS idx_resume_chunk_user_id ON resume_chunk(user_id);
CREATE INDEX IF NOT EXISTS idx_resume_chunk_section_type ON resume_chunk(doc_id, section, chunk_type);
CREATE INDEX IF NOT EXISTS idx_resume_chunk_content_fts ON resume_chunk USING GIN (to_tsvector('simple', content));

CREATE TABLE IF NOT EXISTS chat_session (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    doc_id VARCHAR(64) NOT NULL,
    title VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chat_session_user_id ON chat_session(user_id);

CREATE TABLE IF NOT EXISTS chat_message (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    citations_json TEXT,
    tool_trace_json TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chat_message_session_id_time ON chat_message(session_id, created_at);
