package edu.course.peerreview.util;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import edu.course.peerreview.service.BusinessException;
import java.util.*;
import java.util.function.*;

/** 字段到提取器的规则表，组合筛选不堆叠课程/作业/状态的 if-else。 */
public final class TableFilter<T> {
    private final Map<String, Function<T, String>> fields;
    public TableFilter(Map<String, Function<T, String>> fields) { this.fields = Map.copyOf(fields); }
    public List<T> apply(List<T> source, Map<String, String> selected, String regex, Function<T, String> searchable) {
        List<Predicate<T>> predicates = new ArrayList<>();
        selected.forEach((key, value) -> {
            Function<T, String> extractor = fields.get(key);
            if (extractor == null) throw new BusinessException("未知筛选字段：" + key);
            if (value != null && !value.isBlank()) predicates.add(item -> value.equals(extractor.apply(item)));
        });
        if (regex != null && !regex.isBlank()) {
            if (regex.length() > 500) throw new BusinessException("正则表达式不能超过 500 个字符。");
            try {
                Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                predicates.add(item -> pattern.matcher(Objects.toString(searchable.apply(item), "")).find());
            } catch (PatternSyntaxException e) { throw new BusinessException("正则表达式不合法；使用 RE2 语法，不支持回溯引用和前后向断言。"); }
        }
        return source.stream().filter(item -> predicates.stream().allMatch(p -> p.test(item))).toList();
    }
}
