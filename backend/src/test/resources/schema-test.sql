-- ============================================================
-- API Atlas — Test Database Schema (H2-compatible)
-- Tables: data_source, api_interface, interface_param
-- ============================================================

CREATE TABLE IF NOT EXISTS data_source (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL COMMENT 'MySQL, PostgreSQL, Elasticsearch',
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    database_name VARCHAR(100),
    username VARCHAR(100),
    password VARCHAR(500) COMMENT 'AES-256-GCM encrypted',
    api_key VARCHAR(500) COMMENT 'ES API key',
    status VARCHAR(50) NOT NULL DEFAULT 'DISABLED' COMMENT 'ENABLED, DISABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name),
    INDEX idx_type (type)
);

CREATE TABLE IF NOT EXISTS api_interface (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    english_name VARCHAR(100) NOT NULL,
    chinese_name VARCHAR(200),
    url_slug VARCHAR(200) NOT NULL,
    method VARCHAR(20) NOT NULL DEFAULT 'POST' COMMENT 'POST, GET',
    data_source_id BIGINT NOT NULL,
    query_type VARCHAR(50) NOT NULL COMMENT 'SQL, IBATIS, ESQL, QUERY_DSL',
    query_content TEXT NOT NULL,
    is_paginated TINYINT(1) NOT NULL DEFAULT 0,
    page_size INT DEFAULT 10,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_TEST' COMMENT 'PENDING_TEST, ONLINE, OFFLINE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_english_name (english_name),
    INDEX idx_data_source_id (data_source_id),
    INDEX idx_status (status)
);

CREATE TABLE IF NOT EXISTS interface_param (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    interface_id BIGINT NOT NULL,
    param_name VARCHAR(100) NOT NULL,
    java_type VARCHAR(50) NOT NULL DEFAULT 'String',
    remark VARCHAR(500) DEFAULT '',
    sort_order INT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_interface_id (interface_id),
    CONSTRAINT fk_param_interface FOREIGN KEY (interface_id) REFERENCES api_interface(id) ON DELETE CASCADE
);

-- ============================================================
-- Audit fields (migration)
-- ============================================================

ALTER TABLE data_source ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE data_source ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);

ALTER TABLE api_interface ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE api_interface ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);

ALTER TABLE interface_param ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE interface_param ADD COLUMN IF NOT EXISTS last_modified_by VARCHAR(100);
ALTER TABLE interface_param ADD COLUMN IF NOT EXISTS updated_at DATETIME;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_by VARCHAR(100),
    created_at DATETIME,
    last_modified_by VARCHAR(100),
    last_modified_at DATETIME,
    UNIQUE (username),
    INDEX idx_username (username),
    INDEX idx_role (role)
);
