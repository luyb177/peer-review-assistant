package edu.course.peerreview;

import edu.course.peerreview.allocation.RandomAllocator;
import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.service.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

class RandomAllocatorTest {
    final RandomAllocator allocator = new RandomAllocator();
    @Test void invariantsAcrossSizesSeedsAndReviewerCounts() {
        for (int size = 2; size <= 16; size++) {
            List<Participant> participants = IntStream.range(0, size).mapToObj(i -> new Participant(i + 100, i + 1)).toList();
            for (int n = 1; n < size; n++) for (int seed = 0; seed < 8; seed++) {
                List<Allocation> result = allocator.allocate(participants, n, new Random(seed));
                assertEquals(size * n, result.size()); assertEquals(result.size(), new HashSet<>(result).size());
                for (Participant p : participants) {
                    assertEquals(n, result.stream().filter(a -> a.submissionId() == p.submissionId()).count());
                    assertEquals(n, result.stream().filter(a -> a.reviewerId() == p.studentId()).count());
                    assertFalse(result.contains(new Allocation(p.submissionId(), p.studentId())));
                }
            }
        }
    }
    @Test void rejectsImpossibleAndDuplicateParticipants() {
        List<Participant> pair = List.of(new Participant(1, 10), new Participant(2, 20));
        for (int n : new int[]{-1, 0, 2, 3}) assertThrows(BusinessException.class, () -> allocator.allocate(pair, n, new Random()));
        assertThrows(BusinessException.class, () -> allocator.allocate(List.of(), 1, new Random()));
        assertThrows(BusinessException.class, () -> allocator.allocate(List.of(new Participant(1, 10)), 1, new Random()));
        assertThrows(BusinessException.class, () -> allocator.allocate(List.of(new Participant(1, 10), new Participant(2, 10)), 1, new Random()));
        assertThrows(BusinessException.class, () -> allocator.allocate(List.of(new Participant(1, 10), new Participant(1, 20)), 1, new Random()));
    }
    @Test void inputUnchangedAndDeterministicWithSeed() {
        var input = List.of(new Participant(1, 10), new Participant(2, 20), new Participant(3, 30));
        assertEquals(allocator.allocate(input, 1, new Random(1)), allocator.allocate(input, 1, new Random(1)));
        assertEquals(1, input.get(0).submissionId());
    }
}
