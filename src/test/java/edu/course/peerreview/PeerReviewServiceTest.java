package edu.course.peerreview;

import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.service.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static edu.course.peerreview.dao.Jdbc.*;

class PeerReviewServiceTest {
    TestFixture f;
    @BeforeEach void setup() { f = new TestFixture(); }

    @Test void fullReviewLifecycleAndStatistics() {
        assertEquals(12, f.service.allocate(f.teacher, f.readyId, 2));
        for (User student : f.students) {
            var tasks = f.service.tasks(student, f.readyId); assertEquals(2, tasks.size());
            for (ReviewTask task : tasks) {
                assertEquals("匿名提交", task.author());
                Assignment a = f.service.assignment(student, f.readyId);
                BigDecimal total = f.service.completeReview(student, task.id(), f.scores(a, "80"), "结构清晰，建议补充边界测试。");
                assertEquals(new BigDecimal("80.00"), total);
                assertEquals(3, f.service.reviewDetail(student, task.id()).scores().size());
                assertThrows(BusinessException.class, () -> f.service.completeReview(student, task.id(), f.scores(a, "80"), "重复"));
            }
        }
        var statistics = f.service.statistics(f.teacher, f.readyId);
        assertEquals(6, statistics.size());
        for (Statistics stat : statistics) { assertEquals(2, stat.completed()); assertEquals(new BigDecimal("80.00"), stat.average()); assertEquals(stat.highest(), stat.lowest()); }
        f.service.closeAssignment(f.teacher, f.readyId);
        assertEquals(Phase.CLOSED, f.service.assignment(f.teacher, f.readyId).phase());
        assertThrows(BusinessException.class, () -> f.service.allocate(f.teacher, f.readyId, 2));
    }
    @Test void submissionUpsertAndDeadline() {
        User student = f.students.get(0);
        f.service.submit(student, f.submissionId, "第一次提交"); f.service.submit(student, f.submissionId, "更新后的提交");
        var own = f.service.submissions(student, f.submissionId); assertEquals(1, own.size()); assertEquals("更新后的提交", own.get(0).content());
        assertEquals(0, f.service.submissions(f.students.get(1), f.submissionId).size());
        assertThrows(BusinessException.class, () -> f.service.submit(student, f.submissionId, " "));
        Assignment a = f.service.assignment(student, f.submissionId);
        assertThrows(BusinessException.class, () -> f.at(a.reviewOpen()).submit(student, a.id(), "迟到提交"));
        assertThrows(BusinessException.class, () -> f.service.submit(f.teacher, a.id(), "教师不能提交"));
    }
    @Test void allocationTimingLimitsAndRollback() {
        assertThrows(BusinessException.class, () -> f.service.allocate(f.teacher, f.submissionId, 1));
        assertThrows(BusinessException.class, () -> f.service.allocate(f.teacher, f.readyId, 6));
        assertEquals(Phase.SUBMISSION, f.service.assignment(f.teacher, f.readyId).phase());
        assertTrue(f.service.tasks(f.teacher, f.readyId).isEmpty());
        assertThrows(BusinessException.class, () -> f.service.allocate(f.students.get(0), f.readyId, 1));
        assertEquals(6, f.service.allocate(f.teacher, f.readyId, 1));
        assertThrows(BusinessException.class, () -> f.service.allocate(f.teacher, f.readyId, 1));
    }
    @Test void authorizationAndBlindnessAppliedBeforeUi() {
        f.service.allocate(f.teacher, f.readyId, 1); User s = f.students.get(0); ReviewTask task = f.service.tasks(s, f.readyId).get(0);
        assertEquals("匿名提交", f.service.task(s, task.id()).author());
        assertNotEquals("匿名提交", f.service.task(f.teacher, task.id()).author());
        User other = f.students.stream().filter(u -> u.id() != task.reviewerId()).findFirst().orElseThrow();
        assertThrows(BusinessException.class, () -> f.service.task(other, task.id()));
        assertThrows(BusinessException.class, () -> f.service.reviewDetail(other, task.id()));
        assertThrows(BusinessException.class, () -> f.service.advice(other, task.id()));
        assertEquals(1, f.service.statistics(s, f.readyId).size());
        f.asOtherTeacher(teacher -> {
            assertThrows(BusinessException.class, () -> f.service.assignment(teacher, f.readyId));
            assertThrows(BusinessException.class, () -> f.service.task(teacher, task.id()));
        });
    }
    @Test void failedReviewCannotLeavePartialRows() {
        f.service.allocate(f.teacher, f.readyId, 1); User s = f.students.get(0); ReviewTask t = f.service.tasks(s, f.readyId).get(0);
        assertThrows(BusinessException.class, () -> f.service.completeReview(s, t.id(), Map.of(), "人工评语"));
        assertThrows(BusinessException.class, () -> f.service.completeReview(s, t.id(), f.scores(f.service.assignment(s, f.readyId), "50"), " "));
        assertEquals(TaskStatus.PENDING, f.service.task(s, t.id()).status());
        assertNull(f.service.reviewDetail(s, t.id()));
        assertEquals(0L, f.db.read(c -> count(c, "SELECT COUNT(*) FROM reviews")).longValue());
    }
    @Test void aiAdviceNeverCreatesHumanReview() {
        f.service.allocate(f.teacher, f.readyId, 1); User s = f.students.get(0); ReviewTask t = f.service.tasks(s, f.readyId).get(0);
        assertEquals(t.content(), f.service.adviceInput(s, t.id()).submission());
        f.service.saveAdvice(s, t.id(), "建议核实是否覆盖所有边界条件。", "test-provider");
        f.service.saveAdvice(s, t.id(), "更新的参考意见", "test-provider");
        assertEquals("更新的参考意见", f.service.advice(s, t.id()).text());
        assertNull(f.service.reviewDetail(s, t.id())); assertEquals(TaskStatus.PENDING, f.service.task(s, t.id()).status());
    }
    @Test void freezeRulesAndProtectDeletion() {
        Assignment a = f.service.assignment(f.teacher, f.readyId);
        assertThrows(BusinessException.class, () -> f.service.saveAssignment(f.teacher, a.id(), a.courseId(), a.title(), a.description(), LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(3), true, a.criteria()));
        assertThrows(BusinessException.class, () -> f.service.deleteAssignment(f.teacher, a.id()));
        assertThrows(BusinessException.class, () -> f.service.deleteCourse(f.teacher, a.courseId()));
        f.service.deleteAssignment(f.teacher, f.submissionId);
        assertThrows(BusinessException.class, () -> f.service.assignment(f.teacher, f.submissionId));
    }
    @Test void courseAssignmentCrudAndInputValidation() {
        f.service.saveCourse(f.teacher, 0, "临时课程", "课程说明");
        Course c = f.service.courses(f.teacher).stream().filter(x -> x.name().equals("临时课程")).findFirst().orElseThrow();
        f.service.saveCourse(f.teacher, c.id(), "新名称", "新说明");
        var criteria = f.service.assignment(f.teacher, f.readyId).criteria();
        LocalDateTime open = LocalDateTime.now().plusDays(1), end = open.plusDays(1);
        f.service.saveAssignment(f.teacher, 0, c.id(), "测试作业", "文本说明", open, end, false, criteria);
        Assignment a = f.service.assignments(f.teacher).stream().filter(x -> x.courseId() == c.id()).findFirst().orElseThrow();
        f.service.saveAssignment(f.teacher, a.id(), c.id(), "修改名称", "修改说明", open, end, true, criteria);
        assertEquals("修改名称", f.service.assignment(f.teacher, a.id()).title());
        assertThrows(BusinessException.class, () -> f.service.saveAssignment(f.teacher, 0, c.id(), "标题", "说明", open, open, true, criteria));
        assertThrows(BusinessException.class, () -> f.service.saveCourse(f.teacher, 0, " ", "说明"));
        f.service.deleteAssignment(f.teacher, a.id()); f.service.deleteCourse(f.teacher, c.id());
    }
    @Test void reviewDeadlineAndEarlyClosureRules() {
        f.service.allocate(f.teacher, f.readyId, 1); User s = f.students.get(0); ReviewTask t = f.service.tasks(s, f.readyId).get(0);
        assertThrows(BusinessException.class, () -> f.service.closeAssignment(f.teacher, f.readyId));
        Assignment a = f.service.assignment(s, f.readyId);
        assertThrows(BusinessException.class, () -> f.at(a.reviewDeadline()).completeReview(s, t.id(), f.scores(a, "80"), "已截止"));
        f.at(a.reviewDeadline()).closeAssignment(f.teacher, f.readyId);
        assertEquals(Phase.CLOSED, f.service.assignment(f.teacher, f.readyId).phase());
    }
    @Test void concurrentDuplicateReviewOnlyCommitsOnce() throws Exception {
        f.service.allocate(f.teacher, f.readyId, 1); User s = f.students.get(0); ReviewTask t = f.service.tasks(s, f.readyId).get(0);
        Assignment a = f.service.assignment(s, f.readyId); ExecutorService executor = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> submit = () -> { start.await(); try { f.service.completeReview(s, t.id(), f.scores(a, "80"), "并发互评"); return true; } catch (BusinessException e) { return false; } };
        try {
            var first = executor.submit(submit); var second = executor.submit(submit); start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1L, f.db.read(c -> count(c, "SELECT COUNT(*) FROM reviews WHERE task_id=?", t.id())).longValue());
        } finally { executor.shutdownNow(); }
    }
    @Test void concurrentAllocationCannotRegenerateTasks() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> allocate = () -> { start.await(); try { f.service.allocate(f.teacher, f.readyId, 2); return true; } catch (BusinessException e) { return false; } };
        try {
            var first = executor.submit(allocate); var second = executor.submit(allocate); start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(12, f.service.tasks(f.teacher, f.readyId).size());
        } finally { executor.shutdownNow(); }
    }
}
