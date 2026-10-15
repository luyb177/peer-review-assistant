package edu.course.peerreview.service;

import edu.course.peerreview.dao.*;
import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.util.Passwords;
import java.util.*;
import static edu.course.peerreview.dao.Jdbc.*;

public final class AuthService {
    private final Database db;
    private final Repository repo = new Repository();
    public AuthService(Database db) { this.db = db; }
    public User login(String username, char[] password) {
        try {
            String name = Validation.text(username, "用户名", 64);
            if (password.length > 128) throw new BusinessException("用户名或密码错误。");
            return db.read(c -> {
                var rows = query(c, "SELECT * FROM users WHERE username=?", r -> new Object[]{repo.user(r), r.getString("password_hash")}, name);
                if (rows.isEmpty() || !Passwords.verify(password, (String) rows.get(0)[1])) throw new BusinessException("用户名或密码错误。");
                return (User) rows.get(0)[0];
            });
        } finally { Arrays.fill(password, '\0'); }
    }
    public List<User> users(User actor) { requireTeacher(actor); return db.read(repo::users); }
    public void createStudent(User actor, String username, String name, char[] password) {
        requireTeacher(actor);
        try {
            String login = Validation.text(username, "用户名", 64);
            if (!login.matches("[a-zA-Z0-9_-]{3,64}")) throw new BusinessException("用户名须为 3—64 位字母、数字、下划线或短横线。");
            String display = Validation.text(name, "姓名", 80);
            String hash = Passwords.hash(password);
            db.transaction(c -> {
                update(c, "INSERT INTO users(username,display_name,password_hash,role) VALUES(?,?,?,?)", login, display, hash, Role.STUDENT);
                repo.log(c, actor.id(), "CREATE_STUDENT", login);
                return null;
            });
        } finally { Arrays.fill(password, '\0'); }
    }
    public void renameStudent(User actor, long studentId, String name) {
        requireTeacher(actor);
        String display = Validation.text(name, "学生姓名", 80);
        db.transaction(c -> {
            User student = repo.user(c, studentId);
            if (student.role() != Role.STUDENT) throw new BusinessException("只能编辑学生姓名。");
            update(c, "UPDATE users SET display_name=? WHERE id=?", display, studentId);
            repo.log(c, actor.id(), "RENAME_STUDENT", "学生 " + studentId);
            return null;
        });
    }
    public void deleteStudent(User actor, long studentId) {
        requireTeacher(actor);
        db.transaction(c -> {
            query(c, "SELECT id FROM users WHERE id=? FOR UPDATE", r -> r.getLong(1), studentId);
            if (repo.user(c, studentId).role() != Role.STUDENT) throw new BusinessException("不能删除教师账号。");
            if (count(c, "SELECT COUNT(*) FROM submissions WHERE student_id=?", studentId) > 0
                    || count(c, "SELECT COUNT(*) FROM review_tasks WHERE reviewer_id=?", studentId) > 0
                    || count(c, "SELECT COUNT(*) FROM operation_logs WHERE actor_id=?", studentId) > 0)
                throw new BusinessException("该学生已有提交、任务或操作历史，不能删除，以保护业务记录。");
            update(c, "DELETE FROM users WHERE id=?", studentId);
            repo.log(c, actor.id(), "DELETE_STUDENT", "学生 " + studentId);
            return null;
        });
    }
    public void changePassword(User actor, char[] oldPassword, char[] newPassword) {
        try {
            String hash = Passwords.hash(newPassword);
            db.transaction(c -> {
                var found = query(c, "SELECT password_hash FROM users WHERE id=? FOR UPDATE", r -> r.getString(1), actor.id());
                if (found.isEmpty() || !Passwords.verify(oldPassword, found.get(0))) throw new BusinessException("原密码错误。");
                update(c, "UPDATE users SET password_hash=? WHERE id=?", hash, actor.id());
                repo.log(c, actor.id(), "CHANGE_PASSWORD", "用户修改密码");
                return null;
            });
        } finally { Arrays.fill(oldPassword, '\0'); Arrays.fill(newPassword, '\0'); }
    }
    public static void requireTeacher(User actor) { if (actor == null || actor.role() != Role.TEACHER) throw new BusinessException("仅教师可以执行此操作。"); }
    public static void requireStudent(User actor) { if (actor == null || actor.role() != Role.STUDENT) throw new BusinessException("仅学生可以执行此操作。"); }
}
