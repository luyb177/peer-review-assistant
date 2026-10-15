package edu.course.peerreview.dao;

import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.service.BusinessException;
import java.sql.*;
import java.nio.file.Path;
import java.util.Properties;

/** 每个操作独立连接；事务连接由调用方作用域关闭，避免共享单例 Connection。 */
public final class Database {
    @FunctionalInterface public interface ConnectionFactory { Connection open() throws SQLException; }
    @FunctionalInterface public interface Work<T> { T run(Connection connection) throws SQLException; }
    private final ConnectionFactory factory;

    public Database(AppConfig config) {
        this(() -> {
            Properties properties = new Properties();
            properties.setProperty("user", config.get("db.user"));
            properties.setProperty("password", config.get("db.password"));
            if (!config.get("db.trustStore").isBlank()) {
                Path store = config.path().toAbsolutePath().getParent().resolve(config.get("db.trustStore"));
                properties.setProperty("trustCertificateKeyStoreUrl", store.toUri().toString());
                properties.setProperty("trustCertificateKeyStoreType", "PKCS12");
                properties.setProperty("trustCertificateKeyStorePassword", config.get("db.trustStorePassword"));
                properties.setProperty("fallbackToSystemTrustStore", "false");
            }
            return DriverManager.getConnection(config.get("db.url"), properties);
        });
    }
    public Database(ConnectionFactory factory) { this.factory = factory; }
    public <T> T read(Work<T> work) {
        try (Connection connection = factory.open()) { return work.run(connection); }
        catch (SQLException e) { throw failure(e); }
    }
    public <T> T transaction(Work<T> work) {
        try (Connection connection = factory.open()) {
            // 锁等待结束后，后续读取必须看到其他事务刚提交的任务状态。
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                T value = work.run(connection);
                connection.commit();
                return value;
            } catch (SQLException | RuntimeException e) {
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException e) { throw failure(e); }
    }
    public void ping() { read(c -> { try (var s = c.createStatement()) { s.execute("SELECT 1"); } return null; }); }
    private BusinessException failure(SQLException e) {
        String state = e.getSQLState();
        if (state != null && state.startsWith("23")) return new BusinessException("记录冲突或仍被其他数据引用，请刷新后重试。", e);
        return new BusinessException("数据库操作失败，请检查远程地址、网络、TLS、账号权限以及是否已初始化表结构。", e);
    }
}
