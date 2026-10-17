package edu.course.peerreview.dao;

import edu.course.peerreview.attachment.*;
import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.service.BusinessException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import static edu.course.peerreview.dao.Jdbc.*;

/** Metadata lists exclude binary bodies; writes share the parent's transaction/assignment lock. */
public final class AttachmentRepository {
    public record Owner(long assignmentId,Long submissionId) { }
    private static final String META="id,file_name,media_type,file_size,sha256,text_status,created_at";
    private AttachmentInfo map(ResultSet r)throws SQLException{return new AttachmentInfo(r.getLong("id"),r.getString("file_name"),r.getString("media_type"),r.getLong("file_size"),r.getString("sha256"),r.getString("text_status"),r.getTimestamp("created_at").toLocalDateTime());}
    public List<AttachmentInfo> list(Connection c,long assignmentId,Long submissionId)throws SQLException{
        String where=submissionId==null?"assignment_id=? AND submission_id IS NULL":"assignment_id=? AND submission_id=?";
        return submissionId==null?query(c,"SELECT "+META+" FROM attachments WHERE "+where+" ORDER BY id",this::map,assignmentId):query(c,"SELECT "+META+" FROM attachments WHERE "+where+" ORDER BY id",this::map,assignmentId,submissionId);
    }
    public Owner owner(Connection c,long id)throws SQLException{
        var rows=query(c,"SELECT assignment_id,submission_id FROM attachments WHERE id=?",r -> {long sid=r.getLong(2);boolean missing=r.wasNull();return new Owner(r.getLong(1),missing?null:sid);},id);
        if(rows.isEmpty())throw new BusinessException("附件不存在或已被替换，请刷新。");return rows.get(0);
    }
    public AttachmentDownload read(Connection c,long id)throws SQLException{
        var rows=query(c,"SELECT * FROM attachments WHERE id=?",r -> new AttachmentDownload(map(r),r.getBytes("file_content"),r.getString("text_content")),id);
        if(rows.isEmpty())throw new BusinessException("附件不存在或已被替换，请刷新。");return rows.get(0);
    }
    public List<String[]> texts(Connection c,long assignmentId,Long submissionId)throws SQLException{
        String where=submissionId==null?"assignment_id=? AND submission_id IS NULL":"assignment_id=? AND submission_id=?";
        Jdbc.Mapper<String[]> mapper=r -> new String[]{r.getString("text_status"),r.getString("text_content")};
        return submissionId==null?query(c,"SELECT text_status,text_content FROM attachments WHERE "+where+" ORDER BY id",mapper,assignmentId):query(c,"SELECT text_status,text_content FROM attachments WHERE "+where+" ORDER BY id",mapper,assignmentId,submissionId);
    }
    public int resultingCount(Connection c,long assignmentId,Long submissionId,AttachmentChanges changes)throws SQLException{
        List<AttachmentInfo> existing=list(c,assignmentId,submissionId);
        if(changes==null)return existing.size();
        Set<Long> ids=new HashSet<>();existing.forEach(f -> ids.add(f.id()));
        if(!ids.equals(changes.expectedIds()))throw new BusinessException("附件列表已变化，请关闭窗口并刷新后重试。");
        int count=existing.size()-changes.removedIds().size()+changes.additions().size();
        if(count>PreparedAttachment.MAX_FILES)throw new BusinessException("每份作业资料或提交最多保存 3 个附件。");
        Set<String> hashes=new HashSet<>();existing.stream().filter(f -> !changes.removedIds().contains(f.id())).forEach(f -> hashes.add(f.sha256()));
        for(PreparedAttachment file:changes.additions())if(!hashes.add(file.sha256()))throw new BusinessException("同一记录不能重复添加相同内容的文件。");
        return count;
    }
    public void apply(Connection c,long actorId,long assignmentId,Long submissionId,AttachmentChanges changes,LocalDateTime time)throws SQLException{
        if(changes==null)return;resultingCount(c,assignmentId,submissionId,changes);
        for(long id:changes.removedIds())update(c,"DELETE FROM attachments WHERE id=?",id);
        for(PreparedAttachment file:changes.additions())insert(c,"INSERT INTO attachments(assignment_id,submission_id,file_name,media_type,file_size,sha256,file_content,text_content,text_status,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",assignmentId,submissionId,file.name(),file.mediaType(),file.size(),file.sha256(),file.content(),file.text(),file.textStatus(),actorId,time);
        if(!changes.removedIds().isEmpty()||!changes.additions().isEmpty())new Repository().log(c,actorId,"ATTACHMENTS_CHANGED","作业 "+assignmentId+(submissionId==null?"，教师资料":"，提交 "+submissionId)+"，新增 "+changes.additions().size()+"，移除 "+changes.removedIds().size());
    }
}
