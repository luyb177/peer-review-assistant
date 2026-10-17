package edu.course.peerreview.ui;

import edu.course.peerreview.App;
import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.dao.Database;
import edu.course.peerreview.service.*;
import static edu.course.peerreview.dao.Jdbc.*;
import java.nio.file.*;
import java.util.*;

/** Separate-JVM, read-only verification of the completed packaged live batch. */
public final class PackagedPersistenceProbe {
    public static void main(String[] args) throws Exception {
        Properties manifest=new Properties();try(var reader=Files.newBufferedReader(Path.of(args[1]))){manifest.load(reader);}
        AppConfig config=new AppConfig(Path.of(args[0]));Database db=new Database(config);
        var teacher=new AuthService(db).login(config.get("acceptance.teacher.username"),config.get("acceptance.teacher.password").toCharArray());
        long id=Long.parseLong(manifest.getProperty("assignmentId"));
        var service=new PeerReviewService(db);var files=new AttachmentService(db);
        var stats=service.statistics(teacher,id);
        require(stats.size()==3&&stats.stream().allMatch(s->s.completed()==2),"Scores did not survive JVM restart");
        require(stats.stream().map(s->s.average().toPlainString()).sorted().toList().equals(List.of("78.50","81.50","85.00")),"Persistent mean scores differ");
        long reviews=db.read(c->count(c,"SELECT COUNT(*) FROM reviews r JOIN review_tasks t ON t.id=r.task_id JOIN submissions s ON s.id=t.submission_id WHERE s.assignment_id=?",id));
        long advice=db.read(c->count(c,"SELECT COUNT(*) FROM ai_advice a JOIN review_tasks t ON t.id=a.task_id JOIN submissions s ON s.id=t.submission_id WHERE s.assignment_id=?",id));
        require(reviews==6&&advice==1,"Human and AI records not separate or missing");
        int attachments=0;
        for(var material:files.assignmentFiles(teacher,id)){files.download(teacher,material.id());attachments++;}
        for(var submission:service.submissions(teacher,id))for(var file:files.submissionFiles(teacher,submission.id())){files.download(teacher,file.id());attachments++;}
        require(attachments==6,"Attachment rows did not survive JVM restart");
        Path product=Path.of(App.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(product)));
        require(hash.equals(manifest.getProperty("productSha256")),"This is not the tested final JAR");
        var result=new LinkedHashMap<String,Object>();result.put("result","PASS");result.put("method","Separate JVM, read-only real MySQL queries through the extracted product JAR");result.put("javaVersion",System.getProperty("java.version"));result.put("jarSha256",hash);result.put("submissions",3);result.put("reviews",reviews);result.put("aiAdvice",advice);result.put("attachmentsVerifiedByBytesAndSha256",attachments);result.put("averages",stats.stream().map(s->s.average().toPlainString()).sorted().toList());result.put("externalAiGeneration",false);
        Files.writeString(Path.of(args[2]),new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result));
        System.out.println("PERSIST-NEWJVM PASS: 3 submissions, 6 human reviews, 1 independent AI advice, 6 verified database attachments.");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
