-- Security & multitenancy: users, refresh tokens, audit log, ownership.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    family UUID NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    replaced_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    event VARCHAR(100) NOT NULL,
    actor_id UUID,
    actor_type VARCHAR(20) NOT NULL DEFAULT 'user',
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

-- Ownership: every project belongs to a user. Nullable so pre-existing V1 rows
-- are not silently assigned to anyone; they simply become inaccessible.
ALTER TABLE projects ADD COLUMN owner_id UUID REFERENCES users(id);
CREATE INDEX idx_projects_owner_id ON projects(owner_id);

-- Runner credentials: store only the SHA-256 hash of the registration token.
-- The old plaintext `token` column is dropped; legacy runners must be recreated
-- through POST /api/runners (dev data recreation is the documented migration path).
ALTER TABLE runners ADD COLUMN credential_hash VARCHAR(255);
ALTER TABLE runners ADD COLUMN owner_id UUID REFERENCES users(id);
ALTER TABLE runners ADD COLUMN revoked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE runners DROP COLUMN token;
CREATE INDEX idx_runners_owner_id ON runners(owner_id);