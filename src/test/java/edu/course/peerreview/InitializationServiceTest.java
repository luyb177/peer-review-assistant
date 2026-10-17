package edu.course.peerreview;

import edu.course.peerreview.dao.Database;
import edu.course.peerreview.service.InitializationService;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class InitializationServiceTest {
    @Test void oldSchemaRequiresExplicitAdministratorUpgradeWithoutReinitialization() {
        TestFixture f = new TestFixture();
        f.service.allocate(f.teacher, f.readyId, 1);
        var student = f.students.get(0); var task = f.service.tasks(student, f.readyId).get(0);
        f.service.completeReview(student, task.id(), f.scores(f.service.assignment(student, f.readyId), "80"), "旧库评分");
        f.db.read(c -> { try (var s = c.createStatement()) { s.execute("ALTER TABLE reviews DROP COLUMN version"); } return null; });
        var error = assertThrows(edu.course.peerreview.service.BusinessException.class, () -> new InitializationService(f.db).createSchema());
        assertTrue(error.getMessage().contains("sql/01-review-version.sql"));
        var readError = assertThrows(edu.course.peerreview.service.BusinessException.class, () -> f.service.reviewDetail(student, task.id()));
        assertTrue(readError.getMessage().contains("sql/01-review-version.sql"));
        assertEquals(7L, f.db.read(c -> edu.course.peerreview.dao.Jdbc.count(c, "SELECT COUNT(*) FROM users")).longValue());
        f.db.read(c -> { try (var s = c.createStatement()) { s.execute("ALTER TABLE reviews ADD COLUMN version BIGINT NOT NULL DEFAULT 1"); } return null; });
        assertDoesNotThrow(() -> new InitializationService(f.db).createSchema());
    }
    @Test void preCreatedSchemaDoesNotRequireCreateTablePrivilege() {
        TestFixture fixture = new TestFixture();
        String url = fixture.db.read(c -> c.getMetaData().getURL());
        AtomicInteger ddlAttempts = new AtomicInteger();
        Database limited = new Database(() -> {
            Connection delegate = DriverManager.getConnection(url, "sa", "");
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                if (method.getName().equals("createStatement")) {
                    ddlAttempts.incrementAndGet(); throw new SQLException("DDL is not permitted");
                }
                try { return method.invoke(delegate, args); }
                catch (InvocationTargetException e) { throw e.getCause(); }
            });
        });
        new InitializationService(limited).createSchema();
        assertEquals(0, ddlAttempts.get());
    }
}
