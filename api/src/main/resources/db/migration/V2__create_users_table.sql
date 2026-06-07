-- V2: Create the users table
-- Stores admin dashboard users AND SDK API keys.
-- Two types of authentication use this table:
-- 1. Email/password → JWT token (for the admin dashboard)
-- 2. API key (for the Java SDK calling the evaluation endpoint)

CREATE TABLE users (

    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Email must be unique — used as the login identifier
    email           VARCHAR(255) NOT NULL UNIQUE,

    -- NEVER store plain text passwords — always store a bcrypt hash
    -- bcrypt hash is always 60 characters but we use 255 for safety
    password_hash   VARCHAR(255) NOT NULL,

    -- Role controls what the user can do:
    -- ADMIN  → can create, update, delete flags
    -- VIEWER → can only read flags (read-only dashboard access)
    role            VARCHAR(20) NOT NULL DEFAULT 'VIEWER'
                    CONSTRAINT role_values CHECK (role IN ('ADMIN', 'VIEWER')),

    -- API key for SDK authentication
    -- Generated when the user requests one via POST /auth/api-key
    -- NULL until the user requests an API key
    -- SDK sends this in the X-API-Key header instead of JWT
    api_key         VARCHAR(64) UNIQUE,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Track when the user last logged in — useful for security audits
    last_login      TIMESTAMPTZ
);

-- Index on email — used on every login attempt
CREATE INDEX idx_users_email ON users(email);

-- Index on api_key — used on every SDK request
-- SDK requests are high-frequency, so this index is critical for performance
CREATE INDEX idx_users_api_key ON users(api_key);

-- ─────────────────────────────────────────────
-- Now that the users table exists, we can add
-- the foreign key constraint to the flags table.
-- We couldn't add it in V1 because users didn't
-- exist yet — this is the correct migration order.
-- ─────────────────────────────────────────────
ALTER TABLE flags
    ADD CONSTRAINT fk_flags_created_by
    FOREIGN KEY (created_by)
    REFERENCES users(id)
    -- If a user is deleted, keep their flags but set created_by to NULL
    -- We don't want to cascade-delete all flags when a user leaves
    ON DELETE SET NULL;