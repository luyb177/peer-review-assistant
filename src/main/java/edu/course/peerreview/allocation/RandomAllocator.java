package edu.course.peerreview.allocation;

import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.service.BusinessException;
import java.util.*;

/** 洗牌后循环错位：随机性 + 每份 N 人 + 无自评/重复 + 评审负载严格均衡。 */
public final class RandomAllocator {
    public List<Allocation> allocate(List<Participant> participants, int reviewersPerSubmission, Random random) {
        if (participants == null || participants.size() < 2) throw new BusinessException("至少需要两名已提交作业的学生。");
        if (reviewersPerSubmission < 1 || reviewersPerSubmission >= participants.size()) throw new BusinessException("每份评审人数 N 须为 1—" + (participants.size() - 1) + "。");
        Set<Long> students = new HashSet<>();
        Set<Long> submissions = new HashSet<>();
        for (Participant p : participants) {
            if (!students.add(p.studentId()) || !submissions.add(p.submissionId())) throw new BusinessException("参与名单包含重复学生或提交。");
        }
        List<Participant> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled, random);
        List<Allocation> result = new ArrayList<>();
        for (int i = 0; i < shuffled.size(); i++) {
            for (int offset = 1; offset <= reviewersPerSubmission; offset++) {
                result.add(new Allocation(shuffled.get(i).submissionId(), shuffled.get((i + offset) % shuffled.size()).studentId()));
            }
        }
        return List.copyOf(result);
    }
}
