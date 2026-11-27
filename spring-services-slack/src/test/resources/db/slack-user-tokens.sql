-- Same SQL as in docs/releases/upgrade-to-2.1.md (spec 027); applied by the application's migrations.
CREATE SCHEMA IF NOT EXISTS oe_spring_services;

CREATE TABLE oe_spring_services.slack_user_tokens (
    team_id         VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(64)  NOT NULL,
    encrypted_token BYTEA        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (team_id, user_id)
);
