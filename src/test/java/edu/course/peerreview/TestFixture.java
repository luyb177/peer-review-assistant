package edu.course.peerreview;

import edu.course.peerreview.dao.*;
import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.service.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import static edu.course.peerreview.dao.Jdbc.*;

/** H2 仅存在于 test 依赖中；产品运行始终使用配置的 MySQL。 */
public final class TestFixture {
    private final String name = UUID.randomUUID().toString();
    public final Database db = new Database(() -> DriverManager.getConnection("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=5000", "sa", ""));
    public final AuthService auth = new AuthService(db);
    public final User teacher;
    public final List<User> students;
    public final long readyId, submissionId;
    public final PeerReviewService service;

    public TestFixture() {
        new InitializationService(db).initialize("teacher", "演示教师", "Teacher123!".toCharArray(), true);
        teacher = auth.login("teacher", "Teacher123!".toCharArray());
        students = auth.users(teacher).stream().filter(u -> u.role() == Role.STUDENT).toList();
        List<Assignment> assignments = new PeerReviewService(db).assignments(teacher);
        readyId = assignments.stream().filter(a -> a.title().contains("可立即分配")).findFirst().orElseThrow().id();
        submissionId = assignments.stream().filter(a -> a.title().contains("可提交")).findFirst().orElseThrow().id();
        service = at(LocalDateTime.now());
    }
    public PeerReviewService at(LocalDateTime time) { return new PeerReviewService(db, Clock.fixed(time.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()), new Random(42)); }
    public Map<Long, java.math.BigDecimal> scores(Assignment a, String n) {
        Map<Long, java.math.BigDecimal> result = new HashMap<>(); a.criteria().forEach(c -> result.put(c.id(), new java.math.BigDecimal(n))); return result;
    }
    public void asOtherTeacher(java.util.function.Consumer<User> operation) {
        long id = db.transaction(c -> insert(c, "INSERT INTO users(username,display_name,password_hash,role) VALUES(?,?,?,?)", "other", "另一教师", edu.course.peerreview.util.Passwords.hash("Other123!".toCharArray()), Role.TEACHER));
        operation.accept(db.read(c -> new Repository().user(c, id)));
    }
}
