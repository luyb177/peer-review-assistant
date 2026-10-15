package edu.course.peerreview.scoring;

import edu.course.peerreview.domain.Domain.Criterion;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 策略模式：业务流程依赖评分契约，不依赖具体计算公式。 */
public interface ScoringPolicy {
    void validateCriteria(List<Criterion> criteria);
    BigDecimal calculate(List<Criterion> criteria, Map<Long, BigDecimal> scores);
}
