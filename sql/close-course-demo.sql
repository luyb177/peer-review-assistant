-- MANUAL maintenance only: run after course acceptance has ended.
-- Disable only this course demo database login. Preserve data and all other services.
DROP USER IF EXISTS 'peer_review_course'@'%';
-- Separately revoke the dedicated demo AI Key in the AI provider console.
-- Do not stop the shared MySQL container. Do not DROP other databases.
