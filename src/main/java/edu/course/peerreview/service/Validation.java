package edu.course.peerreview.service;

import java.math.BigDecimal;

public final class Validation {
    private Validation() { }
    public static String text(String value, String name, int max) {
        if (value == null || value.isBlank()) throw new BusinessException(name + "不能为空。");
        String clean = value.strip();
        if (clean.length() > max) throw new BusinessException(name + "不能超过 " + max + " 个字符。");
        return clean;
    }
    public static BigDecimal decimal(BigDecimal value, String name) {
        if (value == null || value.scale() > 2 || value.abs().compareTo(new BigDecimal("100000")) > 0)
            throw new BusinessException(name + "须为绝对值不超过 100000、至多两位小数的数值。");
        return value;
    }
    public static BigDecimal parseDecimal(String text, String name) {
        try { return decimal(new BigDecimal(text.strip()), name); }
        catch (NumberFormatException e) { throw new BusinessException(name + "不是有效数值。"); }
    }
}
