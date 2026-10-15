package edu.course.peerreview;

import edu.course.peerreview.domain.Domain.Criterion;
import edu.course.peerreview.scoring.*;
import edu.course.peerreview.service.PeerReviewService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ScoringPolicyTest {
    @Test void injectablePolicyUsedByBothPreviewAndPersistedReview() {
        TestFixture f = new TestFixture(); AtomicInteger calls = new AtomicInteger(); ScoringPolicy original = new ScoreCalculator();
        ScoringPolicy tracking = new ScoringPolicy() {
            public void validateCriteria(List<Criterion> criteria) { original.validateCriteria(criteria); }
            public BigDecimal calculate(List<Criterion> criteria, Map<Long, BigDecimal> scores) { calls.incrementAndGet(); return original.calculate(criteria, scores); }
        };
        var service = new PeerReviewService(f.db, Clock.systemDefaultZone(), new Random(7), tracking);
        service.allocate(f.teacher, f.readyId, 1); var student = f.students.get(0); var task = service.tasks(student, f.readyId).get(0);
        var criteria = service.assignment(student, f.readyId).criteria(); var scores = f.scores(service.assignment(student, f.readyId), "75");
        assertEquals(service.previewScore(criteria, scores), service.completeReview(student, task.id(), scores, "界面与持久化使用相同策略。"));
        assertEquals(2, calls.get());
        assertEquals(new BigDecimal("75.00"), service.reviewDetail(student, task.id()).total());
    }
}
