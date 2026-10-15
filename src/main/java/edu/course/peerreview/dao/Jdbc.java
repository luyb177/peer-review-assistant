package edu.course.peerreview.dao;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/** PreparedStatement 绑定统一入口，业务 SQL 的值不使用字符串拼接。 */
public final class Jdbc {
    private Jdbc() { }
    @FunctionalInterface public interface Mapper<T> { T map(ResultSet row) throws SQLException; }
    public static <T> List<T> query(Connection c, String sql, Mapper<T> mapper, Object... args) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet rows = statement.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rows.next()) result.add(mapper.map(rows));
                return result;
            }
        }
    }
    public static int update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) { bind(statement, args); return statement.executeUpdate(); }
    }
    public static long insert(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, args);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Missing generated key");
                return keys.getLong(1);
            }
        }
    }
    public static long count(Connection c, String sql, Object... args) throws SQLException {
        return query(c, sql, r -> r.getLong(1), args).get(0);
    }
    private static void bind(PreparedStatement s, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            Object value = args[i];
            if (value instanceof Enum<?> e) s.setString(i + 1, e.name());
            else if (value instanceof LocalDateTime t) s.setTimestamp(i + 1, Timestamp.valueOf(t));
            else s.setObject(i + 1, value);
        }
    }
}
