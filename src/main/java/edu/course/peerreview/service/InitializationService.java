package edu.course.peerreview.service;

import edu.course.peerreview.dao.*;
import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.util.Passwords;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import static edu.course.peerreview.dao.Jdbc.*;

/** 只建不存在的表；仅在用户表为空时创建初始数据，绝不执行 DROP/TRUNCATE。 */
public final class InitializationService {
    private final Database db;
    public InitializationService(Database db) { this.db = db; }
    public void initialize(String username, String displayName, char[] password, boolean demo) {
        try {
            String login = Validation.text(username, "教师账号", 64);
            if (!login.matches("[a-zA-Z0-9_-]{3,64}")) throw new BusinessException("教师账号须为 3—64 位字母、数字、下划线或短横线。");
            String name = Validation.text(displayName, "教师姓名", 80);
            String hash = Passwords.hash(password);
            createSchema();
            db.transaction(c -> {
                if (count(c, "SELECT COUNT(*) FROM users") != 0) throw new BusinessException("已有用户数据，拒绝重复初始化。请直接登录，不要重置现有数据库。");
                long teacher = insert(c, "INSERT INTO users(username,display_name,password_hash,role) VALUES(?,?,?,?)", login, name, hash, Role.TEACHER);
                if (demo) {
                    long course = insert(c, "INSERT INTO courses(name,description,teacher_id) VALUES(?,?,?)", "软件构造 · 演示课堂", "仅用于验收演示的合成数据", teacher);
                    LocalDateTime now = LocalDateTime.now().withNano(0);
                    long ready = assignment(c, course, "设计原则分析（可立即分配）", now.minusHours(1), now.plusDays(7));
                    assignment(c, course, "模块化实践（可提交）", now.plusDays(1), now.plusDays(7));
                    String studentHash = Passwords.hash("Demo12345!".toCharArray());
                    for (int i = 1; i <= 6; i++) {
                        long student = insert(c, "INSERT INTO users(username,display_name,password_hash,role) VALUES(?,?,?,?)", "student0" + i, "演示学生" + i, studentHash, Role.STUDENT);
                        insert(c, "INSERT INTO submissions(assignment_id,student_id,content,submitted_at) VALUES(?,?,?,?)", ready, student, "【合成演示提交 " + i + "】\n我将系统划分为界面层、业务层、数据访问层。\n评分计算应独立于 Swing 和 JDBC，使用接口隔离变化。\n任务分配应排除自评，并通过单元测试覆盖人数不足的边界。\n可改进之处：进一步补充集成测试和异常恢复方案。", now.minusHours(2));
                    }
                }
                new Repository().log(c, teacher, "INITIALIZE", demo ? "初始化合成演示数据" : "初始化教师账号");
                return null;
            });
        } finally { Arrays.fill(password, '\0'); }
    }
    public void createSchema() {
        String sql;
        try (InputStream in = InitializationService.class.getResourceAsStream("/schema.sql")) {
            if (in == null) throw new IOException("Missing schema");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new BusinessException("无法读取内置建表脚本。", e); }
        db.read(c -> {
            java.util.Set<String> expected = new java.util.HashSet<>();
            var pattern = java.util.regex.Pattern.compile("CREATE TABLE IF NOT EXISTS ([a-z_]+)").matcher(sql);
            while (pattern.find()) expected.add(pattern.group(1));
            java.util.Set<String> existing = new java.util.HashSet<>();
            try (var tables = c.getMetaData().getTables(c.getCatalog(), c.getSchema(), "%", new String[]{"TABLE"})) {
                while (tables.next()) existing.add(tables.getString("TABLE_NAME").toLowerCase(java.util.Locale.ROOT));
            }
            // 管理员已建齐表时不再执行 DDL，应用账号只需数据读写权限。
            if (!expected.isEmpty() && existing.containsAll(expected)) return null;
            try (var s = c.createStatement()) {
                for (String part : sql.split(";")) if (!part.isBlank()) s.execute(part);
            }
            return null;
        });
    }
    private long assignment(java.sql.Connection c, long course, String title, LocalDateTime open, LocalDateTime deadline) throws java.sql.SQLException {
        long id = insert(c, "INSERT INTO assignments(course_id,title,description,review_open,review_deadline,blind,phase) VALUES(?,?,?,?,?,?,?)", course, title, "分析分层设计、面向对象原则及测试方案。请提交纯文本，不要在内容中署名。", open, deadline, true, Phase.SUBMISSION);
        update(c, "INSERT INTO criteria(assignment_id,name,weight,min_score,max_score) VALUES(?,?,?,?,?)", id, "完整性", 60, 0, 100);
        update(c, "INSERT INTO criteria(assignment_id,name,weight,min_score,max_score) VALUES(?,?,?,?,?)", id, "规范性", 30, 0, 100);
        update(c, "INSERT INTO criteria(assignment_id,name,weight,min_score,max_score) VALUES(?,?,?,?,?)", id, "创新性", 10, 0, 100);
        return id;
    }
}
