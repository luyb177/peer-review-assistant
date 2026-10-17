package edu.course.peerreview;

import edu.course.peerreview.dao.*;
import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static edu.course.peerreview.dao.Jdbc.*;
import static org.junit.jupiter.api.Assertions.*;

class ReviewMutationTest {
    TestFixture f;
    User student;
    Assignment assignment;
    ReviewTask task;
    ReviewDetail original;

    @BeforeEach void setup() {
        f = new TestFixture(); f.service.allocate(f.teacher, f.readyId, 2);
        student = f.students.get(0); assignment = f.service.assignment(student, f.readyId);
        task = f.service.tasks(student, f.readyId).get(0);
        f.service.saveAdvice(student, task.id(), "保留的参考意见", "test-double");
        f.service.completeReview(student, task.id(), f.scores(assignment, "80"), "原始人工评语");
        original = f.service.reviewDetail(student, task.id());
    }
    BigDecimal change(PeerReviewService service, String score) {
        return service.updateReview(student, task.id(), original.reviewId(), original.version(), f.scores(assignment, score), "修改后的人工评语");
    }
    void withdraw(PeerReviewService service) { service.withdrawReview(student, task.id(), original.reviewId(), original.version()); }
    Statistics stat() { return f.service.statistics(f.teacher, f.readyId).stream().filter(s -> s.submissionId() == task.submissionId()).findFirst().orElseThrow(); }
    long countRows(String table) { return f.db.read(c -> count(c, "SELECT COUNT(*) FROM " + table)); }

    @Test void updateReplacesDetailsAndUpdatesVersionTotalStatisticsAndAudit() {
        assertEquals(new BigDecimal("65.00"), change(f.service, "65"));
        ReviewDetail updated = f.service.reviewDetail(student, task.id());
        assertEquals(original.reviewId(), updated.reviewId()); assertEquals(2L, updated.version());
        assertEquals("修改后的人工评语", updated.comment());
        assertEquals(f.scores(assignment, "65.00"), updated.scores());
        assertEquals(TaskStatus.COMPLETED, f.service.task(student, task.id()).status());
        assertEquals(1, countRows("reviews")); assertEquals(3, countRows("review_scores"));
        assertEquals(1, stat().completed()); assertEquals(new BigDecimal("65.00"), stat().average());
        assertEquals(new BigDecimal("65.00"), stat().highest()); assertEquals(stat().highest(), stat().lowest());
        assertEquals(1L, f.db.read(c -> count(c, "SELECT COUNT(*) FROM operation_logs WHERE action='UPDATE_REVIEW'")).longValue());
        assertEquals("保留的参考意见", f.service.advice(student, task.id()).text());
    }
    @Test void withdrawalRemovesHumanRowsRestoresPendingPreservesAdviceAndAllowsResubmit() {
        withdraw(f.service);
        assertNull(f.service.reviewDetail(student, task.id()));
        ReviewTask pending = f.service.task(student, task.id());
        assertEquals(TaskStatus.PENDING, pending.status()); assertNull(pending.total()); assertNull(pending.comment());
        assertEquals(0, countRows("reviews")); assertEquals(0, countRows("review_scores"));
        assertEquals(0, stat().completed()); assertEquals(2, stat().assigned());
        assertNull(stat().average()); assertNull(stat().highest()); assertNull(stat().lowest());
        assertEquals("保留的参考意见", f.service.advice(student, task.id()).text());
        assertEquals(1L, f.db.read(c -> count(c, "SELECT COUNT(*) FROM operation_logs WHERE action='WITHDRAW_REVIEW'")).longValue());
        f.service.completeReview(student, task.id(), f.scores(assignment, "90"), "重新独立填写的评语");
        ReviewDetail replacement = f.service.reviewDetail(student, task.id());
        assertNotEquals(original.reviewId(), replacement.reviewId()); assertEquals(1, replacement.version());
        assertEquals(new BigDecimal("90.00"), stat().average());
    }
    @Test void aggregatesImmediatelyReflectMutationWithAnotherReview() {
        long reviewerId = f.db.read(c -> query(c, "SELECT reviewer_id FROM review_tasks WHERE submission_id=? AND id<>?", r -> r.getLong(1), task.submissionId(), task.id()).get(0));
        User other = f.students.stream().filter(s -> s.id() == reviewerId).findFirst().orElseThrow();
        ReviewTask otherTask = f.service.tasks(other, f.readyId).stream().filter(t -> t.submissionId() == task.submissionId()).findFirst().orElseThrow();
        f.service.completeReview(other, otherTask.id(), f.scores(assignment, "60"), "另一名学生的人工评语");
        change(f.service, "90"); assertEquals(new BigDecimal("75.00"), stat().average());
        assertEquals(new BigDecimal("90.00"), stat().highest()); assertEquals(new BigDecimal("60.00"), stat().lowest());
        ReviewDetail latest = f.service.reviewDetail(student, task.id());
        f.service.withdrawReview(student, task.id(), latest.reviewId(), latest.version());
        assertEquals(1, stat().completed()); assertEquals(new BigDecimal("60.00"), stat().average());
        assertEquals(stat().average(), stat().highest()); assertEquals(stat().average(), stat().lowest());
    }
    @Test void otherStudentAndTeacherCannotMutateTheReview() {
        User other = f.students.get(1);
        for (User actor : List.of(other, f.teacher)) {
            assertThrows(BusinessException.class, () -> f.service.updateReview(actor, task.id(), original.reviewId(), original.version(), f.scores(assignment, "60"), "不允许"));
            assertThrows(BusinessException.class, () -> f.service.withdrawReview(actor, task.id(), original.reviewId(), original.version()));
        }
        assertEquals(original, f.service.reviewDetail(student, task.id()));
    }
    @Test void pendingTaskCannotBeUpdatedOrWithdrawn() {
        ReviewTask pending = f.service.tasks(student, f.readyId).stream().filter(t -> t.status() == TaskStatus.PENDING).findFirst().orElseThrow();
        assertThrows(BusinessException.class, () -> f.service.updateReview(student, pending.id(), original.reviewId(), original.version(), f.scores(assignment, "60"), "不允许"));
        assertThrows(BusinessException.class, () -> f.service.withdrawReview(student, pending.id(), original.reviewId(), original.version()));
        assertEquals(TaskStatus.PENDING, f.service.task(student, pending.id()).status());
    }
    @Test void invalidUpdatePreservesAllExistingData() {
        assertThrows(BusinessException.class, () -> change(f.service, "101"));
        assertThrows(BusinessException.class, () -> f.service.updateReview(student, task.id(), original.reviewId(), original.version(), Map.of(), "缺项"));
        assertThrows(BusinessException.class, () -> f.service.updateReview(student, task.id(), original.reviewId(), original.version(), f.scores(assignment, "60"), " "));
        assertEquals(original, f.service.reviewDetail(student, task.id())); assertEquals(3, countRows("review_scores"));
        assertEquals(0L, f.db.read(c -> count(c, "SELECT COUNT(*) FROM operation_logs WHERE action='UPDATE_REVIEW'")).longValue());
    }
    @Test void timeWindowAndClosedStatePreventMutations() {
        for (LocalDateTime time : List.of(assignment.reviewOpen().minusNanos(1), assignment.reviewDeadline(), assignment.reviewDeadline().plusSeconds(1))) {
            PeerReviewService clocked = f.at(time);
            assertThrows(BusinessException.class, () -> change(clocked, "60")); assertThrows(BusinessException.class, () -> withdraw(clocked));
        }
        f.db.transaction(c -> { update(c, "UPDATE assignments SET phase='CLOSED' WHERE id=?", f.readyId); return null; });
        assertThrows(BusinessException.class, () -> change(f.service, "60")); assertThrows(BusinessException.class, () -> withdraw(f.service));
        assertEquals(original, f.service.reviewDetail(student, task.id()));
    }
    @Test void updatedSnapshotRejectsStaleEditAndWithdrawal() {
        change(f.service, "70");
        assertThrows(BusinessException.class, () -> change(f.service, "60")); assertThrows(BusinessException.class, () -> withdraw(f.service));
        assertEquals(new BigDecimal("70.00"), f.service.reviewDetail(student, task.id()).total());
        ReviewDetail latest = f.service.reviewDetail(student, task.id());
        f.service.updateReview(student, task.id(), latest.reviewId(), latest.version(), f.scores(assignment, "55"), "新窗口的修改");
        assertEquals(3, f.service.reviewDetail(student, task.id()).version());
    }
    @Test void oldIdentityCannotModifyOrWithdrawResubmittedReview() {
        withdraw(f.service);
        assertThrows(BusinessException.class, () -> withdraw(f.service));
        f.service.completeReview(student, task.id(), f.scores(assignment, "80"), original.comment());
        assertThrows(BusinessException.class, () -> change(f.service, "60")); assertThrows(BusinessException.class, () -> withdraw(f.service));
        assertNotEquals(original.reviewId(), f.service.reviewDetail(student, task.id()).reviewId());
    }
    @ParameterizedTest @ValueSource(strings = {"update-update", "update-withdraw", "withdraw-withdraw"})
    void concurrentRequestsFromSameSnapshotOnlyCommitOnce(String operations) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch gate = new CountDownLatch(1);
        String[] actions = operations.split("-");
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (String action : actions) futures.add(pool.submit(() -> {
                gate.await(); try { if (action.equals("update")) change(f.service, "65"); else withdraw(f.service); return true; }
                catch (BusinessException expected) { return false; }
            }));
            gate.countDown(); assertNotEquals(futures.get(0).get(10, TimeUnit.SECONDS), futures.get(1).get(10, TimeUnit.SECONDS));
            ReviewDetail current = f.service.reviewDetail(student, task.id());
            if (current == null) { assertEquals(TaskStatus.PENDING, f.service.task(student, task.id()).status()); assertEquals(0, countRows("review_scores")); assertEquals(0, stat().completed()); }
            else { assertEquals(2, current.version()); assertEquals(TaskStatus.COMPLETED, f.service.task(student, task.id()).status()); assertEquals(3, countRows("review_scores")); assertEquals(1, stat().completed()); }
            assertEquals(1L, f.db.read(c -> count(c, "SELECT COUNT(*) FROM operation_logs WHERE action IN ('UPDATE_REVIEW','WITHDRAW_REVIEW')")).longValue());
        } finally { pool.shutdownNow(); }
    }
    @ParameterizedTest @ValueSource(strings = {"update", "withdraw"})
    void lateSqlFailureRollsBackRowsStatusAndAudit(String operation) {
        String url = f.db.read(c -> c.getMetaData().getURL());
        Database failing = new Database(() -> {
            Connection connection = DriverManager.getConnection(url, "sa", "");
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                if (method.getName().equals("prepareStatement") && args[0].toString().startsWith("INSERT INTO operation_logs")) throw new SQLException("Injected audit failure");
                try { return method.invoke(connection, args); } catch (InvocationTargetException e) { throw e.getCause(); }
            });
        });
        PeerReviewService service = new PeerReviewService(failing);
        if (operation.equals("update")) assertThrows(BusinessException.class, () -> change(service, "60"));
        else assertThrows(BusinessException.class, () -> withdraw(service));
        assertEquals(original, f.service.reviewDetail(student, task.id()));
        assertEquals(TaskStatus.COMPLETED, f.service.task(student, task.id()).status());
        assertEquals(1, countRows("reviews")); assertEquals(3, countRows("review_scores")); assertEquals(new BigDecimal("80.00"), stat().average());
        assertEquals(0L, f.db.read(c -> count(c, "SELECT COUNT(*) FROM operation_logs WHERE action IN ('UPDATE_REVIEW','WITHDRAW_REVIEW')")).longValue());
    }
}
