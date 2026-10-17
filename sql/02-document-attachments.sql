-- Additive migration only. Run in the dedicated database, never DROP/TRUNCATE old data.
CREATE TABLE IF NOT EXISTS attachments (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 assignment_id BIGINT NOT NULL,
 submission_id BIGINT,
 file_name VARCHAR(255) NOT NULL,
 media_type VARCHAR(120) NOT NULL,
 file_size BIGINT NOT NULL,
 sha256 VARCHAR(64) NOT NULL,
 file_content MEDIUMBLOB NOT NULL,
 text_content MEDIUMTEXT NOT NULL,
 text_status VARCHAR(32) NOT NULL,
 created_by BIGINT NOT NULL,
 created_at TIMESTAMP NOT NULL,
 FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
 FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE,
 FOREIGN KEY (created_by) REFERENCES users(id),
 CHECK (file_size > 0 AND file_size <= 5242880)
) ENGINE=InnoDB;
