package edu.course.peerreview.dao;

import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.service.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;
import static edu.course.peerreview.dao.Jdbc.*;

/** 关系数据库映射；所有授权和状态改变由业务层组织。 */
public final class Repository {
    public User user(ResultSet r) throws SQLException { return new User(r.getLong("id"), r.getString("username"), r.getString("display_name"), Role.valueOf(r.getString("role"))); }
    public User user(Connection c, long id) throws SQLException { return one(query(c, "SELECT * FROM users WHERE id=?", this::user, id)); }
    public List<User> users(Connection c) throws SQLException { return query(c, "SELECT * FROM users ORDER BY id", this::user); }
    public List<Course> courses(Connection c, User actor) throws SQLException {
        String condition = actor.role() == Role.TEACHER ? " WHERE teacher_id=?" : "";
        Object[] args = actor.role() == Role.TEACHER ? new Object[]{actor.id()} : new Object[]{};
        return query(c, "SELECT * FROM courses" + condition + " ORDER BY id", this::course, args);
    }
    private Course course(ResultSet r) throws SQLException { return new Course(r.getLong("id"), r.getString("name"), r.getString("description"), r.getLong("teacher_id")); }
    public Course course(Connection c, long id) throws SQLException { return one(query(c, "SELECT * FROM courses WHERE id=?", this::course, id)); }
    public List<Criterion> criteria(Connection c, long assignmentId) throws SQLException {
        return query(c, "SELECT * FROM criteria WHERE assignment_id=? ORDER BY id", r -> new Criterion(r.getLong("id"), r.getString("name"), r.getBigDecimal("weight"), r.getBigDecimal("min_score"), r.getBigDecimal("max_score")), assignmentId);
    }
    private Assignment assignment(Connection c, ResultSet r) throws SQLException {
        long id = r.getLong("id");
        return new Assignment(id, r.getLong("course_id"), r.getString("title"), r.getString("description"), r.getTimestamp("review_open").toLocalDateTime(), r.getTimestamp("review_deadline").toLocalDateTime(), r.getBoolean("blind"), Phase.valueOf(r.getString("phase")), criteria(c, id));
    }
    public Assignment assignment(Connection c, long id, boolean lock) throws SQLException {
        return one(query(c, "SELECT * FROM assignments WHERE id=?" + (lock ? " FOR UPDATE" : ""), r -> assignment(c, r), id));
    }
    public List<Assignment> assignments(Connection c, User actor) throws SQLException {
        if (actor.role() == Role.TEACHER) return query(c, "SELECT a.* FROM assignments a JOIN courses co ON co.id=a.course_id WHERE co.teacher_id=? ORDER BY a.id DESC", r -> assignment(c, r), actor.id());
        return query(c, "SELECT * FROM assignments ORDER BY id DESC", r -> assignment(c, r));
    }
    public List<Submission> submissions(Connection c, long assignmentId, Long studentId) throws SQLException {
        String sql = "SELECT s.*,u.display_name author FROM submissions s JOIN users u ON u.id=s.student_id WHERE s.assignment_id=?";
        if (studentId != null) return query(c, sql + " AND s.student_id=? ORDER BY s.id", this::submission, assignmentId, studentId);
        return query(c, sql + " ORDER BY s.id", this::submission, assignmentId);
    }
    public Submission submission(Connection c, long id) throws SQLException {
        return one(query(c, "SELECT s.*,u.display_name author FROM submissions s JOIN users u ON u.id=s.student_id WHERE s.id=?", this::submission, id));
    }
    private Submission submission(ResultSet r) throws SQLException { return new Submission(r.getLong("id"), r.getLong("assignment_id"), r.getLong("student_id"), r.getString("author"), r.getString("content"), r.getTimestamp("submitted_at").toLocalDateTime()); }
    public static final String TASK_SQL = "SELECT t.*,s.assignment_id,s.content,s.student_id,u.display_name author,rv.display_name reviewer,r.total,r.comment FROM review_tasks t JOIN submissions s ON s.id=t.submission_id JOIN users u ON u.id=s.student_id JOIN users rv ON rv.id=t.reviewer_id LEFT JOIN reviews r ON r.task_id=t.id";
    private ReviewTask task(ResultSet r) throws SQLException {
        return new ReviewTask(r.getLong("id"), r.getLong("assignment_id"), r.getLong("submission_id"), r.getLong("reviewer_id"), r.getString("author"), r.getString("reviewer"), r.getString("content"), TaskStatus.valueOf(r.getString("status")), r.getBigDecimal("total"), r.getString("comment"));
    }
    public ReviewTask task(Connection c, long id) throws SQLException { return one(query(c, TASK_SQL + " WHERE t.id=?", this::task, id)); }
    public List<ReviewTask> tasks(Connection c, long assignmentId, Long reviewerId) throws SQLException {
        if (reviewerId == null) return query(c, TASK_SQL + " WHERE s.assignment_id=? ORDER BY t.id", this::task, assignmentId);
        return query(c, TASK_SQL + " WHERE s.assignment_id=? AND t.reviewer_id=? ORDER BY t.id", this::task, assignmentId, reviewerId);
    }
    public ReviewDetail detail(Connection c, long taskId) throws SQLException {
        List<ReviewDetail> found = query(c, "SELECT * FROM reviews WHERE task_id=?", r -> {
            Map<Long, BigDecimal> scores = new LinkedHashMap<>();
            query(c, "SELECT * FROM review_scores WHERE review_id=?", s -> { scores.put(s.getLong("criterion_id"), s.getBigDecimal("score")); return 0; }, r.getLong("id"));
            return new ReviewDetail(taskId, r.getLong("id"), r.getLong("version"), scores, r.getString("comment"), r.getBigDecimal("total"));
        }, taskId);
        return found.isEmpty() ? null : found.get(0);
    }
    public Advice advice(Connection c, long taskId) throws SQLException {
        List<Advice> rows = query(c, "SELECT * FROM ai_advice WHERE task_id=?", r -> new Advice(r.getString("advice"), r.getString("model"), r.getTimestamp("created_at").toLocalDateTime()), taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public List<Statistics> statistics(Connection c, long assignmentId, Long studentId) throws SQLException {
        String sql = "SELECT s.id,u.display_name,COUNT(r.id) completed,COUNT(t.id) assigned,AVG(r.total) average,MAX(r.total) highest,MIN(r.total) lowest FROM submissions s JOIN users u ON u.id=s.student_id LEFT JOIN review_tasks t ON t.submission_id=s.id LEFT JOIN reviews r ON r.task_id=t.id WHERE s.assignment_id=?";
        String end = " GROUP BY s.id,u.display_name ORDER BY s.id";
        Jdbc.Mapper<Statistics> mapper = r -> new Statistics(r.getLong("id"), r.getString("display_name"), r.getInt("completed"), r.getInt("assigned"), rounded(r.getBigDecimal("average")), rounded(r.getBigDecimal("highest")), rounded(r.getBigDecimal("lowest")));
        return studentId == null ? query(c, sql + end, mapper, assignmentId) : query(c, sql + " AND s.student_id=?" + end, mapper, assignmentId, studentId);
    }
    private BigDecimal rounded(BigDecimal n) { return n == null ? null : n.setScale(2, RoundingMode.HALF_UP); }
    public void log(Connection c, long actor, String action, String detail) throws SQLException {
        update(c, "INSERT INTO operation_logs(actor_id,action,detail,created_at) VALUES(?,?,?,?)", actor, action, detail, java.time.LocalDateTime.now());
    }
    private <T> T one(List<T> list) { if (list.isEmpty()) throw new BusinessException("记录不存在或已删除，请刷新。"); return list.get(0); }
}
