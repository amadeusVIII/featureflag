-- V4: Create the audit_log table
-- Records every change made to flags and users.
-- This answers the question: "who changed what, and when?"
-- Critical for debugging ("why did this flag turn on at 3am?")
-- and compliance ("prove that only authorized users changed this flag")

CREATE TABLE audit_log (

    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- What type of thing was changed: 'FLAG' or 'USER'
    entity_type VARCHAR(50) NOT NULL
                CONSTRAINT entity_type_values
                CHECK (entity_type IN ('FLAG', 'USER')),

    -- The ID of the specific flag or user that was changed
    entity_id   UUID NOT NULL,

    -- What happened: CREATED, UPDATED, DELETED, or TOGGLED
    -- TOGGLED is separate from UPDATED because toggling is so common
    -- it deserves its own action type for easy filtering
    action      VARCHAR(50) NOT NULL
                CONSTRAINT action_values
                CHECK (action IN ('CREATED', 'UPDATED', 'DELETED', 'TOGGLED')),

    -- Who made the change — the user ID from the users table
    -- We deliberately do NOT use a foreign key here because:
    -- If a user is deleted, we still want to keep the audit history
    -- A foreign key with ON DELETE SET NULL would lose the identity
    changed_by  UUID NOT NULL,

    -- The complete state of the entity BEFORE the change (JSON)
    -- NULL for CREATED actions (nothing existed before)
    old_value   JSONB,

    -- The complete state of the entity AFTER the change (JSON)
    -- NULL for DELETED actions (nothing exists after)
    new_value   JSONB,

    -- When the change happened
    -- We never update audit log rows — they are immutable records
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()

    -- Notice: NO updated_at column — audit records are never modified
);

-- Index for the most common query: "show me all changes to flag X"
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);

-- Index for time-based queries: "show me all changes in the last 24 hours"
-- DESC means newest first — most audit queries want recent events first
CREATE INDEX idx_audit_created ON audit_log(created_at DESC);

-- Index for "show me all changes made by user X"
CREATE INDEX idx_audit_changed_by ON audit_log(changed_by);