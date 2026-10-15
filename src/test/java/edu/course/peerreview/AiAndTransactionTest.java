package edu.course.peerreview;

import edu.course.peerreview.ai.*;
import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static edu.course.peerreview.dao.Jdbc.*;
import static org.junit.jupiter.api.Assertions.*;

class AiAndTransactionTest {
    @TempDir Path temp;
    @Test void aiDisabledAndInsecureEndpointsFailBeforeAnyRequest() {
        AppConfig config = new AppConfig(temp.resolve("absent.properties"));
        var provider = new ClaudeAdviceProvider(config);
        assertThrows(BusinessException.class, () -> provider.generate(null));
        config.set("ai.enabled", "true"); config.set("ai.apiKey", "test-only"); config.set("ai.baseUrl", "http://example.com");
        assertThrows(BusinessException.class, () -> provider.generate(null));
        config.set("ai.baseUrl", "not a URL"); assertThrows(BusinessException.class, () -> provider.generate(null));
    }
    @Test void promptIncludesRulesAndContentButNotIdentityFields() {
        TestFixture f = new TestFixture(); f.service.allocate(f.teacher, f.readyId, 1);
        var student = f.students.get(0); var task = f.service.tasks(student, f.readyId).get(0);
        var input = f.service.adviceInput(student, task.id()); String prompt = AdvicePrompt.user(input);
        assertTrue(prompt.contains(input.submission())); assertTrue(prompt.contains("完整性")); assertTrue(prompt.contains("60.00"));
        assertFalse(prompt.contains("student01")); assertFalse(prompt.contains("演示教师"));
        assertTrue(AdvicePrompt.SYSTEM.contains("不给数值分数"));
        AdviceProvider fake = value -> "仅为测试替身，不是模型输出。";
        f.service.saveAdvice(student, task.id(), fake.generate(input), "test-double");
        assertNull(f.service.reviewDetail(student, task.id()));
    }
    @Test void runtimeAndSqlFailuresRollbackWholeTransaction() {
        TestFixture f = new TestFixture(); long before = f.db.read(c -> count(c, "SELECT COUNT(*) FROM courses"));
        assertThrows(IllegalStateException.class, () -> f.db.transaction(c -> {
            update(c, "INSERT INTO courses(name,description,teacher_id) VALUES(?,?,?)", "应该回滚", "说明", f.teacher.id());
            throw new IllegalStateException("test rollback");
        }));
        assertThrows(BusinessException.class, () -> f.db.transaction(c -> {
            update(c, "INSERT INTO courses(name,description,teacher_id) VALUES(?,?,?)", "同样回滚", "说明", f.teacher.id());
            update(c, "INSERT INTO courses(name,description,teacher_id) VALUES(?,?,?)", "外键失败", "说明", -1); return null;
        }));
        assertEquals(before, f.db.read(c -> count(c, "SELECT COUNT(*) FROM courses")).longValue());
    }
}
