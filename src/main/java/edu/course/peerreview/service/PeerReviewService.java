package edu.course.peerreview.service;

import edu.course.peerreview.allocation.RandomAllocator;
import edu.course.peerreview.attachment.AttachmentChanges;
import edu.course.peerreview.dao.*;
import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.scoring.ScoreCalculator;
import edu.course.peerreview.scoring.ScoringPolicy;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.*;
import java.time.*;
import java.util.*;
import static edu.course.peerreview.dao.Jdbc.*;
import static edu.course.peerreview.service.AuthService.*;

/** 核心业务用例。所有写操作都在事务内完成，先锁作业再修改其关联数据。 */
public final class PeerReviewService {
    private final Database db;
    private final Repository repo = new Repository();
    private final AttachmentRepository attachments = new AttachmentRepository();
    private final ScoringPolicy scoring;
    private final RandomAllocator allocator = new RandomAllocator();
    private final Clock clock;
    private final Random random;

    public PeerReviewService(Database db) { this(db, Clock.systemDefaultZone(), new SecureRandom()); }
    public PeerReviewService(Database db, Clock clock, Random random) { this(db, clock, random, new ScoreCalculator()); }
    public PeerReviewService(Database db, Clock clock, Random random, ScoringPolicy scoring) {
        this.db = db; this.clock = clock; this.random = random; this.scoring = Objects.requireNonNull(scoring);
    }
    public BigDecimal previewScore(List<Criterion> criteria, Map<Long, BigDecimal> scores) { return scoring.calculate(criteria, scores); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }

    public List<Course> courses(User actor) { return db.read(c -> repo.courses(c, actor)); }
    public List<Assignment> assignments(User actor) { return db.read(c -> repo.assignments(c, actor)); }
    public Assignment assignment(User actor, long id) { return db.read(c -> visible(c, actor, id, false)); }

    public void saveCourse(User actor, long id, String name, String description) {
        requireTeacher(actor);
        String title = Validation.text(name, "课程名称", 120);
        String desc = Validation.text(description, "课程说明", 5000);
        db.transaction(c -> {
            if (id == 0) insert(c, "INSERT INTO courses(name,description,teacher_id) VALUES(?,?,?)", title, desc, actor.id());
            else {
                query(c, "SELECT id FROM courses WHERE id=? FOR UPDATE", r -> r.getLong(1), id);
                ownCourse(c, actor, id);
                update(c, "UPDATE courses SET name=?,description=? WHERE id=?", title, desc, id);
            }
            repo.log(c, actor.id(), "SAVE_COURSE", title);
            return null;
        });
    }
    public void deleteCourse(User actor, long id) {
        requireTeacher(actor);
        db.transaction(c -> {
            query(c, "SELECT id FROM courses WHERE id=? FOR UPDATE", r -> r.getLong(1), id);
            ownCourse(c, actor, id);
            if (count(c, "SELECT COUNT(*) FROM assignments WHERE course_id=?", id) > 0) throw new BusinessException("课程仍有作业，请先处理作业，避免误删业务数据。");
            update(c, "DELETE FROM courses WHERE id=?", id);
            repo.log(c, actor.id(), "DELETE_COURSE", "课程 " + id);
            return null;
        });
    }
    public void saveAssignment(User actor, long id, long courseId, String title, String description,
                               LocalDateTime open, LocalDateTime deadline, boolean blind, List<Criterion> criteria) {
        saveAssignmentWithAttachments(actor,id,courseId,title,description,open,deadline,blind,criteria,null);
    }
    public long saveAssignmentWithAttachments(User actor,long id,long courseId,String title,String description,
                                              LocalDateTime open,LocalDateTime deadline,boolean blind,List<Criterion> criteria,AttachmentChanges changes) {
        requireTeacher(actor);
        String name = Validation.text(title, "作业名称", 160);
        String desc = Validation.text(description, "作业说明", 10000);
        if (open == null || deadline == null || !deadline.isAfter(open) || !open.isAfter(now())) throw new BusinessException("互评开始时间须晚于当前时间，截止时间须晚于开始时间。");
        scoring.validateCriteria(criteria);
        return db.transaction(c -> {
            ownCourse(c, actor, courseId);
            long assignmentId = id;
            if (id == 0) {
                assignmentId = insert(c, "INSERT INTO assignments(course_id,title,description,review_open,review_deadline,blind,phase) VALUES(?,?,?,?,?,?,?)", courseId, name, desc, open, deadline, blind, Phase.SUBMISSION);
            } else {
                Assignment old = visible(c, actor, id, true);
                if (old.courseId() != courseId) throw new BusinessException("不能把现有作业移动到其他课程。");
                ensureEditable(c, old);
                update(c, "UPDATE assignments SET title=?,description=?,review_open=?,review_deadline=?,blind=? WHERE id=?", name, desc, open, deadline, blind, id);
                update(c, "DELETE FROM criteria WHERE assignment_id=?", id);
            }
            for (Criterion criterion : criteria) insert(c, "INSERT INTO criteria(assignment_id,name,weight,min_score,max_score) VALUES(?,?,?,?,?)", assignmentId, criterion.name().strip(), criterion.weight(), criterion.min(), criterion.max());
            attachments.apply(c,actor.id(),assignmentId,null,changes,now());
            repo.log(c, actor.id(), "SAVE_ASSIGNMENT", "作业 " + assignmentId);
            return assignmentId;
        });
    }
    public void deleteAssignment(User actor, long id) {
        requireTeacher(actor);
        db.transaction(c -> {
            Assignment assignment = visible(c, actor, id, true);
            ensureEditable(c, assignment);
            update(c, "DELETE FROM assignments WHERE id=?", id);
            repo.log(c, actor.id(), "DELETE_ASSIGNMENT", "作业 " + id);
            return null;
        });
    }
    private void ensureEditable(Connection c, Assignment a) throws SQLException {
        if (a.phase() != Phase.SUBMISSION || count(c, "SELECT COUNT(*) FROM submissions WHERE assignment_id=?", a.id()) > 0)
            throw new BusinessException("已有提交或已进入互评阶段，评分规则和作业不能修改或删除；请新建作业。");
    }
    public void submit(User actor, long assignmentId, String content) { submitWithAttachments(actor,assignmentId,content,null); }
    public void submitWithAttachments(User actor,long assignmentId,String content,AttachmentChanges changes) {
        requireStudent(actor);
        String text = content==null||content.isBlank()?"":Validation.text(content, "作业内容", 20000);
        db.transaction(c -> {
            Assignment a = visible(c, actor, assignmentId, true);
            if (a.phase() != Phase.SUBMISSION || !now().isBefore(a.reviewOpen())) throw new BusinessException("提交已截止或互评已经开始。");
            List<Submission> old = repo.submissions(c, assignmentId, actor.id());
            long previous=old.isEmpty()?-1:old.get(0).id();
            int count=attachments.resultingCount(c,assignmentId,previous,changes);
            if(text.isBlank()&&count==0)throw new BusinessException("作业内容不能为空：请填写文本或添加至少一个文档附件。");
            long submissionId;
            if (old.isEmpty()) submissionId=insert(c, "INSERT INTO submissions(assignment_id,student_id,content,submitted_at) VALUES(?,?,?,?)", assignmentId, actor.id(), text, now());
            else {submissionId=old.get(0).id();update(c, "UPDATE submissions SET content=?,submitted_at=? WHERE id=?", text, now(), submissionId);}
            attachments.apply(c,actor.id(),assignmentId,submissionId,changes,now());
            repo.log(c, actor.id(), "SUBMIT", "作业 " + assignmentId);
            return null;
        });
    }
    public List<Submission> submissions(User actor, long assignmentId) {
        return db.read(c -> { visible(c, actor, assignmentId, false); return repo.submissions(c, assignmentId, actor.role() == Role.TEACHER ? null : actor.id()); });
    }
    public int allocate(User actor, long assignmentId, int reviewers) {
        requireTeacher(actor);
        return db.transaction(c -> {
            Assignment a = visible(c, actor, assignmentId, true);
            if (a.phase() != Phase.SUBMISSION) throw new BusinessException("本作业已分配或已关闭，不允许重复分配。");
            if (now().isBefore(a.reviewOpen()) || !now().isBefore(a.reviewDeadline())) throw new BusinessException("只能在互评开始时间至截止时间之间分配。");
            List<Participant> participants = repo.submissions(c, assignmentId, null).stream().map(s -> new Participant(s.id(), s.studentId())).toList();
            List<Allocation> allocations = allocator.allocate(participants, reviewers, random);
            for (Allocation item : allocations) insert(c, "INSERT INTO review_tasks(submission_id,reviewer_id,status) VALUES(?,?,?)", item.submissionId(), item.reviewerId(), TaskStatus.PENDING);
            update(c, "UPDATE assignments SET phase=? WHERE id=?", Phase.REVIEWING, assignmentId);
            repo.log(c, actor.id(), "ALLOCATE", "作业 " + assignmentId + "，任务数 " + allocations.size());
            return allocations.size();
        });
    }
    public List<ReviewTask> tasks(User actor, long assignmentId) {
        return db.read(c -> {
            Assignment a = visible(c, actor, assignmentId, false);
            List<ReviewTask> result=new ArrayList<>();
            for(ReviewTask task:repo.tasks(c,assignmentId,actor.role()==Role.TEACHER?null:actor.id()))result.add(anonymize(c,actor,a,task));
            return List.copyOf(result);
        });
    }
    public ReviewTask task(User actor, long taskId) {
        return db.read(c -> {
            ReviewTask t = authorizedTask(c, actor, taskId);
            return anonymize(c, actor, visible(c, actor, t.assignmentId(), false), t);
        });
    }
    private ReviewTask anonymize(Connection c,User actor, Assignment a, ReviewTask t) throws SQLException {
        String author = actor.role() == Role.STUDENT && a.blind() ? "匿名提交" : t.author();
        String content=combinedText(t.content(),attachments.texts(c,a.id(),t.submissionId()),false);
        return new ReviewTask(t.id(), t.assignmentId(), t.submissionId(), t.reviewerId(), author, t.reviewer(), content, t.status(), t.total(), t.comment());
    }
    public ReviewDetail reviewDetail(User actor, long taskId) {
        return db.read(c -> { authorizedTask(c, actor, taskId); return repo.detail(c, taskId); });
    }
    public BigDecimal completeReview(User actor, long taskId, Map<Long, BigDecimal> scores, String comment) {
        requireStudent(actor);
        String text = Validation.text(comment, "人工评语", 5000);
        return db.transaction(c -> {
            ReviewTask initial = authorizedTask(c, actor, taskId);
            Assignment a = visible(c, actor, initial.assignmentId(), true);
            ReviewTask task = authorizedTask(c, actor, taskId);
            requirePending(a, task);
            BigDecimal total = scoring.calculate(a.criteria(), scores);
            long reviewId = insert(c, "INSERT INTO reviews(task_id,total,comment,reviewed_at) VALUES(?,?,?,?)", taskId, total, text, now());
            for (var score : scores.entrySet()) update(c, "INSERT INTO review_scores(review_id,criterion_id,score) VALUES(?,?,?)", reviewId, score.getKey(), score.getValue());
            update(c, "UPDATE review_tasks SET status=? WHERE id=?", TaskStatus.COMPLETED, taskId);
            repo.log(c, actor.id(), "REVIEW", "任务 " + taskId);
            return total;
        });
    }
    public BigDecimal updateReview(User actor, long taskId, long reviewId, long expectedVersion,
                                   Map<Long, BigDecimal> scores, String comment) {
        requireStudent(actor);
        String text = Validation.text(comment, "人工评语", 5000);
        return db.transaction(c -> {
            ReviewTask initial = authorizedTask(c, actor, taskId);
            Assignment a = visible(c, actor, initial.assignmentId(), true);
            ReviewTask task = authorizedTask(c, actor, taskId);
            ReviewDetail old = mutableReview(c, a, task, reviewId, expectedVersion);
            BigDecimal total = scoring.calculate(a.criteria(), scores);
            int changed = update(c, "UPDATE reviews SET total=?,comment=?,reviewed_at=?,version=version+1 WHERE id=? AND task_id=? AND version=?",
                    total, text, now(), reviewId, taskId, expectedVersion);
            if (changed != 1) throw staleReview();
            update(c, "DELETE FROM review_scores WHERE review_id=?", reviewId);
            for (var score : scores.entrySet()) update(c, "INSERT INTO review_scores(review_id,criterion_id,score) VALUES(?,?,?)", reviewId, score.getKey(), score.getValue());
            repo.log(c, actor.id(), "UPDATE_REVIEW", "任务 " + taskId + "，记录 " + reviewId + "，版本 " + expectedVersion + "→" + (expectedVersion + 1) + "，总分 " + old.total() + "→" + total);
            return total;
        });
    }
    public void withdrawReview(User actor, long taskId, long reviewId, long expectedVersion) {
        requireStudent(actor);
        db.transaction(c -> {
            ReviewTask initial = authorizedTask(c, actor, taskId);
            Assignment a = visible(c, actor, initial.assignmentId(), true);
            ReviewTask task = authorizedTask(c, actor, taskId);
            ReviewDetail old = mutableReview(c, a, task, reviewId, expectedVersion);
            update(c, "DELETE FROM review_scores WHERE review_id=?", reviewId);
            int removed = update(c, "DELETE FROM reviews WHERE id=? AND task_id=? AND version=?", reviewId, taskId, expectedVersion);
            if (removed != 1) throw staleReview();
            update(c, "UPDATE review_tasks SET status=? WHERE id=?", TaskStatus.PENDING, taskId);
            repo.log(c, actor.id(), "WITHDRAW_REVIEW", "任务 " + taskId + "，记录 " + reviewId + "，版本 " + expectedVersion + "，原总分 " + old.total() + "，恢复待互评");
            return null;
        });
    }
    private ReviewDetail mutableReview(Connection c, Assignment a, ReviewTask task, long reviewId, long version) throws SQLException {
        requireReviewWindow(a);
        if (task.status() != TaskStatus.COMPLETED) throw new BusinessException("该任务尚未完成或已撤回，请刷新任务列表。");
        ReviewDetail detail = repo.detail(c, task.id());
        if (detail == null || detail.reviewId() != reviewId || detail.version() != version) throw staleReview();
        return detail;
    }
    private BusinessException staleReview() { return new BusinessException("互评记录已被修改、撤回或重新提交，请关闭当前窗口并刷新后重试。"); }
    public void closeAssignment(User actor, long assignmentId) {
        requireTeacher(actor);
        db.transaction(c -> {
            Assignment a = visible(c, actor, assignmentId, true);
            if (a.phase() != Phase.REVIEWING) throw new BusinessException("只能结束已经分配任务的作业。");
            boolean pending = repo.tasks(c, assignmentId, null).stream().anyMatch(t -> t.status() == TaskStatus.PENDING);
            if (pending && now().isBefore(a.reviewDeadline())) throw new BusinessException("尚有待评任务，截止时间前不能提前结束。");
            update(c, "UPDATE assignments SET phase=? WHERE id=?", Phase.CLOSED, assignmentId);
            repo.log(c, actor.id(), "CLOSE", "作业 " + assignmentId);
            return null;
        });
    }
    public List<Statistics> statistics(User actor, long assignmentId) {
        return db.read(c -> { visible(c, actor, assignmentId, false); return repo.statistics(c, assignmentId, actor.role() == Role.TEACHER ? null : actor.id()); });
    }
    public Advice advice(User actor, long taskId) {
        return db.read(c -> { authorizedTask(c, actor, taskId); return repo.advice(c, taskId); });
    }
    public record AdviceInput(Assignment assignment, String submission, String materials) {
        public AdviceInput(Assignment assignment,String submission){this(assignment,submission,"");}
    }
    private String combinedText(String body,List<String[]> files,boolean requireReadable) {
        StringBuilder text=new StringBuilder(body);boolean readable=!body.isBlank();int number=0;
        for(String[] file:files){number++;text.append("\n[文档附件 ").append(number).append("]\n");
            if("EXTRACTED".equals(file[0])){text.append(file[1]);readable=true;}
            else text.append("此附件未提供可提取的全文，状态：").append(file[0]).append("。不得据此假称已阅读文件内容。");
        }
        if(requireReadable&&!readable)throw new BusinessException("本次提交没有可供 AI 阅读的正文；扫描/加密/超长文档请补充文本摘要，仍可人工下载评审。");
        return text.toString();
    }
    public AdviceInput adviceInput(User actor, long taskId) {
        requireStudent(actor);
        return db.read(c -> {
            ReviewTask task = authorizedTask(c, actor, taskId);
            Assignment a = visible(c, actor, task.assignmentId(), false);
            requirePending(a, task);
            String submission=combinedText(task.content(),attachments.texts(c,a.id(),task.submissionId()),true);
            String materials=combinedText("",attachments.texts(c,a.id(),null),false);
            if(submission.length()+materials.length()+a.description().length()>60000)throw new BusinessException("正文及附件可读文本总量超过 60000 字符，请精简正文或附件后请求 AI；系统不会静默截断文件。");
            return new AdviceInput(a, submission, materials);
        });
    }
    public void saveAdvice(User actor, long taskId, String text, String model) {
        requireStudent(actor);
        String advice = Validation.text(text, "AI 参考意见", 60000);
        String modelName = Validation.text(model, "模型名称", 160);
        db.transaction(c -> {
            ReviewTask initial = authorizedTask(c, actor, taskId);
            Assignment a = visible(c, actor, initial.assignmentId(), true);
            requirePending(a, authorizedTask(c, actor, taskId));
            update(c, "DELETE FROM ai_advice WHERE task_id=?", taskId);
            update(c, "INSERT INTO ai_advice(task_id,advice,model,created_at) VALUES(?,?,?,?)", taskId, advice, modelName, now());
            repo.log(c, actor.id(), "AI_ADVICE", "任务 " + taskId + "，仅参考");
            return null;
        });
    }
    public List<String[]> logs(User actor) {
        requireTeacher(actor);
        return db.read(c -> query(c, "SELECT l.created_at,u.username,l.action,l.detail FROM operation_logs l JOIN users u ON u.id=l.actor_id WHERE l.actor_id=? OR l.actor_id IN (SELECT id FROM users WHERE role='STUDENT') ORDER BY l.id DESC LIMIT 300", r -> new String[]{r.getString(1), r.getString(2), r.getString(3), r.getString(4)}, actor.id()));
    }
    private void requireReviewWindow(Assignment a) {
        LocalDateTime current = now();
        if (a.phase() != Phase.REVIEWING || current.isBefore(a.reviewOpen()) || !current.isBefore(a.reviewDeadline()))
            throw new BusinessException("当前不在可互评阶段或已截止，不能提交、修改或撤回。");
    }
    private void requirePending(Assignment a, ReviewTask task) {
        requireReviewWindow(a);
        if (task.status() != TaskStatus.PENDING) throw new BusinessException("该任务已完成，请使用修改评分或撤回功能，不能重复提交。");
    }
    private Assignment visible(Connection c, User actor, long id, boolean lock) throws SQLException {
        Assignment a = repo.assignment(c, id, lock);
        if (actor.role() == Role.TEACHER) ownCourse(c, actor, a.courseId());
        return a;
    }
    private Course ownCourse(Connection c, User actor, long id) throws SQLException {
        requireTeacher(actor);
        Course course = repo.course(c, id);
        if (course.teacherId() != actor.id()) throw new BusinessException("不能管理其他教师的课程。");
        return course;
    }
    private ReviewTask authorizedTask(Connection c, User actor, long id) throws SQLException {
        ReviewTask task = repo.task(c, id);
        if (actor.role() == Role.TEACHER) visible(c, actor, task.assignmentId(), false);
        else if (task.reviewerId() != actor.id()) throw new BusinessException("只能访问分配给自己的评审任务。");
        return task;
    }
}
