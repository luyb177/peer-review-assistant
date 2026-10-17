package edu.course.peerreview;

import edu.course.peerreview.attachment.*;
import edu.course.peerreview.dao.*;
import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.service.*;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import static edu.course.peerreview.dao.Jdbc.*;
import static org.junit.jupiter.api.Assertions.*;

class AttachmentServiceTest {
    TestFixture f;AttachmentService files;Assignment assignment;User student;
    @BeforeEach void setup(){f=new TestFixture();files=new AttachmentService(f.db);assignment=f.service.assignment(f.teacher,f.submissionId);student=f.students.get(0);}
    PreparedAttachment file(String name,String text){return PreparedAttachment.of(name,text.getBytes(StandardCharsets.UTF_8));}
    long submission(){return f.service.submissions(student,assignment.id()).get(0).id();}
    AttachmentInfo original(){return files.ownSubmissionFiles(student,assignment.id()).get(0);}
    void upload(String body,PreparedAttachment file){f.service.submitWithAttachments(student,assignment.id(),body,AttachmentChanges.add(file));}
    @Test void teacherMaterialsAndDocumentOnlyStudentSubmissionRoundTrip()throws Exception{
        var material=PreparedAttachment.of("评分说明.docx",AttachmentDocumentsTest.docx("请分析模块边界和异常恢复"));
        f.service.saveAssignmentWithAttachments(f.teacher,assignment.id(),assignment.courseId(),assignment.title(),assignment.description(),assignment.reviewOpen(),assignment.reviewDeadline(),true,assignment.criteria(),AttachmentChanges.add(material));
        var teacherFile=files.assignmentFiles(student,assignment.id()).get(0);assertEquals("评分说明.docx",teacherFile.name());assertArrayEquals(material.content(),files.download(student,teacherFile.id()).content());
        var own=file("姓名学号-报告.txt","仅附件内容，包含正则查找标记XYZ");upload("",own);assertEquals("",f.service.submissions(student,assignment.id()).get(0).content());
        assertArrayEquals(own.content(),files.download(student,original().id()).content());assertTrue(files.searchableText(student,submission()).contains("XYZ"));
        assertThrows(BusinessException.class,()->files.download(f.students.get(1),original().id()));
        f.asOtherTeacher(other->assertThrows(BusinessException.class,()->files.assignmentFiles(other,assignment.id())));
    }
    @Test void replacementRemovalStaleSnapshotAndEmptySubmissionAreAtomic(){
        upload("original",file("a.txt","old file"));AttachmentInfo old=original();
        var replace=new AttachmentChanges(Set.of(old.id()),Set.of(old.id()),List.of(file("b.txt","new file")));
        f.service.submitWithAttachments(student,assignment.id(),"updated",replace);AttachmentInfo current=original();assertNotEquals(old.id(),current.id());assertThrows(BusinessException.class,()->files.download(student,old.id()));
        assertThrows(BusinessException.class,()->f.service.submitWithAttachments(student,assignment.id(),"stale overwrite",replace));assertEquals("updated",f.service.submissions(student,assignment.id()).get(0).content());
        var remove=new AttachmentChanges(Set.of(current.id()),Set.of(current.id()),List.of());
        assertThrows(BusinessException.class,()->f.service.submitWithAttachments(student,assignment.id()," ",remove));assertEquals(current.id(),original().id());
        f.service.submitWithAttachments(student,assignment.id(),"text remains",remove);assertTrue(files.ownSubmissionFiles(student,assignment.id()).isEmpty());
    }
    @Test void limitsDuplicatesOwnershipAndDeadlineRejectWithoutPartialRows(){
        var four=List.of(file("1.txt","one"),file("2.txt","two"),file("3.txt","three"),file("4.txt","four"));
        assertThrows(BusinessException.class,()->f.service.submitWithAttachments(student,assignment.id(),"body",new AttachmentChanges(Set.of(),Set.of(),four)));
        var same=file("a.txt","same");assertThrows(BusinessException.class,()->f.service.submitWithAttachments(student,assignment.id(),"body",new AttachmentChanges(Set.of(),Set.of(),List.of(same,same))));
        assertTrue(f.service.submissions(student,assignment.id()).isEmpty());
        upload("",file("a.txt","actual"));var edits=new AttachmentChanges(Set.of(original().id()),Set.of(original().id()),List.of());
        assertThrows(BusinessException.class,()->f.service.submitWithAttachments(f.students.get(1),assignment.id(),"steal",edits));
        assertThrows(BusinessException.class,()->f.at(assignment.reviewOpen()).submitWithAttachments(student,assignment.id(),"late",edits));assertEquals(1,files.ownSubmissionFiles(student,assignment.id()).size());
    }
    @Test void blindReviewerGetsAnonymousNamesAndOnlyAssignedDownloadsAndAiText()throws Exception{
        var doc=PreparedAttachment.of("学生真实姓名-123.docx",AttachmentDocumentsTest.docx("本文讨论模块化、测试和异常恢复"));upload("",doc);
        for(int i=1;i<3;i++)f.service.submit(f.students.get(i),assignment.id(),"其他学生的文本");
        PeerReviewService review=f.at(assignment.reviewOpen().plusSeconds(1));review.allocate(f.teacher,assignment.id(),1);
        long sid=submission();ReviewTask t=review.tasks(f.teacher,assignment.id()).stream().filter(x->x.submissionId()==sid).findFirst().orElseThrow();User reviewer=f.students.stream().filter(u->u.id()==t.reviewerId()).findFirst().orElseThrow();
        AttachmentInfo info=files.reviewFiles(reviewer,t.id()).get(0);assertEquals("匿名附件-1.docx",info.name());assertEquals(info.name(),files.download(reviewer,info.id()).info().name());assertArrayEquals(doc.content(),files.download(reviewer,info.id()).content());
        assertEquals(doc.name(),files.submissionFiles(f.teacher,sid).get(0).name());
        User outsider=f.students.subList(0,3).stream().filter(u->u.id()!=reviewer.id()&&u.id()!=student.id()).findFirst().orElseThrow();assertThrows(BusinessException.class,()->files.download(outsider,info.id()));
        assertTrue(review.task(reviewer,t.id()).content().contains("模块化"));var input=review.adviceInput(reviewer,t.id());assertTrue(input.submission().contains("模块化"));assertFalse(input.submission().contains("123.docx"));
    }
    @Test void unreadableOnlyDocumentBlocksAiRatherThanInventingReadContent()throws Exception{
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument()){pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());pdf.save(bytes);}
        upload("",PreparedAttachment.of("scan.pdf",bytes.toByteArray()));f.service.submit(f.students.get(1),assignment.id(),"other");PeerReviewService review=f.at(assignment.reviewOpen().plusSeconds(1));review.allocate(f.teacher,assignment.id(),1);
        ReviewTask task=review.tasks(f.teacher,assignment.id()).stream().filter(t->t.submissionId()==submission()).findFirst().orElseThrow();User actor=f.students.stream().filter(u->u.id()==task.reviewerId()).findFirst().orElseThrow();assertThrows(BusinessException.class,()->review.adviceInput(actor,task.id()));
        assertArrayEquals(bytes.toByteArray(),files.download(actor,original().id()).content());
    }
    @Test void schemaUpgradeCreatesAttachmentTableWithoutLosingOldGrades(){
        f.service.allocate(f.teacher,f.readyId,1);var task=f.service.tasks(student,f.readyId).get(0);f.service.completeReview(student,task.id(),f.scores(f.service.assignment(student,f.readyId),"80"),"grade survives");
        f.db.read(c->{try(var s=c.createStatement()){s.execute("DROP TABLE attachments");}return null;});assertEquals(List.of("attachments"),new SchemaManager().inspect(f.db).missingTables());assertTrue(new SchemaManager().prepare(f.db).ready());assertEquals(new BigDecimal("80.00"),f.service.reviewDetail(student,task.id()).total());
    }
    @Test void sqlFailureRollsBackParentAndAttachmentReplacement(){
        upload("before",file("a.txt","preserved"));AttachmentInfo old=original();String url=f.db.read(c->c.getMetaData().getURL());Database broken=new Database(()->{
            Connection delegate=DriverManager.getConnection(url,"sa","");return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                if(method.getName().equals("prepareStatement")&&args[0].toString().startsWith("INSERT INTO operation_logs"))throw new SQLException("Injected log failure");try{return method.invoke(delegate,args);}catch(InvocationTargetException e){throw e.getCause();}
            });
        });
        var replace=new AttachmentChanges(Set.of(old.id()),Set.of(old.id()),List.of(file("new.txt","should rollback")));
        assertThrows(BusinessException.class,()->new PeerReviewService(broken).submitWithAttachments(student,assignment.id(),"after",replace));assertEquals("before",f.service.submissions(student,assignment.id()).get(0).content());assertEquals(old.id(),original().id());assertEquals("preserved",files.download(student,old.id()).text());
    }
    @Test void teacherCanReplaceRemoveAndDeleteMaterialsBeforeSubmissions(){
        f.service.saveAssignmentWithAttachments(f.teacher,assignment.id(),assignment.courseId(),assignment.title(),assignment.description(),assignment.reviewOpen(),assignment.reviewDeadline(),true,assignment.criteria(),AttachmentChanges.add(file("a.txt","teacher old")));
        AttachmentInfo old=files.assignmentFiles(f.teacher,assignment.id()).get(0);var replace=new AttachmentChanges(Set.of(old.id()),Set.of(old.id()),List.of(file("b.txt","teacher new")));
        Assignment updated=f.service.assignment(f.teacher,assignment.id());f.service.saveAssignmentWithAttachments(f.teacher,updated.id(),updated.courseId(),updated.title(),updated.description(),updated.reviewOpen(),updated.reviewDeadline(),true,updated.criteria(),replace);
        assertEquals("teacher new",files.download(student,files.assignmentFiles(student,assignment.id()).get(0).id()).text());
        f.service.deleteAssignment(f.teacher,assignment.id());assertEquals(0L,f.db.read(c->count(c,"SELECT COUNT(*) FROM attachments WHERE assignment_id=?",assignment.id())).longValue());
    }
    @Test void teacherMaterialsFreezeAndLongCombinedAiInputIsExplicitlyRejected(){
        var teacherDocs=new AttachmentChanges(Set.of(),Set.of(),List.of(file("t1.txt","t".repeat(19000)),file("t2.txt","u".repeat(19000))));
        f.service.saveAssignmentWithAttachments(f.teacher,assignment.id(),assignment.courseId(),assignment.title(),assignment.description(),assignment.reviewOpen(),assignment.reviewDeadline(),true,assignment.criteria(),teacherDocs);
        f.service.submitWithAttachments(student,assignment.id(),"",new AttachmentChanges(Set.of(),Set.of(),List.of(file("s1.txt","s".repeat(19000)),file("s2.txt","r".repeat(19000)))));
        var existing=files.assignmentFiles(f.teacher,assignment.id());Set<Long> ids=new HashSet<>();existing.forEach(x->ids.add(x.id()));Assignment latest=f.service.assignment(f.teacher,assignment.id());
        assertThrows(BusinessException.class,()->f.service.saveAssignmentWithAttachments(f.teacher,latest.id(),latest.courseId(),latest.title(),latest.description(),latest.reviewOpen(),latest.reviewDeadline(),true,latest.criteria(),new AttachmentChanges(ids,ids,List.of())));
        f.service.submit(f.students.get(1),assignment.id(),"other");PeerReviewService review=f.at(assignment.reviewOpen().plusSeconds(1));review.allocate(f.teacher,assignment.id(),1);ReviewTask task=review.tasks(f.teacher,assignment.id()).stream().filter(t->t.submissionId()==submission()).findFirst().orElseThrow();User reviewer=f.students.stream().filter(u->u.id()==task.reviewerId()).findFirst().orElseThrow();
        assertThrows(BusinessException.class,()->review.adviceInput(reviewer,task.id()));assertEquals(2,files.assignmentFiles(student,assignment.id()).size());
    }
    @Test void concurrentAttachmentReplacementFromSameSnapshotOnlyCommitsOnce()throws Exception{
        upload("before",file("a.txt","old"));long id=original().id();ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try{List<Future<Boolean>> result=new ArrayList<>();for(String mark:List.of("first","second"))result.add(pool.submit(()->{gate.await();try{f.service.submitWithAttachments(student,assignment.id(),mark,new AttachmentChanges(Set.of(id),Set.of(id),List.of(file(mark+".txt",mark))));return true;}catch(BusinessException expected){return false;}}));gate.countDown();assertNotEquals(result.get(0).get(10,TimeUnit.SECONDS),result.get(1).get(10,TimeUnit.SECONDS));assertEquals(1,files.ownSubmissionFiles(student,assignment.id()).size());assertEquals(f.service.submissions(student,assignment.id()).get(0).content(),files.download(student,original().id()).text());}finally{pool.shutdownNow();}
    }
}
