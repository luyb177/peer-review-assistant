CREATE TABLE IF NOT EXISTS users (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(64) NOT NULL UNIQUE,
 display_name VARCHAR(80) NOT NULL,
 password_hash VARCHAR(512) NOT NULL,
 role VARCHAR(16) NOT NULL,
 CHECK (role IN ('TEACHER','STUDENT'))
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS courses (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(120) NOT NULL,
 description TEXT NOT NULL,
 teacher_id BIGINT NOT NULL,
 FOREIGN KEY (teacher_id) REFERENCES users(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS assignments (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 course_id BIGINT NOT NULL,
 title VARCHAR(160) NOT NULL,
 description TEXT NOT NULL,
 review_open TIMESTAMP NOT NULL,
 review_deadline TIMESTAMP NOT NULL,
 blind BOOLEAN NOT NULL,
 phase VARCHAR(20) NOT NULL,
 FOREIGN KEY (course_id) REFERENCES courses(id),
 CHECK (phase IN ('SUBMISSION','REVIEWING','CLOSED')),
 CHECK (review_deadline > review_open)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS criteria (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 assignment_id BIGINT NOT NULL,
 name VARCHAR(80) NOT NULL,
 weight DECIMAL(7,2) NOT NULL,
 min_score DECIMAL(10,2) NOT NULL,
 max_score DECIMAL(10,2) NOT NULL,
 FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
 UNIQUE (assignment_id, name),
 CHECK (weight > 0 AND weight <= 100),
 CHECK (min_score >= 0 AND max_score > min_score)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS submissions (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 assignment_id BIGINT NOT NULL,
 student_id BIGINT NOT NULL,
 content TEXT NOT NULL,
 submitted_at TIMESTAMP NOT NULL,
 FOREIGN KEY (assignment_id) REFERENCES assignments(id),
 FOREIGN KEY (student_id) REFERENCES users(id),
 UNIQUE (assignment_id, student_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS review_tasks (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 submission_id BIGINT NOT NULL,
 reviewer_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL,
 FOREIGN KEY (submission_id) REFERENCES submissions(id),
 FOREIGN KEY (reviewer_id) REFERENCES users(id),
 UNIQUE (submission_id, reviewer_id),
 CHECK (status IN ('PENDING','COMPLETED'))
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS reviews (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 task_id BIGINT NOT NULL UNIQUE,
 version BIGINT NOT NULL DEFAULT 1,
 total DECIMAL(7,2) NOT NULL,
 comment TEXT NOT NULL,
 reviewed_at TIMESTAMP NOT NULL,
 FOREIGN KEY (task_id) REFERENCES review_tasks(id),
 CHECK (total >= 0 AND total <= 100)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS review_scores (
 review_id BIGINT NOT NULL,
 criterion_id BIGINT NOT NULL,
 score DECIMAL(10,2) NOT NULL,
 PRIMARY KEY (review_id, criterion_id),
 FOREIGN KEY (review_id) REFERENCES reviews(id),
 FOREIGN KEY (criterion_id) REFERENCES criteria(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS ai_advice (
 task_id BIGINT PRIMARY KEY,
 advice MEDIUMTEXT NOT NULL,
 model VARCHAR(160) NOT NULL,
 created_at TIMESTAMP NOT NULL,
 FOREIGN KEY (task_id) REFERENCES review_tasks(id)
) ENGINE=InnoDB;
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
CREATE TABLE IF NOT EXISTS operation_logs (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 actor_id BIGINT NOT NULL,
 action VARCHAR(80) NOT NULL,
 detail VARCHAR(500) NOT NULL,
 created_at TIMESTAMP NOT NULL,
 FOREIGN KEY (actor_id) REFERENCES users(id)
) ENGINE=InnoDB;
