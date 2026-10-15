package edu.course.peerreview;

import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.domain.Domain.Statistics;
import edu.course.peerreview.service.*;
import edu.course.peerreview.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UtilitiesTest {
    @TempDir Path temp;
    @Test void passwordSaltAndVerification() {
        String a = Passwords.hash("Secure123!".toCharArray()), b = Passwords.hash("Secure123!".toCharArray());
        assertNotEquals(a, b); assertTrue(Passwords.verify("Secure123!".toCharArray(), a)); assertFalse(Passwords.verify("Wrong123!".toCharArray(), a));
        assertFalse(Passwords.verify("x".toCharArray(), "malformed")); assertThrows(BusinessException.class, () -> Passwords.hash("short".toCharArray()));
    }
    record Row(String course, String assignment, String state, String text) { }
    @Test void tableDrivenCombinedFiltersAndRegex() {
        var filter = new TableFilter<Row>(Map.of("course", Row::course, "assignment", Row::assignment, "state", Row::state));
        var rows = List.of(new Row("1", "2", "PENDING", "JUnit 边界测试"), new Row("1", "2", "COMPLETED", "评分参考"), new Row("2", "3", "PENDING", "junit"));
        assertEquals(List.of(rows.get(0)), filter.apply(rows, Map.of("course", "1", "assignment", "2", "state", "PENDING"), "junit.*测试", Row::text));
        assertEquals(3, filter.apply(rows, Map.of("course", ""), "", Row::text).size());
        assertThrows(BusinessException.class, () -> filter.apply(rows, Map.of(), "[", Row::text));
        assertThrows(BusinessException.class, () -> filter.apply(rows, Map.of("unknown", "x"), "", Row::text));
        assertEquals(0, filter.apply(rows, Map.of(), "(a+)+$", Row::text).size());
    }
    @Test void csvEscapesChineseCommaQuotesNewlinesAndFormulas() throws Exception {
        assertEquals("\"a,\"\"b\"\"\n中\"", CsvExporter.cell("a,\"b\"\n中"));
        assertEquals("\"'=1+1\"", CsvExporter.cell("=1+1"));
        assertEquals("\"'  @SUM(A1)\"", CsvExporter.cell("  @SUM(A1)"));
        Path csv = temp.resolve("results.csv"); CsvExporter.write(csv, List.of(new Statistics(1, "中文学生", 0, 2, null, null, null)));
        String content = Files.readString(csv); assertTrue(content.startsWith("﻿")); assertTrue(content.contains("中文学生")); assertFalse(content.contains("null"));
    }
    @Test void configUtf8AtomicSaveAndTimeoutValidation() {
        Path file = temp.resolve("config/application.properties"); AppConfig c = new AppConfig(file); c.set("db.user", "课堂用户"); c.save();
        assertEquals("课堂用户", new AppConfig(file).get("db.user"));
        c.set("ai.timeoutSeconds", "0"); assertThrows(BusinessException.class, c::timeoutSeconds);
        c.set("ai.timeoutSeconds", "oops"); assertThrows(BusinessException.class, c::timeoutSeconds);
        assertFalse(new AppConfig(temp.resolve("absent.properties")).aiEnabled());
    }
    @Test void studentRenameDeletionAndHistoricalProtection() {
        TestFixture f = new TestFixture();
        f.auth.createStudent(f.teacher, "unused", "未使用学生", "Student123!".toCharArray());
        var student = f.auth.login("unused", "Student123!".toCharArray());
        f.auth.renameStudent(f.teacher, student.id(), "修改后的姓名");
        assertEquals("修改后的姓名", f.auth.users(f.teacher).stream().filter(u -> u.id() == student.id()).findFirst().orElseThrow().name());
        assertThrows(BusinessException.class, () -> f.auth.deleteStudent(student, student.id()));
        assertThrows(BusinessException.class, () -> f.auth.deleteStudent(f.teacher, f.students.get(0).id()));
        assertThrows(BusinessException.class, () -> f.auth.deleteStudent(f.teacher, f.teacher.id()));
        f.auth.deleteStudent(f.teacher, student.id());
        assertThrows(BusinessException.class, () -> f.auth.login("unused", "Student123!".toCharArray()));
    }
    @Test void authenticationAndInitGuard() {
        TestFixture f = new TestFixture();
        char[] wrong = "Wrong123!".toCharArray(); assertThrows(BusinessException.class, () -> f.auth.login("teacher", wrong)); for (char ch : wrong) assertEquals('\0', ch);
        assertThrows(BusinessException.class, () -> f.auth.login("' OR 1=1 --", "x".toCharArray()));
        assertThrows(BusinessException.class, () -> f.auth.users(f.students.get(0)));
        f.auth.createStudent(f.teacher, "newstudent", "新同学", "Student123!".toCharArray());
        var actor = f.auth.login("newstudent", "Student123!".toCharArray());
        f.auth.changePassword(actor, "Student123!".toCharArray(), "Changed123!".toCharArray());
        assertEquals(actor.id(), f.auth.login("newstudent", "Changed123!".toCharArray()).id());
        assertThrows(BusinessException.class, () -> new InitializationService(f.db).initialize("teacher", "教师", "Teacher123!".toCharArray(), true));
    }
}
