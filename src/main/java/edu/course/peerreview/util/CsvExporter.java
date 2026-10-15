package edu.course.peerreview.util;

import edu.course.peerreview.domain.Domain.Statistics;
import edu.course.peerreview.service.BusinessException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class CsvExporter {
    private CsvExporter() { }
    public static String cell(Object value) {
        String text = Objects.toString(value, "");
        String stripped = text.stripLeading();
        if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0) text = "'" + text;
        if (text.startsWith("\t") || text.startsWith("\r")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
    public static void write(Path path, List<Statistics> rows) {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write('﻿');
            out.write("提交编号,学生,已评人数,分配人数,平均分,最高分,最低分\r\n");
            for (Statistics r : rows) {
                Object[] cells = {r.submissionId(), r.author(), r.completed(), r.assigned(), r.average(), r.highest(), r.lowest()};
                out.write(String.join(",", Arrays.stream(cells).map(CsvExporter::cell).toList()));
                out.write("\r\n");
            }
        } catch (IOException e) { throw new BusinessException("CSV 导出失败，请检查路径、权限或文件是否被其他程序占用。", e); }
    }
}
