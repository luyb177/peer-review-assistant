package edu.course.peerreview.service;

import edu.course.peerreview.attachment.PreparedAttachment;
import edu.course.peerreview.dao.*;
import edu.course.peerreview.domain.Domain.*;
import java.sql.*;
import java.security.*;
import java.util.*;
import static edu.course.peerreview.dao.Jdbc.*;

/** The only UI download/list boundary; original names are not returned to blind reviewers. */
public final class AttachmentService {
    private final Database db;
    private final Repository repo=new Repository();
    private final AttachmentRepository files=new AttachmentRepository();
    public AttachmentService(Database db){this.db=db;}
    public List<AttachmentInfo> assignmentFiles(User actor,long assignmentId){return db.read(c -> {assignment(c,actor,assignmentId);return files.list(c,assignmentId,null);});}
    public List<AttachmentInfo> ownSubmissionFiles(User actor,long assignmentId){
        AuthService.requireStudent(actor);return db.read(c -> {assignment(c,actor,assignmentId);var rows=repo.submissions(c,assignmentId,actor.id());return rows.isEmpty()?List.of():files.list(c,assignmentId,rows.get(0).id());});
    }
    public List<AttachmentInfo> submissionFiles(User actor,long submissionId){return db.read(c -> {
        Submission s=repo.submission(c,submissionId);Assignment a=authorize(c,actor,s);return display(actor,s,a,files.list(c,a.id(),s.id()));
    });}
    public String searchableText(User actor,long submissionId){return db.read(c -> {
        Submission s=repo.submission(c,submissionId);Assignment a=authorize(c,actor,s);StringBuilder text=new StringBuilder(s.content());
        for(String[] file:files.texts(c,a.id(),s.id()))if("EXTRACTED".equals(file[0]))text.append('\n').append(file[1]);return text.toString();
    });}
    public List<AttachmentInfo> reviewFiles(User actor,long taskId){if(actor==null)throw new BusinessException("请先登录。");return db.read(c -> {
        ReviewTask task=repo.task(c,taskId);if(actor.role()==Role.STUDENT&&task.reviewerId()!=actor.id())throw new BusinessException("只能访问自己的互评任务附件。");
        Submission s=repo.submission(c,task.submissionId());Assignment a=authorize(c,actor,s);return display(actor,s,a,files.list(c,a.id(),s.id()));
    });}
    public AttachmentDownload download(User actor,long id){return db.read(c -> {
        AttachmentRepository.Owner owner=files.owner(c,id);Assignment a;Submission s=null;
        if(owner.submissionId()==null)a=assignment(c,actor,owner.assignmentId());
        else{s=repo.submission(c,owner.submissionId());a=authorize(c,actor,s);}
        AttachmentDownload result=files.read(c,id);
        try{if(result.content().length!=result.info().size()||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(result.content())).equals(result.info().sha256()))throw new BusinessException("附件内容校验失败，请联系教师重新上传。");}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        AttachmentInfo info=result.info();
        if(s!=null){var displayed=display(actor,s,a,files.list(c,a.id(),s.id()));info=displayed.stream().filter(f -> f.id()==id).findFirst().orElseThrow(() -> new BusinessException("附件已更新，请刷新。"));}
        return new AttachmentDownload(info,result.content(),result.text());
    });}
    private Assignment assignment(Connection c,User actor,long id)throws SQLException{
        if(actor==null)throw new BusinessException("请先登录。");Assignment a=repo.assignment(c,id,false);
        if(actor.role()==Role.TEACHER&&repo.course(c,a.courseId()).teacherId()!=actor.id())throw new BusinessException("不能访问其他教师课程的资料。");return a;
    }
    private Assignment authorize(Connection c,User actor,Submission s)throws SQLException{
        Assignment a=assignment(c,actor,s.assignmentId());
        if(actor.role()==Role.STUDENT&&s.studentId()!=actor.id()&&count(c,"SELECT COUNT(*) FROM review_tasks WHERE submission_id=? AND reviewer_id=?",s.id(),actor.id())==0)throw new BusinessException("无权访问该学生提交的附件。");return a;
    }
    private List<AttachmentInfo> display(User actor,Submission submission,Assignment a,List<AttachmentInfo> input){
        if(actor.role()!=Role.STUDENT||actor.id()==submission.studentId()||!a.blind())return input;
        List<AttachmentInfo> result=new ArrayList<>();
        for(int i=0;i<input.size();i++){AttachmentInfo f=input.get(i);result.add(new AttachmentInfo(f.id(),"匿名附件-"+(i+1)+"."+PreparedAttachment.extension(f.name()),f.mediaType(),f.size(),f.sha256(),f.textStatus(),f.createdAt()));}return List.copyOf(result);
    }
}
