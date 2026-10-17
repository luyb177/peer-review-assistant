package edu.course.peerreview.service;

import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.dao.Database;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

/** Non-destructive schema inspection. Only missing tables and known additive migrations are automatic. */
public final class SchemaManager {
    private record Column(String name, String type, int size, int scale, boolean required, boolean identity) { }
    private record Table(String name, String ddl, List<Column> columns, Set<String> primary, List<Set<String>> unique, Set<String> foreign) { }
    public record Report(List<String> missingTables, boolean reviewVersionMissing, List<String> conflicts) {
        public Report { missingTables = List.copyOf(missingTables); conflicts = List.copyOf(conflicts); }
        public boolean ready() { return missingTables.isEmpty() && !reviewVersionMissing && conflicts.isEmpty(); }
        public String summary() {
            if (ready()) return "连接成功，业务表及必要字段、主键、唯一约束、外键检查通过。";
            return "缺失表：" + missingTables + (reviewVersionMissing ? "；需补 reviews.version" : "") + "；结构冲突：" + conflicts;
        }
    }
    private final List<Table> expected = definitions();
    public Report inspect(Database database) { return database.read(this::inspect); }
    public Report prepare(Database database) {
        Report before = inspect(database);
        if (!before.conflicts().isEmpty()) throw new BusinessException("目标库存在不兼容结构，未删除或覆盖任何表。" + before.summary() + " 请使用空的专用库或请管理员迁移。");
        if (before.ready()) return before;
        try {
            database.read(c -> {
                try (Statement statement = c.createStatement()) {
                    for (Table table : expected) if (before.missingTables().contains(table.name())) statement.execute(table.ddl());
                    if (before.reviewVersionMissing()) statement.execute("ALTER TABLE reviews ADD COLUMN version BIGINT NOT NULL DEFAULT 1");
                }
                return null;
            });
        } catch (BusinessException e) { throw new BusinessException("自动准备表结构失败，未清空数据。请检查专用库的 CREATE/REFERENCES 权限；旧版版本字段升级还需要 ALTER 权限。修复后可以重试。", e); }
        Report result = inspect(database);
        if (!result.ready()) throw new BusinessException("表结构尚未准备完成：" + result.summary());
        return result;
    }
    public Report prepare(AppConfig candidate) {
        validateTarget(candidate.get("db.url"));
        Database database = new Database(candidate.copy());
        try { return prepare(database); }
        catch (BusinessException e) {
            if (!missingDatabase(e)) throw e;
            URI uri = URI.create(candidate.get("db.url").substring(5));
            String name = uri.getPath().substring(1);
            AppConfig server = candidate.copy();
            server.set("db.url", "jdbc:mysql://" + uri.getRawAuthority() + "/" + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
            new Database(server).read(c -> { try (Statement s = c.createStatement()) { s.execute("CREATE DATABASE IF NOT EXISTS `" + name + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); } return null; });
            return prepare(database);
        }
    }
    public static void validateTarget(String value) {
        try {
            if (!value.startsWith("jdbc:mysql://")) throw new IllegalArgumentException();
            URI uri = URI.create(value.substring(5)); String name = uri.getPath().substring(1);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null || !name.matches("[A-Za-z0-9_]{1,64}")
                    || Set.of("mysql", "sys", "information_schema", "performance_schema").contains(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException();
        } catch (RuntimeException e) { throw new BusinessException("请填写指向专用库的 jdbc:mysql://主机:端口/库名 地址；不能使用系统库或在地址中内嵌账号。"); }
    }
    private boolean missingDatabase(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) if (t instanceof SQLException sql && sql.getErrorCode() == 1049) return true;
        return false;
    }
    private Report inspect(Connection c) throws SQLException {
        DatabaseMetaData meta = c.getMetaData(); String catalog = c.getCatalog(), schema = c.getSchema();
        Map<String,String> tables = new HashMap<>();
        try (ResultSet rows = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) { while (rows.next()) tables.put(lower(rows.getString("TABLE_NAME")), rows.getString("TABLE_NAME")); }
        List<String> missing = new ArrayList<>(), conflicts = new ArrayList<>(); boolean version = false;
        for (Table table : expected) {
            String actual = tables.get(table.name()); if (actual == null) { missing.add(table.name()); continue; }
            Map<String,Column> columns = new HashMap<>(); Map<String,String> defaults = new HashMap<>();
            try (ResultSet rows = meta.getColumns(catalog, schema, actual, "%")) {
                while (rows.next()) {
                    String n = lower(rows.getString("COLUMN_NAME"));
                    defaults.put(n, rows.getString("COLUMN_DEF"));
                    columns.put(n, new Column(n, rows.getString("TYPE_NAME").toUpperCase(Locale.ROOT), rows.getInt("COLUMN_SIZE"), rows.getInt("DECIMAL_DIGITS"), rows.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls, "YES".equals(rows.getString("IS_AUTOINCREMENT"))));
                    boolean known = table.columns().stream().anyMatch(col -> col.name().equals(n));
                    if (!known && rows.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls && rows.getString("COLUMN_DEF") == null && !"YES".equals(rows.getString("IS_AUTOINCREMENT")) && !"YES".equals(rows.getString("IS_GENERATEDCOLUMN"))) conflicts.add(table.name()+"."+n+" 为额外必填字段");
                }
            }
            for (Column required : table.columns()) {
                Column got = columns.get(required.name());
                if (got == null) {
                    if (table.name().equals("reviews") && required.name().equals("version")) version = true;
                    else conflicts.add(table.name()+"."+required.name()+" 缺失");
                } else if (!compatible(required, got)) conflicts.add(table.name()+"."+required.name()+" 类型/长度/非空或自增属性不兼容");
            }
            if (table.name().equals("reviews") && columns.containsKey("version") && !"1".equals(Objects.toString(defaults.get("version"), "").replace("(", "").replace(")", "").strip()))
                conflicts.add("reviews.version 默认值必须为 1");
            Set<String> primary = new HashSet<>(); try (ResultSet rows = meta.getPrimaryKeys(catalog, schema, actual)) { while (rows.next()) primary.add(lower(rows.getString("COLUMN_NAME"))); }
            if (!primary.equals(table.primary())) conflicts.add(table.name()+" 主键不一致");
            Map<String,Set<String>> indexes = new HashMap<>();
            try (ResultSet rows = meta.getIndexInfo(catalog, schema, actual, true, false)) { while (rows.next()) if (rows.getString("COLUMN_NAME") != null) indexes.computeIfAbsent(rows.getString("INDEX_NAME"), k -> new HashSet<>()).add(lower(rows.getString("COLUMN_NAME"))); }
            for (Set<String> unique : table.unique()) if (!indexes.containsValue(unique)) conflicts.add(table.name()+" 缺少唯一约束 "+unique);
            Set<String> foreign = new HashSet<>();
            try (ResultSet rows = meta.getImportedKeys(catalog, schema, actual)) {
                while (rows.next()) {
                    String column = lower(rows.getString("FKCOLUMN_NAME"));
                    foreign.add(column+"->"+lower(rows.getString("PKTABLE_NAME"))+"."+lower(rows.getString("PKCOLUMN_NAME")));
                    if (((table.name().equals("criteria") && column.equals("assignment_id")) || (table.name().equals("attachments") && (column.equals("assignment_id") || column.equals("submission_id")))) && rows.getShort("DELETE_RULE") != DatabaseMetaData.importedKeyCascade)
                        conflicts.add(table.name()+"."+column+" 必须保留级联删除约束");
                }
            }
            for (String key : table.foreign()) if (!foreign.contains(key)) conflicts.add(table.name()+" 缺少外键 "+key);
        }
        return new Report(missing, version, conflicts);
    }
    private boolean compatible(Column want, Column got) {
        String type = got.type().replaceAll("\\(.*", "");
        boolean good = switch (want.type()) {
            case "BOOLEAN" -> Set.of("BOOLEAN", "BOOL", "TINYINT", "BIT").contains(type);
            case "TEXT", "MEDIUMTEXT" -> (Set.of("TEXT", "MEDIUMTEXT", "LONGTEXT", "CHARACTER LARGE OBJECT", "CLOB").contains(type) && (!want.type().equals("MEDIUMTEXT") || !type.equals("TEXT")))
                    || (Set.of("VARCHAR", "CHARACTER VARYING").contains(type) && got.size() >= (want.type().equals("MEDIUMTEXT") ? 16777215 : 65535));
            case "MEDIUMBLOB" -> Set.of("MEDIUMBLOB", "LONGBLOB", "BINARY LARGE OBJECT").contains(type)
                    || (Set.of("BLOB", "VARBINARY", "BINARY VARYING").contains(type) && got.size() >= 5242880);
            case "VARCHAR" -> Set.of("VARCHAR", "CHARACTER VARYING").contains(type) && got.size() >= want.size();
            case "DECIMAL" -> Set.of("DECIMAL", "NUMERIC").contains(type) && got.scale() == want.scale() && got.size() >= want.size();
            case "TIMESTAMP" -> Set.of("TIMESTAMP", "DATETIME").contains(type);
            default -> want.type().equals(type);
        };
        return good && (!want.required() || got.required()) && (!want.identity() || got.identity());
    }
    private List<Table> definitions() {
        String sql;
        try (InputStream input = SchemaManager.class.getResourceAsStream("/schema.sql")) { if (input == null) throw new IOException(); sql = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
        catch (IOException e) { throw new IllegalStateException("Missing schema resource", e); }
        List<Table> tables = new ArrayList<>();
        for (String ddl : sql.split(";")) {
            if (ddl.isBlank()) continue;
            Matcher name = Pattern.compile("CREATE TABLE IF NOT EXISTS ([a-z_]+)").matcher(ddl); if (!name.find()) throw new IllegalStateException("Unsupported DDL");
            List<Column> columns = new ArrayList<>(); Set<String> primary = new HashSet<>(), foreign = new HashSet<>(); List<Set<String>> unique = new ArrayList<>();
            for (String raw : ddl.split("\\R")) {
                String line = raw.strip(); Matcher column = Pattern.compile("^([a-z_][a-z0-9_]*) ([A-Z]+)(?:\\((\\d+)(?:,(\\d+))?\\))?").matcher(line);
                if (column.find()) {
                    boolean pk = line.contains("PRIMARY KEY"); String n = column.group(1);
                    columns.add(new Column(n, column.group(2), column.group(3)==null?0:Integer.parseInt(column.group(3)), column.group(4)==null?0:Integer.parseInt(column.group(4)), pk || line.contains("NOT NULL"), line.contains("AUTO_INCREMENT")));
                    if (pk) primary.add(n); if (line.contains("UNIQUE")) unique.add(Set.of(n));
                }
                Matcher key = Pattern.compile("(?:PRIMARY KEY|UNIQUE) \\(([^)]+)\\)").matcher(line);
                if (key.find()) { Set<String> fields = new HashSet<>(); for (String n : key.group(1).split(",")) fields.add(n.strip()); if (line.startsWith("PRIMARY")) primary.addAll(fields); else unique.add(fields); }
                Matcher fk = Pattern.compile("FOREIGN KEY \\(([^)]+)\\) REFERENCES ([a-z_]+)\\(([^)]+)\\)").matcher(line);
                if (fk.find()) foreign.add(fk.group(1)+"->"+fk.group(2)+"."+fk.group(3));
            }
            tables.add(new Table(name.group(1), ddl, columns, primary, unique, foreign));
        }
        return List.copyOf(tables);
    }
    private static String lower(String value) { return value.toLowerCase(Locale.ROOT); }
}
