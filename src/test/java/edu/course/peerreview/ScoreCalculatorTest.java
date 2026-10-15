package edu.course.peerreview;

import edu.course.peerreview.domain.Domain.Criterion;
import edu.course.peerreview.scoring.ScoreCalculator;
import edu.course.peerreview.service.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScoreCalculatorTest {
    final ScoreCalculator calculator = new ScoreCalculator();
    static BigDecimal d(String n) { return new BigDecimal(n); }
    final List<Criterion> rules = List.of(new Criterion(1, "完整性", d("60"), d("0"), d("100")), new Criterion(2, "规范性", d("40"), d("10"), d("20")));
    @ParameterizedTest @CsvSource({"0,10,0.00", "100,20,100.00", "80,15,68.00", "33.33,13.33,33.32"})
    void weightedNormalizedScores(String a, String b, String expected) { assertEquals(d(expected), calculator.calculate(rules, Map.of(1L, d(a), 2L, d(b)))); }
    @ParameterizedTest @CsvSource({"-1,15", "101,15", "80,9.99", "80,20.01", "80.001,15"})
    void rejectsOutOfRangeOrPrecision(String a, String b) { assertThrows(BusinessException.class, () -> calculator.calculate(rules, Map.of(1L, d(a), 2L, d(b)))); }
    @Test void exactSetOfCriteriaRequired() {
        assertThrows(BusinessException.class, () -> calculator.calculate(rules, Map.of(1L, d("80"))));
        assertThrows(BusinessException.class, () -> calculator.calculate(rules, Map.of(1L, d("80"), 2L, d("15"), 3L, d("1"))));
        Map<Long, BigDecimal> scores = new HashMap<>(); scores.put(1L, null); scores.put(2L, d("15"));
        assertThrows(BusinessException.class, () -> calculator.calculate(rules, scores));
    }
    @Test void validatesWeightsNamesAndRanges() {
        assertThrows(BusinessException.class, () -> calculator.validateCriteria(List.of()));
        assertThrows(BusinessException.class, () -> calculator.validateCriteria(List.of(new Criterion(1, "a", d("99.99"), d("0"), d("10")))));
        assertThrows(BusinessException.class, () -> calculator.validateCriteria(List.of(new Criterion(1, "a", d("100"), d("10"), d("10")))));
        assertThrows(BusinessException.class, () -> calculator.validateCriteria(List.of(new Criterion(1, "a", d("50"), d("0"), d("10")), new Criterion(2, "a", d("50"), d("0"), d("10")))));
        assertThrows(BusinessException.class, () -> calculator.validateCriteria(List.of(new Criterion(1, "a", d("0"), d("0"), d("10")))));
    }
    @Test void rejectsDuplicateIdsAtCalculation() {
        var duplicate = List.of(new Criterion(1, "a", d("50"), d("0"), d("100")), new Criterion(1, "b", d("50"), d("0"), d("100")));
        assertThrows(BusinessException.class, () -> calculator.calculate(duplicate, Map.of(1L, d("50"))));
    }
}
