-- Run with a server administrator ONLY if a dedicated database does not exist.
-- Supports a remote MySQL server. Never use an unrelated production database.
CREATE DATABASE IF NOT EXISTS peer_review_assistant CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- Ask the administrator for a dedicated user limited to this database.
-- Do not paste real passwords into a committed SQL file.
-- Then fill config/application.properties and run the application initialization.
