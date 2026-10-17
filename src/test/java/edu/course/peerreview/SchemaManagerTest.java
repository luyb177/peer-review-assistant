package edu.course.peerreview;

import edu.course.peerreview.dao.Database;
import edu.course.peerreview.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.sql.DriverManager;
import java.util.UUID;
import static edu.course.peerreview.dao.Jdbc.*;
import static org.junit.jupiter.api.Assertions.*;

class SchemaManagerTest {
    Database empty() { String name = UUID.randomUUID().toString(); return new Database(() -> DriverManager.getConnection("jdbc:h2:mem:"+name+";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "")); }
    @Test void buildsAllMissingTablesAndRechecksWithoutChangingData() {
        Database db = empty(); SchemaManager manager = new SchemaManager(); assertEquals(11, manager.inspect(db).missingTables().size());
        assertTrue(manager.prepare(db).ready()); assertTrue(manager.prepare(db).ready());
        assertEquals(0L, db.read(c -> count(c, "SELECT COUNT(*) FROM users")).longValue());
    }
    @Test void knownAdditiveMigrationKeepsOldScoresAndUsers() {
        TestFixture f = new TestFixture(); f.service.allocate(f.teacher, f.readyId, 1); var student=f.students.get(0); var task=f.service.tasks(student,f.readyId).get(0);
        f.service.completeReview(student,task.id(),f.scores(f.service.assignment(student,f.readyId),"80"),"历史评分");
        f.db.read(c -> { try(var s=c.createStatement()){s.execute("ALTER TABLE reviews DROP COLUMN version");} return null; });
        SchemaManager manager=new SchemaManager(); assertTrue(manager.inspect(f.db).reviewVersionMissing()); assertTrue(manager.prepare(f.db).ready());
        assertEquals(1,f.service.reviewDetail(student,task.id()).version()); assertEquals("历史评分",f.service.reviewDetail(student,task.id()).comment());
        assertEquals(7L,f.db.read(c -> count(c,"SELECT COUNT(*) FROM users")).longValue());
    }
    @Test void incompatibleExistingTableIsRejectedWithoutRecreation() {
        TestFixture f=new TestFixture(); f.db.read(c -> {try(var s=c.createStatement()){s.execute("ALTER TABLE users ALTER COLUMN display_name DROP NOT NULL");}return null;});
        SchemaManager manager=new SchemaManager(); assertFalse(manager.inspect(f.db).conflicts().isEmpty());
        assertThrows(BusinessException.class,() -> manager.prepare(f.db)); assertEquals(7L,f.db.read(c -> count(c,"SELECT COUNT(*) FROM users")).longValue());
    }
    @Test void recreatesOnlyMissingTableAndKeepsOtherRecords() {
        TestFixture f=new TestFixture(); f.db.read(c -> {try(var s=c.createStatement()){s.execute("DROP TABLE operation_logs");}return null;});
        SchemaManager manager=new SchemaManager(); assertEquals(java.util.List.of("operation_logs"),manager.inspect(f.db).missingTables()); assertTrue(manager.prepare(f.db).ready());
        assertEquals(6L,f.db.read(c -> count(c,"SELECT COUNT(*) FROM submissions")).longValue());
    }
    @Test void rejectsMissingVersionDefaultWithoutRewritingExistingColumns() {
        TestFixture f=new TestFixture(); f.db.read(c -> {try(var s=c.createStatement()){s.execute("ALTER TABLE reviews ALTER COLUMN version DROP DEFAULT");}return null;});
        var report=new SchemaManager().inspect(f.db);assertTrue(report.conflicts().stream().anyMatch(s -> s.contains("默认值")));assertThrows(BusinessException.class,() -> new SchemaManager().prepare(f.db));
    }
    @Test void rejectsForeignKeyWithoutRequiredCascade() {
        TestFixture f=new TestFixture();f.db.read(c -> {
            String name;try(var keys=c.getMetaData().getImportedKeys(c.getCatalog(),c.getSchema(),"criteria")){assertTrue(keys.next());name=keys.getString("FK_NAME");}
            assertTrue(name.matches("[A-Za-z0-9_]+"));try(var s=c.createStatement()){s.execute("ALTER TABLE criteria DROP CONSTRAINT \""+name+"\"");s.execute("ALTER TABLE criteria ADD CONSTRAINT replacement_fk FOREIGN KEY(assignment_id) REFERENCES assignments(id)");}return null;
        });
        assertTrue(new SchemaManager().inspect(f.db).conflicts().stream().anyMatch(s -> s.contains("级联删除")));
        assertThrows(BusinessException.class,() -> new SchemaManager().prepare(f.db));
    }
    @ParameterizedTest @ValueSource(strings={"jdbc:mysql://localhost/mysql","jdbc:mysql://localhost/sys","jdbc:mysql://localhost/information_schema","jdbc:mysql://localhost/","jdbc:mysql://root:secret@localhost/course","jdbc:mysql://localhost/a/b","jdbc:h2:mem:other"})
    void rejectsSystemOrUnsafeTargets(String url){assertThrows(BusinessException.class,() -> SchemaManager.validateTarget(url));}
}
