-- 0. Kích hoạt Extension sinh UUID tự động
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. BẢNG NGƯỜI DÙNG (users)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. BẢNG SƠ ĐỒ (diagrams)
CREATE TABLE diagrams (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(255) NOT NULL,
    diagram_type VARCHAR(20) NOT NULL CHECK (diagram_type IN ('UML', 'ERD')),
    data JSONB NOT NULL DEFAULT '{"nodes": [], "edges": []}',
    version INT NOT NULL DEFAULT 1,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. BẢNG QUYỀN CHIA SẺ (diagram_shares)
CREATE TABLE diagram_shares (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    diagram_id UUID NOT NULL REFERENCES diagrams(id) ON DELETE CASCADE,
    share_token VARCHAR(64) UNIQUE NOT NULL,
    role VARCHAR(10) NOT NULL CHECK (role IN ('view', 'edit')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. BẢNG LỊCH SỬ PHIÊN BẢN (diagram_versions)
CREATE TABLE diagram_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    diagram_id UUID NOT NULL REFERENCES diagrams(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    data JSONB NOT NULL,
    note VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index tăng tốc độ truy vấn
CREATE INDEX idx_diagrams_owner ON diagrams(owner_id);
CREATE INDEX idx_shares_token ON diagram_shares(share_token);
CREATE INDEX idx_versions_diagram ON diagram_versions(diagram_id);

-- DỮ LIỆU MẪU (Seed Data để test ngay)
INSERT INTO users (id, username, password_hash) 
VALUES ('11111111-1111-1111-1111-111111111111', 'student01', '$2a$12$eImiTXuWVxfM37uY4JANjOL.88T89q0e.5hM3m3B4C5G6H7I8J9K');

INSERT INTO diagrams (id, title, diagram_type, data, version, is_public, owner_id)
VALUES (
    '22222222-2222-2222-2222-222222222222', 
    'Sơ đồ ERD Quản lý Học phần', 
    'ERD', 
    '{"nodes": [{"id": "1", "label": "Student"}], "edges": []}', 
    1, 
    FALSE, 
    '11111111-1111-1111-1111-111111111111'
);