package edu.course.peerreview;

import edu.course.peerreview.dao.Database;
import edu.course.peerreview.service.InitializationService;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class InitializationServiceTest {
    @Test void preCreatedSchemaDoesNotRequireCreateTablePrivilege() {
        TestFixture fixture = new TestFixture();
        String url = fixture.db.read(c -> c.getMetaData().getURL());
        AtomicInteger ddlAttempts = new AtomicInteger();
        Database limited = new Database(() -> {
            Connection delegate = DriverManager.getConnection(url, "sa", "");
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                if (method.getName().equals("createStatement")) {
                    ddlAttempts.incrementAndGet(); throw new SQLException("DDL is not permitted");
                }
                try { return method.invoke(delegate, args); }
                catch (InvocationTargetException e) { throw e.getCause(); }
            });
        });
        new InitializationService(limited).createSchema();
        assertEquals(0, ddlAttempts.get());
    }
}
