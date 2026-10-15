package edu.course.peerreview.scoring;

import edu.course.peerreview.domain.Domain.Criterion;
import edu.course.peerreview.service.BusinessException;
import edu.course.peerreview.service.Validation;
import java.math.*;
import java.util.*;

/** 区间归一化后按百分比权重汇总，最终总分为 0—100。 */
public final class ScoreCalculator implements ScoringPolicy {
    @Override public void validateCriteria(List<Criterion> criteria) {
        if (criteria == null || criteria.isEmpty() || criteria.size() > 20) throw new BusinessException("评分维度须为 1—20 项。");
        Set<String> names = new HashSet<>();
        BigDecimal weights = BigDecimal.ZERO;
        for (Criterion c : criteria) {
            String name = Validation.text(c.name(), "评分项名称", 80);
            if (!names.add(name)) throw new BusinessException("评分项名称不能重复。");
            Validation.decimal(c.weight(), "权重");
            Validation.decimal(c.min(), "最低分");
            Validation.decimal(c.max(), "最高分");
            if (c.weight().signum() <= 0 || c.weight().compareTo(new BigDecimal("100")) > 0) throw new BusinessException("权重须大于 0 且不超过 100。");
            if (c.min().signum() < 0 || c.max().compareTo(c.min()) <= 0) throw new BusinessException("分值区间须满足 0 ≤ 最低分 < 最高分。");
            weights = weights.add(c.weight());
        }
        if (weights.compareTo(new BigDecimal("100")) != 0) throw new BusinessException("各项权重之和必须为 100%。");
    }
    @Override public BigDecimal calculate(List<Criterion> criteria, Map<Long, BigDecimal> scores) {
        validateCriteria(criteria);
        Set<Long> ids = new HashSet<>();
        for (Criterion c : criteria) if (!ids.add(c.id())) throw new BusinessException("评分项编号重复。");
        if (scores == null || !scores.keySet().equals(ids)) throw new BusinessException("必须且只能填写本作业的全部评分项。");
        BigDecimal total = BigDecimal.ZERO;
        for (Criterion c : criteria) {
            BigDecimal score = Validation.decimal(scores.get(c.id()), c.name() + "得分");
            if (score.compareTo(c.min()) < 0 || score.compareTo(c.max()) > 0) throw new BusinessException(c.name() + "得分超出允许区间。");
            BigDecimal normalized = score.subtract(c.min()).divide(c.max().subtract(c.min()), MathContext.DECIMAL128);
            total = total.add(normalized.multiply(c.weight()));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
