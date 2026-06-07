-- V3: Create the flag_rules table
-- Rules allow targeting specific users or groups.
-- Example rule: "show this flag to users whose country is 'JO'"
-- Example rule: "show this flag to users in the beta_users list"
--
-- How rules work with rollout_pct:
-- 1. First evaluate rules in order (rule_order column)
-- 2. If a rule matches → serve that rule's value (serve_value)
-- 3. If no rule matches → fall back to rollout_pct evaluation
-- 4. If rollout_pct = 0 and no rules match → flag is off

CREATE TABLE flag_rules (

    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Which flag this rule belongs to
    -- ON DELETE CASCADE means: if the flag is deleted,
    -- all its rules are automatically deleted too
    flag_id     UUID NOT NULL REFERENCES flags(id) ON DELETE CASCADE,

    -- Lower number = evaluated first
    -- Allows ordering: "check VIP users before checking country"
    rule_order  INTEGER NOT NULL DEFAULT 0,

    -- The user attribute to check: 'userId', 'country', 'plan', 'email'
    -- This matches a key in the user context the SDK sends with each request
    attribute   VARCHAR(100) NOT NULL,

    -- How to compare the attribute value:
    -- IN         → attribute value is in the list
    -- NOT_IN     → attribute value is NOT in the list
    -- EQUALS     → attribute value exactly matches
    -- CONTAINS   → attribute value contains the string
    operator    VARCHAR(20) NOT NULL
                CONSTRAINT operator_values
                CHECK (operator IN ('IN', 'NOT_IN', 'EQUALS', 'CONTAINS')),

    -- JSON array of values to compare against
    -- Stored as TEXT because the array size varies
    -- Example: '["user-abc", "user-xyz"]'
    -- Example: '["JO", "US", "GB"]'
    values      TEXT NOT NULL,

    -- What to return when this rule matches
    -- TRUE  = serve the flag as enabled
    -- FALSE = serve the flag as disabled (useful for exclusion rules)
    serve_value BOOLEAN NOT NULL DEFAULT TRUE
);

-- Index to quickly find all rules for a specific flag
-- Used every time we evaluate a flag that has rules
CREATE INDEX idx_flag_rules_flag_id ON flag_rules(flag_id);

-- Composite index for ordered rule evaluation
CREATE INDEX idx_flag_rules_flag_order ON flag_rules(flag_id, rule_order);