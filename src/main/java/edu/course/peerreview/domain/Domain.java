package edu.course.peerreview.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 不可变业务数据；数据库凭据、密码哈希不属于可展示的用户模型。 */
public final class Domain {
    private Domain() { }

    public enum Role { TEACHER, STUDENT }
    public enum Phase { SUBMISSION, REVIEWING, CLOSED }
    public enum TaskStatus { PENDING, COMPLETED }

    public record User(long id, String username, String name, Role role) {
        @Override public String toString() { return name + " (" + username + ")"; }
    }
    public record Course(long id, String name, String description, long teacherId) {
        @Override public String toString() { return name; }
    }
    public record Criterion(long id, String name, BigDecimal weight, BigDecimal min, BigDecimal max) { }
    public record Assignment(long id, long courseId, String title, String description,
                             LocalDateTime reviewOpen, LocalDateTime reviewDeadline,
                             boolean blind, Phase phase, List<Criterion> criteria) {
        public Assignment { criteria = List.copyOf(criteria); }
        @Override public String toString() { return title; }
    }
    public record Submission(long id, long assignmentId, long studentId, String author,
                             String content, LocalDateTime submittedAt) { }
    public record ReviewTask(long id, long assignmentId, long submissionId, long reviewerId,
                             String author, String reviewer, String content,
                             TaskStatus status, BigDecimal total, String comment) { }
    public record ReviewDetail(long taskId, Map<Long, BigDecimal> scores, String comment, BigDecimal total) {
        public ReviewDetail { scores = Map.copyOf(scores); }
    }
    public record Statistics(long submissionId, String author, int completed, int assigned,
                             BigDecimal average, BigDecimal highest, BigDecimal lowest) { }
    public record Allocation(long submissionId, long reviewerId) { }
    public record Participant(long submissionId, long studentId) { }
    public record Advice(String text, String model, LocalDateTime createdAt) { }
}
