-- V1: Create the flags table
-- This is the core table of the entire system.
-- Every feature flag is one row in this table.

CREATE TABLE flags (

    -- UUID primary key — better than auto-increment integers because:
    -- 1. IDs are unpredictable (security — users can't guess other flag IDs)
    -- 2. Safe to generate on the client side without a DB round trip
    -- 3. Works correctly when merging data from multiple environments
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- The short identifier used in code: "dark-mode", "new-checkout-flow"
    -- kebab-case by convention — this is what developers type in their app
    key             VARCHAR(100) NOT NULL,

    -- Human-readable display name for the admin dashboard
    name            VARCHAR(200) NOT NULL,

    -- Optional explanation of what this flag controls
    description     TEXT,

    -- Whether the flag is currently on or off
    -- Always start new flags as FALSE (disabled) — never release dark code enabled
    enabled         BOOLEAN NOT NULL DEFAULT FALSE,

    -- Which environment this flag belongs to: dev, staging, production
    -- Same flag key can have DIFFERENT states per environment
    -- e.g. dark-mode enabled in staging, disabled in production
    environment     VARCHAR(50) NOT NULL DEFAULT 'production',

    -- Percentage of users who see this flag as enabled: 0-100
    -- 100 = everyone, 0 = nobody, 10 = 10% of users
    -- Used for gradual rollouts
    rollout_pct     INTEGER NOT NULL DEFAULT 100
                    -- Enforce valid percentage range at the database level
                    CONSTRAINT rollout_pct_range CHECK (rollout_pct >= 0 AND rollout_pct <= 100),

    -- Type of flag: BOOLEAN (on/off) or STRING (returns a value)
    flag_type       VARCHAR(20) NOT NULL DEFAULT 'BOOLEAN',

    -- Value returned when flag_type is STRING
    -- NULL for BOOLEAN flags
    string_value    VARCHAR(500),

    -- Which user created this flag — references users table (added in V2)
    -- We use UUID type here to match the users.id column we'll create next
    created_by      UUID NOT NULL,

    -- Audit timestamps — always include these on every table
    -- TIMESTAMPTZ = timestamp WITH timezone — always store in UTC
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- The combination of (key + environment) must be unique
    -- You CAN have "dark-mode" in both staging AND production
    -- You CANNOT have two "dark-mode" flags in the same environment
    CONSTRAINT uq_flags_key_environment UNIQUE (key, environment)
);

-- ─────────────────────────────────────────────
-- INDEXES
-- An index is like a book's table of contents —
-- it lets the database find rows without scanning
-- the entire table. Always index columns you
-- filter or join on frequently.
-- ─────────────────────────────────────────────

-- We look up flags by key constantly (every evaluation request)
CREATE INDEX idx_flags_key ON flags(key);

-- We filter by environment on almost every query
CREATE INDEX idx_flags_environment ON flags(environment);

-- Composite index for the most common query pattern:
-- "give me flag X in environment Y"
-- This is faster than using the two single-column indexes above
CREATE INDEX idx_flags_key_env ON flags(key, environment);