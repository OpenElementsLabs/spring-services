-- Pre-create the library's dedicated schema so Hibernate's create-drop startup DROP phase
-- (which emits an unguarded "drop schema oe_spring_services") has an existing schema to drop
-- on the first run against a fresh Testcontainer. Without this, Hibernate logs a harmless but
-- noisy CommandAcceptanceException before recreating the schema.
CREATE SCHEMA IF NOT EXISTS oe_spring_services;
