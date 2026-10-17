package edu.course.peerreview.ui;

import edu.course.peerreview.App;
import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.dao.Database;
import edu.course.peerreview.domain.Domain.*;
import edu.course.peerreview.service.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static edu.course.peerreview.dao.Jdbc.*;

/**
 * Explicit opt-in live acceptance runner, NOT a default Surefire test.
 * Opens actual product windows, invokes their buttons, and uses configured MySQL/AI.
 * Creates clearly named synthetic records; passwords remain in ignored local files.
 */
public final class LiveAcceptance {
    private static Path root, output, manifestPath;
    private static Properties manifest;
    private static final List<String> results = new ArrayList<>();
    private static final List<String> screenshots = new ArrayList<>();
    private static final Map<String, String> expectedAverages = new LinkedHashMap<>();
    private static final Map<String, String> expectedHighest = new LinkedHashMap<>(), expectedLowest = new LinkedHashMap<>();
    private static String courseName, assignmentName, teacherLogin, teacherPassword, run;
    private static String[] logins, names, passwords;
    private static Database database;
    private static LocalDateTime reviewOpen;
    private static String stage = "preflight";
    private static WorkspaceFrame workspace;
    private static boolean fullFeatures;
    private static final Duration UI_TIMEOUT = Duration.ofSeconds(35);

    private LiveAcceptance() { }
    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(System.err, true, StandardCharsets.UTF_8));
        boolean success = false;
        try {
            if (args.length == 0) throw new IllegalArgumentException("Pass the absolute project directory; this runner writes synthetic live data");
            if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("A real graphical desktop is required");
            root = Path.of(args[0]).toAbsolutePath();
            boolean mutations = args.length > 1 && args[1].equals("--review-mutations");
            boolean configurationOnly=args.length>2&&args[1].equals("--configuration-only");
            boolean supplementOnly=args.length>2&&args[1].equals("--resume-supplement");
            boolean resumeFull=args.length>2&&args[1].equals("--resume-all-features");
            fullFeatures = args.length > 1 && (args[1].equals("--all-features")||resumeFull);
            configure(resumeFull||configurationOnly||supplementOnly?Path.of(args[2]):args.length > 1 && !mutations && !fullFeatures ? Path.of(args[1]) : null);
            if (fullFeatures) {
                manifest.setProperty("scenario","all-features");saveManifest();
                if(resumeFull)resumeFullFeatureReviews(Arrays.asList(args).contains("--allow-ai-once"));else runAllFeatures(Arrays.asList(args).contains("--allow-ai-once"));
                logout(); postCheck();
            } else if(configurationOnly){
                login(teacherLogin,teacherPassword);scope();runConfigurationChecks();
            } else if(supplementOnly){
                login(teacherLogin,teacherPassword);scope();runReleaseSupplement();
            } else if (mutations) {
                manifest.setProperty("scenario", "review-mutations"); saveManifest(); runReviewMutationScenario();
                runReleaseSupplement();
            } else if (args.length > 2 && args[2].equals("--post-check")) postCheck(); else runScenario();
            stage = "complete";
            success = true;
            report("PASS", null);
            System.out.println("LIVE_ACCEPTANCE_PASS " + output);
        } catch (Throwable error) {
            try { screenshot(activeWindow(), "failure"); } catch (Exception ignored) { }
            try { report("FAIL", error); } catch (Exception ignored) { }
            System.err.println("LIVE_ACCEPTANCE_FAIL stage=" + stage + " type=" + error.getClass().getSimpleName() + " message=" + error.getMessage());
            error.printStackTrace();
        } finally {
            try { ui(() -> { for (Window w : Window.getWindows()) w.dispose(); return null; }); } catch (Exception ignored) { }
            System.exit(success ? 0 : 1);
        }
    }

    private static void configure(Path resume) throws Exception {
        manifest = new Properties();
        if (resume != null) {
            manifestPath = resume;
            try (var reader = Files.newBufferedReader(resume, StandardCharsets.UTF_8)) { manifest.load(reader); }
            run = manifest.getProperty("run");
        } else {
            run = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd-HHmmss")) + "-" + Integer.toHexString(new SecureRandom().nextInt(65536));
            manifestPath = root.resolve("config/acceptance-" + run + ".local.properties");
            manifest.setProperty("run", run);
            for (int i = 0; i < 3; i++) {
                manifest.setProperty("student." + i + ".login", "qa_" + run.replace('-', '_') + "_" + (i + 1));
                manifest.setProperty("student." + i + ".password", secret());
            }
        }
        if (!run.matches("[0-9]{4}-[0-9]{6}-[0-9a-f]{1,4}")) throw new IllegalArgumentException("Invalid acceptance batch id");
        output = root.resolve("target/live-acceptance/" + run); Files.createDirectories(output);
        if (resume != null && Files.exists(output.resolve("progress.txt"))) results.addAll(Files.readAllLines(output.resolve("progress.txt"), StandardCharsets.UTF_8));
        if (!"component".equals(manifest.getProperty("screenshotMode"))) {
            try (var paths = Files.list(output)) { for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".png")).toList()) Files.delete(path); }
            manifest.setProperty("screenshotMode", "component");
        }
        try (var paths = Files.list(output)) { paths.filter(p -> p.getFileName().toString().endsWith(".png")).forEach(p -> screenshots.add(p.getFileName().toString())); }
        if (manifest.getProperty("reviewOpen") != null) reviewOpen = LocalDateTime.parse(manifest.getProperty("reviewOpen"));
        courseName = "[实机验收 " + run + "] 软件构造";
        assignmentName = "[实机验收 " + run + "] 合成文本互评";
        logins = new String[3]; names = new String[3]; passwords = new String[3];
        for (int i = 0; i < 3; i++) {
            logins[i] = manifest.getProperty("student." + i + ".login"); passwords[i] = manifest.getProperty("student." + i + ".password");
            names[i] = (i == 2 ? "=" : "") + "验收学生" + (char) ('A' + i) + "-" + run;
        }
        Properties teacher = new Properties();
        Path teacherFile=root.resolve("config/initial-login.local.properties");
        if(Files.exists(teacherFile))try(var reader=Files.newBufferedReader(teacherFile,StandardCharsets.UTF_8)){teacher.load(reader);}
        else if(!fullFeatures)throw new IllegalStateException("Teacher login settings are required for this acceptance mode");
        teacherLogin = teacher.getProperty("teacher.username","teacher");
        teacherPassword = Optional.ofNullable(System.getenv("PEER_REVIEW_TEACHER_PASSWORD")).orElse(teacher.getProperty("teacher.password",""));
        manifest.setProperty("course", courseName); manifest.setProperty("assignment", assignmentName); saveManifest();
        expectedAverages.put(names[0], "81.50"); expectedHighest.put(names[0], "88.00"); expectedLowest.put(names[0], "75.00");
        expectedAverages.put(names[1], "78.50"); expectedHighest.put(names[1], "82.00"); expectedLowest.put(names[1], "75.00");
        expectedAverages.put(names[2], "85.00"); expectedHighest.put(names[2], "88.00"); expectedLowest.put(names[2], "82.00");
        String override = System.getenv("PEER_REVIEW_ACCEPTANCE_CONFIG");
        Path configFile = override == null || override.isBlank() ? root.resolve("config/application.properties") : Path.of(override).toAbsolutePath();
        AppConfig config = new AppConfig(configFile); database = new Database(config);
        if(!config.get("acceptance.teacher.username").isBlank()) {
            teacherLogin=config.get("acceptance.teacher.username");teacherPassword=config.get("acceptance.teacher.password");
        }
        database.ping();
        if(fullFeatures){
            teacherLogin=config.get("acceptance.teacher.username");teacherPassword=config.get("acceptance.teacher.password");
            new SchemaManager().prepare(database);
            if(database.read(c->count(c,"SELECT COUNT(*) FROM users"))==0)new InitializationService(database).initialize(teacherLogin,"全功能验收教师",teacherPassword.toCharArray(),false);
            manifest.setProperty("teacher.username",teacherLogin);manifest.setProperty("teacher.password",teacherPassword);saveManifest();
        }
        pass("PRE-01", "真实数据库连接成功；使用私有配置，不打印凭据");
        if (Boolean.getBoolean("peerreview.acceptance.packaged")) {
            Path product = Path.of(App.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            check(product.equals(Path.of(System.getProperty("peerreview.acceptance.jar")).toRealPath()), "Product must load from the extracted final JAR");
            manifest.setProperty("productJar",product.toString());
            manifest.setProperty("productSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(product))));
            manifest.setProperty("javaVersion",System.getProperty("java.version"));saveManifest();
            pass("PACKAGE-01","实际 java -jar 启动最终解压 JAR，产品来源与 SHA-256 已核对；使用包内 Java " + System.getProperty("java.version"));
        } else App.main(new String[]{"--config", configFile.toString()});
        await(() -> showing(LoginFrame.class) != null, "actual login frame", UI_TIMEOUT);
        screenshot(showing(LoginFrame.class), "01-login"); pass("UI-01", "从 App.main 打开真实产品登录窗口");
    }

    private static void runScenario() throws Exception {
        stage = "teacher login";
        LoginFrame login = showing(LoginFrame.class);
        fill(login, "用户名", teacherLogin); fill(login, "密码", "deliberately-wrong-password"); click(login, "登录");
        warning("用户名或密码错误"); pass("AUTH-01", "错误密码被实际登录窗口拒绝");
        login(teacherLogin, teacherPassword);
        pass("AUTH-02", "真实教师账号登录成功");
        stage = "course and students";
        if (results.stream().noneMatch(line -> line.startsWith("ASSIGN-01 | PASS"))) {
            prepareCourse(); prepareStudents(); prepareAssignment();
        } else {
            check(reviewOpen != null, "Resume requires original schedule");
            pass("RESUME-01", "恢复同一批次，复用已完成的建课、账号和规则验收；不复制已有数据");
        }
        logout();
        stage = "student submissions";
        for (int i = 0; i < 3; i++) {
            login(logins[i], passwords[i]);
            check(!tabTitles(workspace).contains("课程管理") && !tabTitles(workspace).contains("学生账号"), "Student must not see teacher tabs");
            scope(); selectTab("我的提交");
            boolean submitted = ui(() -> fieldUnchecked(workspace, "submissions", JTable.class).getRowCount()) > 0;
            if (!submitted) {
            selectTab("课堂作业"); select(field(workspace, "assignments", JTable.class), 2, assignmentName);
            click(workspace, "提交 / 更新作业"); JDialog dialog = dialogContaining("提交作业");
            JTextArea content = first(dialog, JTextArea.class);
            if (i == 0) {
                setText(content, " "); click(dialog, "保存提交"); warning("作业内容不能为空");
                pass("SUB-01", "空提交在真实窗口被拒绝，填写窗口仍保留");
            }
            setText(content, synthetic(i, false)); click(dialog, "保存提交"); awaitGone(dialog); ready();
            if (i == 0) {
                selectTab("课堂作业"); select(field(workspace, "assignments", JTable.class), 2, assignmentName);
                click(workspace, "提交 / 更新作业"); dialog = dialogContaining("提交作业");
                check(ui(() -> firstUnchecked(dialogContainingNow("提交作业"), JTextArea.class).getText()).equals(synthetic(i, false)), "Existing submission must reload");
                setText(first(dialog, JTextArea.class), synthetic(i, true)); click(dialog, "保存提交"); awaitGone(dialog); ready();
                pass("SUB-02", "提交后从窗口重新读取并更新文本");
            }
            }
            selectTab("我的提交"); JTable table = field(workspace, "submissions", JTable.class);
            check(ui(table::getRowCount) == 1, "A student must see only one own submission");
            check(ui(() -> table.getValueAt(0, 2).toString()).equals(names[i]), "Submission belongs to logged-in student");
            if (i == 1 && !Boolean.parseBoolean(manifest.getProperty("student.1.passwordChanged", "false"))) changeStudentPassword(i);
            screenshot(workspace, "02-student-" + (i + 1) + "-submission"); logout();
        }
        pass("SUB-03", "三名学生分别提交，学生界面只显示自己的文本记录");
        login(teacherLogin, teacherPassword); scope(); selectTab("全部提交");
        check(ui(() -> fieldUnchecked(workspace, "submissions", JTable.class).getRowCount()) == 3, "Teacher must see all three submissions");
        pass("SUB-04", "教师从实际窗口读取三份提交"); screenshot(workspace, "03-teacher-submissions");
        selectTab("作业管理"); select(field(workspace, "assignments", JTable.class), 2, assignmentName);
        click(workspace, "查看要求"); JDialog savedRules = dialogContaining(assignmentName); screenshot(savedRules, "01b-saved-assignment-rules"); ui(() -> { savedRules.dispose(); return null; });
        click(workspace, "删除作业"); confirm(); warning("已有提交"); ready();
        pass("STATE-01", "已有提交的作业被保护，不能删除");
        stage = "natural review opening and allocation";
        if (LocalDateTime.now().isBefore(reviewOpen)) {
            System.out.println("WAIT natural review opening " + AssignmentDialog.TIME.format(reviewOpen));
            while (LocalDateTime.now().isBefore(reviewOpen.plusSeconds(1))) Thread.sleep(250);
        }
        selectTab("作业管理"); select(field(workspace, "assignments", JTable.class), 2, assignmentName);
        allocate("3", false); warning("评审人数"); ready();
        pass("ALLOC-01", "人数不足 N=3 被拒绝，未留下部分分配");
        allocate("2", true); ready(); selectTab("互评进度");
        check(ui(() -> fieldUnchecked(workspace, "tasks", JTable.class).getRowCount()) == 6, "Exactly six tasks expected");
        PeerReviewService service = field(workspace, "service", PeerReviewService.class); User teacher = field(workspace, "actor", User.class);
        Assignment assignment = service.assignments(teacher).stream().filter(a -> a.title().equals(assignmentName)).findFirst().orElseThrow();
        manifest.setProperty("assignmentId", Long.toString(assignment.id())); saveManifest();
        verifyAllocations(assignment.id()); screenshot(workspace, "04-teacher-six-tasks");
        pass("ALLOC-02", "三人每份两评：共六条任务，无自评、重复或负载不均");
        selectTab("作业管理"); select(field(workspace, "assignments", JTable.class), 2, assignmentName);
        allocate("2", false); warning("不允许重复分配"); ready();
        click(workspace, "结束互评"); confirm(); warning("尚有待评任务"); ready();
        pass("STATE-02", "重复分配和未完成时提前结束均被真实界面拒绝"); logout();
        stage = "human reviews and actual AI";
        for (int i = 0; i < 3; i++) {
            login(logins[i], passwords[i]); scope(); selectTab("我的互评任务");
            JTable table = field(workspace, "tasks", JTable.class);
            check(ui(table::getRowCount) == 2, "Each student must receive two tasks");
            check(ui(() -> { for (int r = 0; r < table.getRowCount(); r++) if (!"匿名提交".equals(table.getValueAt(r, 2))) return false; return true; }), "Blind review must hide real names");
            if (i == 0) verifyForeignTaskDenied(assignment.id());
            while (true) {
                int pending = ui(() -> row(table, 4, "待互评")); if (pending < 0) break;
                ui(() -> { table.setRowSelectionInterval(pending, pending); return null; }); click(workspace, "查看 / 填写互评");
                ReviewDialog review = showingDialog(ReviewDialog.class);
                if (i == 0 && !Boolean.parseBoolean(manifest.getProperty("aiVerified", "false"))) verifyAi(review, i);
                enterReview(review, i);
                if (i == 0 && !Boolean.parseBoolean(manifest.getProperty("rangeVerified", "false"))) {
                    setText(first(review, JTextField.class), "101"); click(review, "计算当前分数"); warning("超出允许区间");
                    enterReview(review, i); manifest.setProperty("rangeVerified", "true"); saveManifest();
                    pass("SCORE-01", "真实评分窗口拒绝越界分数，修正输入后可继续");
                }
                click(review, "计算当前分数");
                String expected = new String[]{"82.00", "88.00", "75.00"}[i];
                check(ui(() -> descendants(review, JLabel.class).stream().anyMatch(l -> ("总分：" + expected).equals(l.getText()))), "Normalized weighted preview mismatch");
                if (i == 0 && !Boolean.parseBoolean(manifest.getProperty("emptyCommentVerified", "false"))) {
                    JTextArea comment = lastEditableArea(review); setText(comment, " "); click(review, "提交人工互评"); confirm(); warning("人工评语不能为空");
                    check(ui(() -> review.isShowing() && review.isEnabled()), "Form must recover after validation failure");
                    enterReview(review, i); manifest.setProperty("emptyCommentVerified", "true"); saveManifest();
                    pass("REVIEW-01", "空人工评语不能保存，原评分输入未丢失");
                }
                screenshot(review, "05-manual-review-" + (i + 1));
                click(review, "提交人工互评"); confirm(); info("互评已保存"); awaitGone(review); ready();
            }
            check(ui(() -> { for (int r = 0; r < table.getRowCount(); r++) if (!"已完成".equals(table.getValueAt(r, 4))) return false; return true; }), "Both tasks must be completed");
            ui(() -> { table.setRowSelectionInterval(0, 0); return null; }); click(workspace, "查看 / 填写互评"); ReviewDialog completed = showingDialog(ReviewDialog.class);
            check(!ui(() -> button(completed, "保存修改").isEnabled()), "Completed reviews begin in view mode; explicit edit is required");
            check(ui(() -> descendants(completed, JTextField.class).stream().noneMatch(JTextField::isEditable)), "Saved score inputs must be read-only");
            click(completed, "关闭"); awaitGone(completed);
            verifyDuplicateDenied(assignment.id());
            pass("REVIEW-" + (i + 2), "合成学生 " + (i + 1) + " 的两条人工互评已保存，默认查看模式且服务端拒绝重复新增");
            logout();
        }
        stage = "teacher statistics, filtering and export";
        login(teacherLogin, teacherPassword); scope(); verifyFilters(); selectTab("结果统计");
        JTable stats = field(workspace, "statistics", JTable.class); verifyStatistics(stats);
        screenshot(workspace, "07-statistics"); pass("STAT-01", "真实界面均值、最高分、最低分与独立计算结果一致");
        Path csv = output.resolve("live-review-results.csv");
        click(workspace, "导出当前统计 CSV");
        await(() -> activeChooser() != null, "CSV file chooser", UI_TIMEOUT);
        ui(() -> { JFileChooser chooser = activeChooser(); chooser.setSelectedFile(csv.toFile()); chooser.approveSelection(); return null; });
        info("CSV 已导出"); ready(); verifyCsv(csv);
        pass("CSV-01", "经真实文件选择器导出，中文、BOM、三行统计和公式前缀转义正确");
        selectTab("作业管理"); select(field(workspace, "assignments", JTable.class), 2, assignmentName); click(workspace, "结束互评"); confirm(); ready();
        check(ui(() -> { JTable a = fieldUnchecked(workspace, "assignments", JTable.class); int r = row(a, 2, assignmentName); return r >= 0 && a.getValueAt(r, 3).equals("已结束"); }), "Assignment must close");
        pass("STATE-03", "全部互评完成后可正常结束作业"); logout();
        login(teacherLogin, teacherPassword); scope(); selectTab("结果统计"); verifyStatistics(field(workspace, "statistics", JTable.class));
        selectTab("操作记录"); screenshot(workspace, "08-operation-history");
        List<String> actions = database.read(c -> query(c, "SELECT DISTINCT action FROM operation_logs WHERE actor_id IN (SELECT id FROM users WHERE username IN (?,?,?))", r -> r.getString(1), logins[0], logins[1], logins[2]));
        check(actions.contains("SUBMIT") && actions.contains("REVIEW") && actions.contains("AI_ADVICE"), "Submission, human review and AI actions must be logged");
        pass("PERSIST-01", "退出并重新登录后统计仍正确，提交/人工评分/AI 参考操作有持久化记录");
        manifest.setProperty("completed", "true"); saveManifest();
    }

    private static void runAllFeatures(boolean allowAi)throws Exception{
        stage="full mandatory and optional classroom setup";
        LoginFrame initial=showing(LoginFrame.class);fill(initial,"用户名",teacherLogin);fill(initial,"密码","deliberately-wrong-password");click(initial,"登录");warning("用户名或密码错误");pass("AUTH-01","错误密码在最终运行包实际登录窗口被拒绝");
        login(teacherLogin,teacherPassword);prepareCourse();prepareStudents();
        String observer="qa_"+run.replace('-','_')+"_observer",observerPassword=secret();manifest.setProperty("observer.login",observer);manifest.setProperty("observer.password",observerPassword);saveManifest();createStudent(observer,"未提交旁观学生-"+run,observerPassword);
        prepareAssignment();
        Path inputs=output.resolve("synthetic-inputs");Files.createDirectories(inputs);
        Path teacherDoc=inputs.resolve("教师作业说明.docx"),teacherPdf=inputs.resolve("教师评分补充.pdf"),teacherText=inputs.resolve("教师注意事项.txt");
        Files.write(teacherDoc,wordDocument("本作业只含合成数据。请检查模块划分、输入校验和事务回滚说明。"));Files.write(teacherPdf,pdfDocument("Please examine correctness, readable design and boundary tests."));Files.writeString(teacherText,"请依据三个评分维度独立判断，不直接采用 AI 的结论。",StandardCharsets.UTF_8);
        selectTab("作业管理");select(field(workspace,"assignments",JTable.class),2,assignmentName);click(workspace,"编辑作业");AssignmentDialog edit=showingDialog(AssignmentDialog.class);chooseDocuments(edit,"添加附件",List.of(teacherDoc,teacherPdf,teacherText));screenshot(edit,"A01-teacher-three-documents");click(edit,"保存作业");awaitGone(edit);ready();
        PeerReviewService service=field(workspace,"service",PeerReviewService.class);User teacher=field(workspace,"actor",User.class);AttachmentService fileService=field(workspace,"attachments",AttachmentService.class);Assignment assignment=service.assignments(teacher).stream().filter(a->a.title().equals(assignmentName)).findFirst().orElseThrow();long assignmentId=assignment.id();manifest.setProperty("assignmentId",Long.toString(assignmentId));saveManifest();
        check(fileService.assignmentFiles(teacher,assignmentId).size()==3,"Teacher materials did not persist");pass("FILE-01","教师通过真实文件选择器发布 DOCX、PDF、TXT 三个资料，正文与附件共同保存");logout();
        Path first=inputs.resolve("学生A姓名学号-初稿.docx"),revised=inputs.resolve("学生A姓名学号-修订.docx"),extra=inputs.resolve("临时补充.txt"),legacy=inputs.resolve("学生B姓名学号-报告.doc"),pdf=inputs.resolve("学生C姓名学号-报告.pdf");
        Files.write(first,wordDocument("合成初稿：模块化设计应分离界面、业务和数据访问。"));Files.write(revised,wordDocument("ATTACHMENT_ONLY_MARKER。本文是合成文档：采用模块化设计，将界面、业务、持久化和评分算法分开。输入需校验空值、时间和越界分数；保存评分应整体提交或回滚。还应补充异常恢复和边界测试。"));Files.writeString(extra,"临时附件，移除后不应继续下载。",StandardCharsets.UTF_8);Files.copy(root.resolve("src/test/resources/attachments/poi-simple-doc/simple.doc"),legacy);Files.write(pdf,pdfDocument("Synthetic submission: modular design and unit testing are useful. Add transaction rollback examples."));
        Map<Long,byte[]> expectedBytes=new HashMap<>();long firstSubmission=0;
        for(int i=0;i<3;i++){
            login(logins[i],passwords[i]);check(!tabTitles(workspace).contains("课程管理")&&!tabTitles(workspace).contains("学生账号"),"Student teacher tabs exposed");scope();selectTab("课堂作业");select(field(workspace,"assignments",JTable.class),2,assignmentName);click(workspace,"提交 / 更新作业");JDialog submit=dialogContaining("提交作业");Path selected=i==0?first:i==1?legacy:pdf;
            if(i==0){setText(first(submit,JTextArea.class)," ");click(submit,"保存提交");warning("作业内容不能为空");setText(first(submit,JTextArea.class),"");pass("SUB-01","最终包拒绝空文本且无附件的提交，原表单可继续使用");}
            if(i>0)setText(first(submit,JTextArea.class),synthetic(i,false));chooseDocuments(submit,"添加附件",List.of(selected));click(submit,"保存提交");awaitGone(submit);ready();
            User actor=field(workspace,"actor",User.class);PeerReviewService current=field(workspace,"service",PeerReviewService.class);long sid=current.submissions(actor,assignmentId).get(0).id();
            if(i==0){
                firstSubmission=sid;check(current.submissions(actor,assignmentId).get(0).content().isEmpty(),"Document-only submission should not invent text");
                selectTab("课堂作业");select(field(workspace,"assignments",JTable.class),2,assignmentName);click(workspace,"提交 / 更新作业");JDialog replace=dialogContaining("提交作业");JTable files=first(replace,JTable.class);ui(()->{files.setRowSelectionInterval(0,0);return null;});chooseDocuments(replace,"替换附件",List.of(revised));chooseDocuments(replace,"添加附件",List.of(extra));select(files,0,extra.getFileName().toString());click(replace,"移除附件");screenshot(replace,"A02-document-only-replace-remove");click(replace,"保存提交");awaitGone(replace);ready();expectedBytes.put(sid,Files.readAllBytes(revised));
            }else expectedBytes.put(sid,Files.readAllBytes(selected));
            selectTab("我的提交");check(ui(()->fieldUnchecked(workspace,"submissions",JTable.class).getRowCount())==1,"Student sees another person's submission");if(i==1)changeStudentPassword(i);logout();
        }
        pass("FILE-02","三人实际提交 DOCX-only、DOC+文本、PDF+文本；仅文档提交可替换并移除临时附件");
        // Remove only our generated local source: later downloads must come from MySQL, not a saved path.
        Files.delete(revised);login(teacherLogin,teacherPassword);scope();selectTab("全部提交");JTable submissionsTable=field(workspace,"submissions",JTable.class);select(submissionsTable,0,Long.toString(firstSubmission));click(workspace,"阅读所选提交");JDialog submissionView=dialogContaining(assignmentName+" · ");List<AttachmentInfo> originalFiles=fileService.submissionFiles(teacher,firstSubmission);check(originalFiles.size()==1,"Replaced/removed attachments still persisted");JTable fileTable=first(submissionView,JTable.class);ui(()->{fileTable.setRowSelectionInterval(0,0);return null;});click(submissionView,"预览附件正文");JDialog preview=dialogContaining("附件正文预览");check(ui(()->firstUnchecked(preview,JTextArea.class).getText()).contains("ATTACHMENT_ONLY_MARKER"),"Extracted DOCX preview missing");ui(()->{preview.dispose();return null;});downloadDocument(submissionView,output.resolve("teacher-download.docx"),originalFiles.get(0).name(),expectedBytes.get(firstSubmission));screenshot(submissionView,"A03-teacher-document-read-download");click(submissionView,"关闭");awaitGone(submissionView);pass("FILE-03","删除测试上传源文件后，教师仍可从 MySQL 预览/下载修订 DOCX，字节与 SHA 对应原文");
        selectTab("作业管理");select(field(workspace,"assignments",JTable.class),2,assignmentName);System.out.println("WAIT actual review opening "+AssignmentDialog.TIME.format(reviewOpen));while(LocalDateTime.now().isBefore(reviewOpen.plusSeconds(1)))Thread.sleep(250);allocate("3",false);warning("评审人数");ready();allocate("2",true);ready();verifyAllocations(assignmentId);pass("ALLOC-FULL","三份作业产生六条任务，旁观未提交学生未参与，排除自评和重复");
        select(field(workspace,"assignments",JTable.class),2,assignmentName);allocate("2",false);warning("不允许重复分配");ready();select(field(workspace,"assignments",JTable.class),2,assignmentName);click(workspace,"结束互评");confirm();warning("尚有待评任务");ready();pass("STATE-02","最终包拒绝重复分配和有待评任务时提前结束");
        User outsider=new AuthService(database).login(observer,observerPassword.toCharArray());long privateFile=originalFiles.get(0).id();try{fileService.download(outsider,privateFile);throw new AssertionError("Observer could download private attachment");}catch(BusinessException expected){check(expected.getMessage().contains("无权"),"Unexpected file authorization result");}pass("FILE-04","未提交、未被分配的学生不能按 ID 读取其他人的提交附件（服务层额外检查）");logout();
        reviewDocumentTasksAndResults(allowAi,assignmentId,firstSubmission,revised,expectedBytes,fileService);
    }
    private static void resumeFullFeatureReviews(boolean allowAi)throws Exception{
        login(teacherLogin,teacherPassword);scope();selectTab("作业管理");select(field(workspace,"assignments",JTable.class),2,assignmentName);
        long assignmentId=Long.parseLong(manifest.getProperty("assignmentId"));PeerReviewService service=field(workspace,"service",PeerReviewService.class);User teacher=field(workspace,"actor",User.class);AttachmentService files=field(workspace,"attachments",AttachmentService.class);
        select(field(workspace,"assignments",JTable.class),2,assignmentName);allocate("2",false);warning("不允许重复分配");ready();select(field(workspace,"assignments",JTable.class),2,assignmentName);click(workspace,"结束互评");confirm();warning("尚有待评任务");ready();pass("STATE-02","最终包拒绝重复分配和有待评任务时提前结束");
        Map<Long,byte[]> bytes=new HashMap<>();long firstSubmission=0;
        for(Submission submission:service.submissions(teacher,assignmentId)){
            int index=Arrays.asList(names).indexOf(submission.author());
            if(index==0){firstSubmission=submission.id();bytes.put(submission.id(),Files.readAllBytes(output.resolve("teacher-download.docx")));}
            else bytes.put(submission.id(),Files.readAllBytes(output.resolve(index==1?"synthetic-inputs/学生B姓名学号-报告.doc":"synthetic-inputs/学生C姓名学号-报告.pdf")));
        }
        check(firstSubmission!=0,"Original document-only submission missing");
        User observer=new AuthService(database).login(manifest.getProperty("observer.login"),manifest.getProperty("observer.password").toCharArray());long privateFile=files.submissionFiles(teacher,firstSubmission).get(0).id();
        try{files.download(observer,privateFile);throw new AssertionError("Observer could read private attachment");}catch(BusinessException expected){check(expected.getMessage().contains("无权"),"Unexpected permission error");}pass("FILE-04","旁观学生直接按 ID 下载私有附件被真实数据库上的服务授权拒绝");logout();
        reviewDocumentTasksAndResults(allowAi,assignmentId,firstSubmission,output.resolve("synthetic-inputs/学生A姓名学号-修订.docx"),bytes,files);
    }
    private static void reviewDocumentTasksAndResults(boolean allowAi,long assignmentId,long firstSubmission,Path revised,Map<Long,byte[]> expectedBytes,AttachmentService fileService)throws Exception{
        stage="blind document review and one authorized AI generation";
        for(int i=0;i<3;i++){
            login(logins[i],passwords[i]);scope();selectTab("我的互评任务");JTable tasks=field(workspace,"tasks",JTable.class);User actor=field(workspace,"actor",User.class);PeerReviewService current=field(workspace,"service",PeerReviewService.class);
            if(i==1){for(ReviewTask t:current.tasks(actor,assignmentId))if(t.submissionId()==firstSubmission)select(tasks,0,Long.toString(t.id()));}else ui(()->{tasks.setRowSelectionInterval(0,0);return null;});
            boolean firstTask=true;
            while(true){
                int selected=ui(tasks::getSelectedRow);if(selected<0||!"待互评".equals(ui(()->tasks.getValueAt(tasks.getSelectedRow(),4)))){int pending=ui(()->row(tasks,4,"待互评"));if(pending<0)break;ui(()->{tasks.setRowSelectionInterval(pending,pending);return null;});}
                long taskId=ui(()->Long.parseLong(tasks.getValueAt(tasks.getSelectedRow(),0).toString()));ReviewTask task=current.task(actor,taskId);check(task.author().equals("匿名提交"),"Blind author leaked");click(workspace,"查看 / 填写互评");ReviewDialog review=showingDialog(ReviewDialog.class);List<AttachmentInfo> visible=fileService.reviewFiles(actor,taskId);check(visible.size()==1&&visible.get(0).name().startsWith("匿名附件-"),"Original filename leaked");JTable table=first(review,JTable.class);ui(()->{table.setRowSelectionInterval(0,0);return null;});
                if(firstTask){Path downloaded=output.resolve("reviewer-"+(i+1)+"."+edu.course.peerreview.attachment.PreparedAttachment.extension(visible.get(0).name()));downloadDocument(review,downloaded,visible.get(0).name(),expectedBytes.get(task.submissionId()));screenshot(review,"A04-blind-file-review-"+(i+1));}
                if(i==1&&firstTask&&allowAi){
                    check(task.submissionId()==firstSubmission,"AI should use our synthetic DOCX-only submission");String prompt=edu.course.peerreview.ai.AdvicePrompt.user(current.adviceInput(actor,taskId));check(prompt.contains("ATTACHMENT_ONLY_MARKER")&&prompt.contains("教师文档资料"),"AI input is not using DB-extracted file text");check(!prompt.contains(revised.getFileName().toString()),"Original filename entered AI input");
                    Path budget=output.resolve("ai-attempted.txt");if(Files.exists(budget))throw new IllegalStateException("The authorized generation was already attempted; refusing automatic repeat");Files.writeString(budget,"run="+run+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);verifyAi(review,i);
                    pass("AI-FILE","当前默认公网接口实际读取合成 DOCX 与教师资料，参考意见独立保存，没有代填分数或人工评语");
                }
                enterReview(review,i);
                if(i==0&&firstTask){setText(first(review,JTextField.class),"101");click(review,"计算当前分数");warning("超出允许区间");enterReview(review,i);setText(lastEditableArea(review)," ");click(review,"提交人工互评");confirm();warning("人工评语不能为空");enterReview(review,i);verifyForeignTaskDenied(assignmentId);pass("SCORE-01","最终包拒绝越界评分和空评语，失败后保留表单并可继续");}
                click(review,"计算当前分数");String score=new String[]{"82.00","88.00","75.00"}[i];check(ui(()->descendants(review,JLabel.class).stream().anyMatch(l->("总分："+score).equals(l.getText()))),"Weighted score mismatch");click(review,"提交人工互评");confirm();info("互评已保存");awaitGone(review);ready();firstTask=false;
                int pending=ui(()->row(tasks,4,"待互评"));if(pending<0)break;ui(()->{tasks.setRowSelectionInterval(pending,pending);return null;});
            }
            verifyDuplicateDenied(assignmentId);logout();
        }
        pass("FILE-05","各评审人通过匿名另存获取文档真实字节，DOC/DOCX/PDF/TXT 参与本批次发布/提交，六条人工评分全部完成；Markdown 在补充批次单独验证");
        stage="document-aware search, statistics and export";login(teacherLogin,teacherPassword);scope();selectTab("全部提交");setText(field(workspace,"regex",JTextField.class),"ATTACHMENT_ONLY_MARKER");click(workspace,"筛选");check(ui(()->fieldUnchecked(workspace,"submissions",JTable.class).getRowCount())==1,"Document-only submission text is not searchable");selectTab("互评进度");check(ui(()->fieldUnchecked(workspace,"tasks",JTable.class).getRowCount())==2,"Extracted file content not included in task search");setText(field(workspace,"regex",JTextField.class),"");click(workspace,"筛选");verifyFilters();selectTab("结果统计");verifyStatistics(field(workspace,"statistics",JTable.class));screenshot(workspace,"A05-complete-results");
        Path csv=output.resolve("all-features-results.csv");click(workspace,"导出当前统计 CSV");await(()->activeChooser()!=null,"all feature CSV",UI_TIMEOUT);ui(()->{JFileChooser chooser=activeChooser();chooser.setSelectedFile(csv.toFile());chooser.approveSelection();return null;});info("CSV 已导出");ready();verifyCsv(csv);pass("FILE-06","仅文档提交的提取正文可正则搜索，课程/作业/状态筛选、均值极值与 CSV 都正确");
        selectTab("作业管理");select(field(workspace,"assignments",JTable.class),2,assignmentName);click(workspace,"结束互评");confirm();ready();logout();login(teacherLogin,teacherPassword);scope();selectTab("结果统计");verifyStatistics(field(workspace,"statistics",JTable.class));check(fileService.submissionFiles(field(workspace,"actor",User.class),firstSubmission).size()==1,"Attachments lost after relogin");pass("PERSIST-FULL","结束互评及重新登录后，文档内容、任务、评分和统计保留");manifest.setProperty("completed","true");saveManifest();
    }
    private static void chooseDocuments(Window owner,String action,List<Path> paths)throws Exception{
        click(owner,action);await(()->activeChooser()!=null,"document chooser",UI_TIMEOUT);ui(()->{JFileChooser chooser=activeChooser();chooser.setSelectedFile(paths.get(0).toFile());if(chooser.isMultiSelectionEnabled())chooser.setSelectedFiles(paths.stream().map(Path::toFile).toArray(java.io.File[]::new));chooser.approveSelection();return null;});
        await(()->uiUnchecked(()->owner.isEnabled()&&paths.stream().allMatch(path->descendants(owner,JTable.class).stream().anyMatch(t->{for(int r=0;r<t.getRowCount();r++)if(path.getFileName().toString().equals(t.getValueAt(r,0)))return true;return false;}))),"staged document rows",UI_TIMEOUT);
    }
    private static void downloadDocument(Window owner,Path path,String expectedName,byte[] expected)throws Exception{
        click(owner,"另存附件");await(()->activeChooser()!=null,"attachment save chooser",UI_TIMEOUT);check(ui(()->activeChooser().getSelectedFile().getName()).equals(expectedName),"Download filename did not match privacy policy");ui(()->{JFileChooser chooser=activeChooser();chooser.setSelectedFile(path.toFile());chooser.approveSelection();return null;});info("附件已另存");check(Arrays.equals(Files.readAllBytes(path),expected),"Downloaded bytes differ from stored original");
    }
    private static byte[] wordDocument(String body)throws Exception{
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(out)){zip.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));zip.write("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>".getBytes(StandardCharsets.UTF_8));zip.closeEntry();zip.putNextEntry(new java.util.zip.ZipEntry("_rels/.rels"));zip.write("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>".getBytes(StandardCharsets.UTF_8));zip.closeEntry();zip.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));zip.write(("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>"+body+"</w:t></w:r></w:p></w:body></w:document>").getBytes(StandardCharsets.UTF_8));zip.closeEntry();}return out.toByteArray();
    }
    private static byte[] pdfDocument(String body)throws Exception{
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument()){var page=new org.apache.pdfbox.pdmodel.PDPage();pdf.addPage(page);try(var text=new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf,page)){text.beginText();text.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);text.newLineAtOffset(40,700);text.showText(body);text.endText();}pdf.save(out);}return out.toByteArray();
    }

    private static void runReviewMutationScenario() throws Exception {
        stage = "review mutation classroom setup";
        login(teacherLogin, teacherPassword); prepareCourse(); prepareStudents(); prepareAssignment(); logout();
        for (int i = 0; i < 3; i++) {
            login(logins[i], passwords[i]); scope(); selectTab("课堂作业"); select(field(workspace, "assignments", JTable.class), 2, assignmentName);
            click(workspace, "提交 / 更新作业"); JDialog submit = dialogContaining("提交作业");
            setText(first(submit, JTextArea.class), synthetic(i, false)); click(submit, "保存提交"); awaitGone(submit); ready(); logout();
        }
        login(teacherLogin, teacherPassword); scope(); selectTab("作业管理"); select(field(workspace, "assignments", JTable.class), 2, assignmentName);
        System.out.println("WAIT natural review opening " + AssignmentDialog.TIME.format(reviewOpen));
        while (LocalDateTime.now().isBefore(reviewOpen.plusSeconds(1))) Thread.sleep(250);
        allocate("2", true); ready();
        PeerReviewService teacherService = field(workspace, "service", PeerReviewService.class); User teacher = field(workspace, "actor", User.class);
        Assignment assignment = teacherService.assignments(teacher).stream().filter(a -> a.title().equals(assignmentName)).findFirst().orElseThrow();
        manifest.setProperty("assignmentId", Long.toString(assignment.id())); saveManifest(); verifyAllocations(assignment.id()); logout();
        stage = "actual review edit and withdrawal";
        login(logins[0], passwords[0]); scope(); selectTab("我的互评任务"); JTable initialTasks = field(workspace, "tasks", JTable.class);
        ui(() -> { initialTasks.setRowSelectionInterval(0, 0); return null; });
        long taskId = ui(() -> Long.parseLong(initialTasks.getValueAt(0, 0).toString()));
        PeerReviewService service = field(workspace, "service", PeerReviewService.class); User actor = field(workspace, "actor", User.class);
        long submissionId = service.task(actor, taskId).submissionId();
        click(workspace, "查看 / 填写互评"); ReviewDialog entry = showingDialog(ReviewDialog.class); enterReview(entry, 0);
        click(entry, "提交人工互评"); confirm(); info("互评已保存"); awaitGone(entry); ready();
        ReviewDetail first = service.reviewDetail(actor, taskId);
        check(first.version() == 1 && first.total().compareTo(new BigDecimal("82.00")) == 0, "Original review mismatch");
        pass("MUTATE-01", "真实窗口提交人工评分 82.00，版本为 1");
        select(initialTasks, 0, Long.toString(taskId)); click(workspace, "查看 / 填写互评"); ReviewDialog edit = showingDialog(ReviewDialog.class);
        check(!ui(() -> button(edit, "保存修改").isEnabled()), "Viewing must not implicitly edit");
        click(edit, "修改评分"); enterReview(edit, 1); screenshot(edit, "11-edit-review");
        click(edit, "计算当前分数"); check(ui(() -> descendants(edit, JLabel.class).stream().anyMatch(l -> "总分：88.00".equals(l.getText()))), "Edited preview mismatch");
        check(service.reviewDetail(actor, taskId).equals(first), "Preview must not persist");
        click(edit, "保存修改"); confirm(); info("评分修改已保存"); awaitGone(edit); ready();
        ReviewDetail second = service.reviewDetail(actor, taskId);
        check(second.reviewId() == first.reviewId() && second.version() == 2 && second.total().compareTo(new BigDecimal("88.00")) == 0, "Saved edit/version mismatch");
        check(second.comment().equals(comment(1)), "Edited comment not saved");
        check(database.read(c -> count(c, "SELECT COUNT(*) FROM review_scores WHERE review_id=?", first.reviewId())) == 3, "Old detail rows must be replaced, not duplicated");
        pass("MUTATE-02", "真实窗口显式修改后保存 88.00 和人工评语，版本递增，评分明细没有重复");
        try { service.updateReview(actor, taskId, first.reviewId(), first.version(), second.scores(), "旧窗口覆盖"); throw new AssertionError("Stale update accepted"); }
        catch (BusinessException expected) { check(expected.getMessage().contains("刷新"), "Stale edit should require refresh"); }
        try { service.withdrawReview(actor, taskId, first.reviewId(), first.version()); throw new AssertionError("Stale withdrawal accepted"); }
        catch (BusinessException expected) { check(expected.getMessage().contains("刷新"), "Stale withdrawal should require refresh"); }
        pass("MUTATE-03", "真实 MySQL 上的旧版本修改和撤回均被拒绝（服务层额外验证）");
        logout(); login(teacherLogin, teacherPassword); scope(); selectTab("结果统计");
        assertSingleReviewStatistic(submissionId, "88.00", 1); screenshot(workspace, "12-statistics-after-edit");
        pass("MUTATE-04", "教师实际统计面板立即显示修改后的 88.00，人数未增加");
        selectTab("互评进度"); select(field(workspace, "tasks", JTable.class), 0, Long.toString(taskId)); click(workspace, "查看互评详情");
        ReviewDialog readOnly = showingDialog(ReviewDialog.class);
        check(!ui(() -> button(readOnly, "修改评分").isEnabled()) && !ui(() -> button(readOnly, "撤回互评").isEnabled()), "Teacher must not edit or withdraw student reviews");
        screenshot(readOnly, "13-teacher-read-only"); click(readOnly, "关闭"); awaitGone(readOnly); logout();
        login(logins[0], passwords[0]); scope(); selectTab("我的互评任务"); select(field(workspace, "tasks", JTable.class), 0, Long.toString(taskId));
        click(workspace, "查看 / 填写互评"); ReviewDialog withdraw = showingDialog(ReviewDialog.class); click(withdraw, "撤回互评");
        JDialog confirmation = dialogContaining("请确认"); screenshot(confirmation, "14-withdraw-confirmation"); accept(confirmation); info("互评已撤回"); awaitGone(withdraw); ready();
        check(service.reviewDetail(actor, taskId) == null && service.task(actor, taskId).status() == TaskStatus.PENDING, "Withdrawal must restore pending without review");
        check(database.read(c -> count(c, "SELECT COUNT(*) FROM review_scores WHERE review_id=?", first.reviewId())) == 0, "Withdrawn detail rows remain");
        screenshot(workspace, "15-task-pending-again");
        pass("MUTATE-05", "本人真实窗口撤回后评分及明细删除，任务恢复待互评，分配关系保留");
        logout(); login(teacherLogin, teacherPassword); scope(); selectTab("结果统计"); assertSingleReviewStatistic(submissionId, null, 0);
        screenshot(workspace, "16-statistics-after-withdrawal"); pass("MUTATE-06", "撤回最后一份评分后已评人数为 0、均值和极值为空，不伪装成 0 分");
        logout(); login(logins[0], passwords[0]); scope(); selectTab("我的互评任务"); select(field(workspace, "tasks", JTable.class), 0, Long.toString(taskId));
        click(workspace, "查看 / 填写互评"); ReviewDialog again = showingDialog(ReviewDialog.class);
        check(ui(() -> descendants(again, JTextField.class).stream().allMatch(t -> t.isEditable() && t.getText().isEmpty())), "Resubmission form must begin blank");
        enterReview(again, 2); click(again, "提交人工互评"); confirm(); info("互评已保存"); awaitGone(again); ready();
        ReviewDetail third = service.reviewDetail(actor, taskId);
        check(third.reviewId() != first.reviewId() && third.version() == 1 && third.total().compareTo(new BigDecimal("75.00")) == 0, "Resubmission identity mismatch");
        try { service.withdrawReview(actor, taskId, first.reviewId(), first.version()); throw new AssertionError("Withdrawn old ID affected new review"); }
        catch (BusinessException expected) { check(expected.getMessage().contains("刷新"), "Old identity should require refresh"); }
        pass("MUTATE-07", "撤回后重新填写得到新评分记录，旧窗口即使版本同为 1 也不能撤回新记录");
        logout(); login(teacherLogin, teacherPassword); scope(); selectTab("结果统计"); assertSingleReviewStatistic(submissionId, "75.00", 1);
        screenshot(workspace, "17-statistics-after-resubmission");
        Path csv = output.resolve("review-mutation-results.csv"); click(workspace, "导出当前统计 CSV"); await(() -> activeChooser() != null, "mutation CSV chooser", UI_TIMEOUT);
        ui(() -> { JFileChooser chooser = activeChooser(); chooser.setSelectedFile(csv.toFile()); chooser.approveSelection(); return null; }); info("CSV 已导出"); ready();
        List<List<String>> rows = parseCsv(Files.readString(csv, StandardCharsets.UTF_8));
        check(rows.size() == 4, "Export must retain three submission rows");
        List<String> target = rows.stream().skip(1).filter(r -> r.get(0).equals(Long.toString(submissionId))).findFirst().orElseThrow();
        check(target.get(2).equals("1"), "CSV should count one effective review"); decimal(target.get(4), "75.00");
        for (String action : List.of("UPDATE_REVIEW", "WITHDRAW_REVIEW")) {
            check(database.read(c -> count(c, "SELECT COUNT(*) FROM operation_logs WHERE actor_id=? AND action=?", actor.id(), action)) == 1, "Audit log should record exactly one accepted mutation");
        }
        pass("MUTATE-08", "教师重新登录及导出 CSV 读取新成绩，修改/撤回日志各一条，无重复评分");
        manifest.setProperty("completed", "true"); saveManifest();
    }
    private static void runReleaseSupplement() throws Exception {
        stage="non-blind classroom and all attachment formats";
        choose(field(workspace,"assignmentFilter",JComboBox.class),"全部作业");selectTab("作业管理");
        String publicTitle="[实机验收 "+run+"] 非匿名与 Markdown";
        PeerReviewService previousService=field(workspace,"service",PeerReviewService.class);User previousActor=field(workspace,"actor",User.class);
        var abandoned=previousService.assignments(previousActor).stream().filter(a->a.title().equals(publicTitle)).findFirst();
        if(abandoned.isPresent()){
            if(!previousService.submissions(previousActor,abandoned.get().id()).isEmpty()){
                check(abandoned.get().phase()==Phase.REVIEWING&&previousService.tasks(previousActor,abandoned.get().id()).stream().allMatch(t->t.status()==TaskStatus.PENDING),"Supplement resume requires allocated, unreviewed tasks");
                logout();finishNonBlindReviews(publicTitle,output.resolve("supplement-inputs/学生实名报告.md"));return;
            }
            select(field(workspace,"assignments",JTable.class),2,publicTitle);click(workspace,"删除作业");confirm();ready();
        }
        Path inputs=output.resolve("supplement-inputs");Files.createDirectories(inputs);
        Path old=inputs.resolve("教师初稿.txt"),markdown=inputs.resolve("教师规则.md"),removed=inputs.resolve("待移除资料.txt"),studentMarkdown=inputs.resolve("学生实名报告.md");
        Files.writeString(old,"初稿资料",StandardCharsets.UTF_8);Files.writeString(markdown,"# 合成评分规则\n考查模块化、正确性与边界。",StandardCharsets.UTF_8);
        Files.writeString(removed,"临时资料",StandardCharsets.UTF_8);Files.writeString(studentMarkdown,"# 合成学生报告\nMARKDOWN_LIVE_CHECK：界面、业务和数据访问分离。",StandardCharsets.UTF_8);
        click(workspace,"发布作业");AssignmentDialog form=showingDialog(AssignmentDialog.class);choose(first(form,JComboBox.class),courseName);
        fill(form,"作业名称",publicTitle);fill(form,"作业要求","本次明确关闭匿名，只使用合成学生和合成材料。");
        LocalDateTime open=LocalDateTime.now().plusMinutes(2).withSecond(0).withNano(0);
        fill(form,"互评开始",AssignmentDialog.TIME.format(open));fill(form,"互评截止",AssignmentDialog.TIME.format(open.plusMinutes(8)));
        ui(()->{descendants(form,JCheckBox.class).get(0).setSelected(false);return null;});chooseDocuments(form,"添加附件",List.of(old));click(form,"保存作业");awaitGone(form);ready();
        JTable assignments=field(workspace,"assignments",JTable.class);select(assignments,2,publicTitle);click(workspace,"编辑作业");AssignmentDialog edit=showingDialog(AssignmentDialog.class);JTable staged=first(first(edit,AttachmentEditorPanel.class),JTable.class);
        ui(()->{staged.setRowSelectionInterval(0,0);return null;});chooseDocuments(edit,"替换附件",List.of(markdown));chooseDocuments(edit,"添加附件",List.of(removed));select(staged,0,removed.getFileName().toString());click(edit,"移除附件");click(edit,"保存作业");awaitGone(edit);ready();
        PeerReviewService service=field(workspace,"service",PeerReviewService.class);User teacher=field(workspace,"actor",User.class);AttachmentService files=field(workspace,"attachments",AttachmentService.class);
        Assignment assignment=service.assignments(teacher).stream().filter(a->a.title().equals(publicTitle)).findFirst().orElseThrow();
        var materials=files.assignmentFiles(teacher,assignment.id());check(materials.size()==1&&materials.get(0).name().endsWith(".md"),"Teacher replacement/removal did not persist");
        check(files.download(teacher,materials.get(0).id()).text().contains("合成评分规则"),"Markdown extraction failed");pass("FILE-07","最终包真实教师编辑窗口替换 TXT 为 Markdown 并移除临时附件，存库重新读取与提取正确");logout();
        for(int i=0;i<2;i++){
            login(logins[i],passwords[i]);choose(field(workspace,"courseFilter",JComboBox.class),courseName);choose(field(workspace,"assignmentFilter",JComboBox.class),publicTitle);selectTab("课堂作业");select(field(workspace,"assignments",JTable.class),2,publicTitle);
            click(workspace,"提交 / 更新作业");JDialog submit=dialogContaining("提交作业");setText(first(submit,JTextArea.class),"合成文本初稿 "+i);if(i==0)chooseDocuments(submit,"添加附件",List.of(studentMarkdown));click(submit,"保存提交");awaitGone(submit);ready();
            if(i==1){select(field(workspace,"assignments",JTable.class),2,publicTitle);click(workspace,"提交 / 更新作业");JDialog update=dialogContaining("提交作业");check(ui(()->firstUnchecked(update,JTextArea.class).getText()).equals("合成文本初稿 1"),"Text did not reload");setText(first(update,JTextArea.class),"合成文本已更新：加入边界测试与异常恢复。");click(update,"保存提交");awaitGone(update);ready();}
            logout();
        }
        pass("SUB-02","最终包真实学生窗口完成 Markdown 附件提交和纯文本提交更新，再读内容一致");
        login(teacherLogin,teacherPassword);choose(field(workspace,"courseFilter",JComboBox.class),courseName);choose(field(workspace,"assignmentFilter",JComboBox.class),publicTitle);selectTab("作业管理");select(field(workspace,"assignments",JTable.class),2,publicTitle);
        System.out.println("WAIT non-blind actual opening "+open);while(LocalDateTime.now().isBefore(open.plusSeconds(1)))Thread.sleep(250);allocate("1",true,2);ready();logout();
        finishNonBlindReviews(publicTitle,studentMarkdown);
    }
    private static void finishNonBlindReviews(String publicTitle,Path studentMarkdown)throws Exception{
        for(int i=0;i<2;i++){
            login(logins[i],passwords[i]);choose(field(workspace,"courseFilter",JComboBox.class),courseName);choose(field(workspace,"assignmentFilter",JComboBox.class),publicTitle);selectTab("我的互评任务");JTable tasks=field(workspace,"tasks",JTable.class);check(ui(tasks::getRowCount)==1,"N=1 tasks incorrect");
            check(ui(()->tasks.getValueAt(0,2)).equals(names[1-i]),"Non-blind author incorrectly hidden");ui(()->{tasks.setRowSelectionInterval(0,0);return null;});long task=ui(()->Long.parseLong(tasks.getValueAt(0,0).toString()));click(workspace,"查看 / 填写互评");ReviewDialog review=showingDialog(ReviewDialog.class);
            if(i==1){JTable table=first(review,JTable.class);check(ui(()->table.getValueAt(0,0)).equals(studentMarkdown.getFileName().toString()),"Non-blind filename hidden");ui(()->{table.setRowSelectionInterval(0,0);return null;});downloadDocument(review,output.resolve("nonblind-student.md"),studentMarkdown.getFileName().toString(),Files.readAllBytes(studentMarkdown));screenshot(review,"S01-nonblind-markdown");}
            for(JTextField score:ui(()->descendants(review,JTextField.class)))setText(score,"80");setText(lastEditableArea(review),"合成人工评语：模块边界明确，建议增加异常测试。");click(review,"提交人工互评");confirm();info("互评已保存");awaitGone(review);ready();logout();
        }
        pass("BLIND-02","关闭匿名后实际任务和评审窗口显示合成作者及原文件名；N=1 分配、Markdown 原字节下载和人工评分通过");
        login(teacherLogin,teacherPassword);scope();selectTab("操作记录");check(ui(()->fieldUnchecked(workspace,"logs",JTable.class).getRowCount())>0,"Operation history empty");screenshot(workspace,"S02-operation-log");
        List<String> actions=database.read(c->query(c,"SELECT DISTINCT action FROM operation_logs WHERE actor_id IN (SELECT id FROM users WHERE username IN (?,?,?,?))",r->r.getString(1),teacherLogin,logins[0],logins[1],logins[2]));
        check(actions.containsAll(List.of("ALLOCATE","SUBMIT","REVIEW","UPDATE_REVIEW","WITHDRAW_REVIEW")),"Expected actions missing from MySQL logs");pass("LOG-01","最终包教师操作记录页与真实 MySQL 一致，分配、提交、评分、修改和撤回均有日志");
        runConfigurationChecks();
    }
    private static void runConfigurationChecks()throws Exception{
        stage="configuration persistence and failure recovery";AppConfig active=field(workspace,"config",AppConfig.class);byte[] before=Files.readAllBytes(active.path());
        click(workspace,"连接配置");JDialog config=dialogContaining("连接配置与方案切换");fill(config,"MySQL JDBC 地址","jdbc:mysql://127.0.0.1:1/qa_invalid?connectTimeout=500&socketTimeout=1000");click(config,"测试连接与结构");warning("数据库操作失败");check(Arrays.equals(before,Files.readAllBytes(active.path())),"Invalid DB draft overwrote active config");click(config,"关闭");awaitGone(config);pass("CONFIG-01","最终包连接配置窗口测试无效数据库后活动配置与凭据保持原样，人工数据未受影响");
        click(workspace,"连接配置");JDialog aiConfig=dialogContaining("连接配置与方案切换");ui(()->{JTabbedPane tabs=firstUnchecked(aiConfig,JTabbedPane.class);tabs.setSelectedIndex(1);return null;});click(aiConfig,"测试 AI 连通性");
        await(()->visibleWarning()!=null||uiUnchecked(()->aiConfig.isEnabled()&&descendants(aiConfig,JTextArea.class).stream().anyMatch(a->a.getText().contains("未发起生成"))),"real AI models GET",Duration.ofSeconds(120));
        if(visibleWarning()!=null)throw new IllegalStateException("AI connectivity probe failed: "+message(visibleWarning()));fill(aiConfig,"另存方案名称","本次实机 AI "+run);click(aiConfig,"保存并切换");info("AI 配置已切换");awaitGone(aiConfig);check(showing(WorkspaceFrame.class)!=null,"AI-only config logged out user");pass("CONFIG-02","真实 AI 模型列表 GET 连通、独立方案保存与切换通过；没有新增内容生成");
        click(workspace,"连接配置");JDialog dbConfig=dialogContaining("连接配置与方案切换");fill(dbConfig,"另存方案名称","本次实机数据库 "+run);click(dbConfig,"保存并切换");confirm();info("数据库已切换");awaitGone(dbConfig);await(()->showing(LoginFrame.class)!=null&&showing(WorkspaceFrame.class)==null,"database change logout",UI_TIMEOUT);workspace=null;login(teacherLogin,teacherPassword);scope();
        AppConfig reread=new AppConfig(active.path());check(reread.get("db.url").equals(active.get("db.url"))&&reread.get("ai.model").equals(active.get("ai.model")),"Saved local config failed reload");
        var profiles=new edu.course.peerreview.config.ConfigProfiles(active);check(!profiles.list("db").isEmpty()&&!profiles.list("ai").isEmpty(),"Saved database or AI profile missing");pass("CONFIG-03","真实 MySQL 结构检查、方案保存和切换触发重新登录；本地文件重新读取及两个独立方案均保留");
        long tableCount=database.read(c->count(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"));check(tableCount==11,"Expected exactly 11 product tables");check(new SchemaManager().inspect(database).ready(),"Product schema inspection not ready");
        pass("SCHEMA-01","真实 MySQL 查询确认 11 张产品表，结构检查通过；没有创建远程数据库账号或改变权限");
        logout();LoginFrame login=showing(LoginFrame.class);click(login,"初始化专用库");JDialog init=dialogContaining("创建表结构和初始教师");fill(init,"教师用户名",teacherLogin);fill(init,"教师姓名","合成验收教师");fill(init,"教师密码（8位起）",teacherPassword);accept(init);confirm();warning("已有用户数据");pass("INIT-01","最终包真实初始化窗口拒绝覆盖已有数据，现有用户、课程和成绩保留");login(teacherLogin,teacherPassword);scope();
    }

    private static void assertSingleReviewStatistic(long submissionId, String total, int completed) throws Exception {
        JTable table = field(workspace, "statistics", JTable.class); check(ui(table::getRowCount) == 3, "Three submissions expected in real statistics");
        Statistics stat = field(workspace, "service", PeerReviewService.class).statistics(field(workspace, "actor", User.class), Long.parseLong(manifest.getProperty("assignmentId"))).stream()
                .filter(s -> s.submissionId() == submissionId).findFirst().orElseThrow();
        check(stat.completed() == completed && stat.assigned() == 2, "Statistics counts mismatch");
        ui(() -> {
            for (int r = 0; r < table.getRowCount(); r++) {
                if (completed > 0 && table.getValueAt(r, 2).equals("1 / 2")) {
                    decimal(table.getValueAt(r, 3), total); decimal(table.getValueAt(r, 4), total); decimal(table.getValueAt(r, 5), total);
                } else {
                    check(table.getValueAt(r, 2).equals("0 / 2"), "Unexpected completed count in table");
                    check(table.getValueAt(r, 3) == null && table.getValueAt(r, 4) == null && table.getValueAt(r, 5) == null, "Unreviewed rows must have empty scores");
                }
            }
            return null;
        });
        if (total == null) check(stat.average() == null && stat.highest() == null && stat.lowest() == null, "Withdrawn statistics must be empty");
        else decimal(stat.average(), total);
    }

    private static void postCheck() throws Exception {
        stage = "supplemental live CRUD and deadline checks";
        login(teacherLogin, teacherPassword); scope();
        choose(field(workspace, "assignmentFilter", JComboBox.class), "全部作业"); selectTab("作业管理");
        String temporary = "[实机验收 " + run + "] 空作业 CRUD";
        click(workspace, "发布作业"); AssignmentDialog form = showingDialog(AssignmentDialog.class);
        choose(first(form, JComboBox.class), courseName); fill(form, "作业名称", temporary); fill(form, "作业要求", "仅用于无提交作业的编辑删除验收，结束后删除。");
        ui(() -> { descendants(form, JCheckBox.class).get(0).setSelected(false); return null; });
        click(form, "保存作业"); awaitGone(form); ready();
        JTable table = field(workspace, "assignments", JTable.class); select(table, 2, temporary);
        check(ui(() -> table.getValueAt(table.getSelectedRow(), 6)).equals("否"), "Non-blind setting must persist");
        click(workspace, "编辑作业"); AssignmentDialog edit = showingDialog(AssignmentDialog.class);
        check(!ui(() -> descendants(edit, JCheckBox.class).get(0).isSelected()), "Edit must load saved blind switch");
        String renamed = temporary + "-已编辑"; fill(edit, "作业名称", renamed); fill(edit, "作业要求", "已通过实际编辑窗口更新说明。");
        ui(() -> { descendants(edit, JCheckBox.class).get(0).setSelected(true); return null; });
        click(edit, "保存作业"); awaitGone(edit); ready(); select(table, 2, renamed);
        check(ui(() -> table.getValueAt(table.getSelectedRow(), 6)).equals("是"), "Blind switch update must persist");
        screenshot(workspace, "09-empty-assignment-edited"); click(workspace, "删除作业"); confirm(); ready();
        check(ui(() -> row(table, 2, renamed)) < 0, "Unused assignment must be deleted");
        pass("ASSIGN-02", "无提交作业从真实窗口创建、编辑、删除，盲评开关关闭/开启均能保存并重新读取");
        selectTab("课程管理"); select(field(workspace, "courses", JTable.class), 1, courseName); click(workspace, "删除课程"); confirm(); warning("课程仍有作业"); ready();
        selectTab("学生账号"); select(field(workspace, "users", JTable.class), 1, logins[0]); click(workspace, "删除未使用学生"); confirm(); warning("已有提交、任务或操作历史"); ready();
        pass("PROTECT-01", "已有作业的课程及已有业务历史的学生不能删除，原验收成绩保留");
        logout(); login(logins[0], passwords[0]); scope(); selectTab("课堂作业"); select(field(workspace, "assignments", JTable.class), 2, assignmentName);
        click(workspace, "提交 / 更新作业"); JDialog submit = dialogContaining("提交作业");
        String original = ui(() -> firstUnchecked(submit, JTextArea.class).getText()); click(submit, "保存提交"); warning("提交已截止或互评已经开始");
        check(ui(() -> firstUnchecked(submit, JTextArea.class).getText()).equals(original), "Rejected late submission must retain text");
        click(submit, "取消"); awaitGone(submit); ready(); pass("SUB-05", "真实截止后/已结束作业禁止再次提交，原文本和成绩未被覆盖");
        selectTab("我的成绩"); JTable stats = field(workspace, "statistics", JTable.class);
        check(ui(stats::getRowCount) == 1, "Student must see only own results");
        check(ui(() -> stats.getValueAt(0, 1)).equals(names[0]), "Student result must belong to logged-in account");
        decimal(ui(() -> stats.getValueAt(0, 3)), "81.50"); screenshot(workspace, "10-student-own-result");
        pass("PERMISSION-02", "学生成绩窗口只展示自己的汇总结果，真实平均分正确");
        manifest.setProperty("postCheckCompleted", "true"); saveManifest();
    }

    private static void prepareCourse() throws Exception {
        selectTab("课程管理");
        click(workspace, "新建课程"); JDialog form = dialogContaining("新建课程"); fill(form, "课程名称", " "); fill(form, "课程说明", "合成验收说明"); accept(form); warning("课程名称不能为空"); ready();
        pass("COURSE-01", "空课程名称被拒绝");
        click(workspace, "新建课程"); form = dialogContaining("新建课程"); fill(form, "课程名称", courseName); fill(form, "课程说明", "独立标记的合成实机验收课程，不包含真实学生信息。"); accept(form); ready();
        select(field(workspace, "courses", JTable.class), 1, courseName); click(workspace, "编辑课程"); form = dialogContaining("编辑课程"); fill(form, "课程说明", "已通过真实课程编辑窗口更新。"); accept(form); ready();
        pass("COURSE-02", "从真实窗口新建并编辑课程");
        String empty = courseName + "-空课程"; click(workspace, "新建课程"); form = dialogContaining("新建课程"); fill(form, "课程名称", empty); fill(form, "课程说明", "仅用于删除验收，结束后不保留。"); accept(form); ready();
        select(field(workspace, "courses", JTable.class), 1, empty); click(workspace, "删除课程"); confirm(); ready();
        check(ui(() -> row(fieldUnchecked(workspace, "courses", JTable.class), 1, empty)) < 0, "Empty course should be deleted"); pass("COURSE-03", "空课程删除完成，没有误删其他课程");
    }
    private static void prepareStudents() throws Exception {
        selectTab("学生账号");
        for (int i = 0; i < 3; i++) createStudent(logins[i], names[i], passwords[i]);
        String spare = "qa_" + run.replace('-', '_') + "_unused"; createStudent(spare, "验收临时账号-" + run, secret());
        select(field(workspace, "users", JTable.class), 1, spare); click(workspace, "删除未使用学生"); confirm(); ready();
        check(ui(() -> row(fieldUnchecked(workspace, "users", JTable.class), 1, spare)) < 0, "Unused account must be deleted");
        select(field(workspace, "users", JTable.class), 1, logins[0]); click(workspace, "编辑学生姓名"); JDialog rename = anyDialog();
        setText(first(rename, JTextField.class), names[0] + "-编辑验证"); accept(rename); ready();
        select(field(workspace, "users", JTable.class), 1, logins[0]); click(workspace, "编辑学生姓名"); rename = anyDialog(); setText(first(rename, JTextField.class), names[0]); accept(rename); ready();
        click(workspace, "添加学生账号"); JDialog duplicate = dialogContaining("添加学生"); fill(duplicate, "用户名", logins[0]); fill(duplicate, "学生姓名", names[0]); fill(duplicate, "初始密码（8位起）", secret()); accept(duplicate); warning("记录冲突"); ready();
        pass("USER-01", "学生账号创建、姓名编辑、未使用账号删除和重名冲突防护均通过");
    }
    private static void createStudent(String login, String name, String password) throws Exception {
        click(workspace, "添加学生账号"); JDialog d = dialogContaining("添加学生"); fill(d, "用户名", login); fill(d, "学生姓名", name); fill(d, "初始密码（8位起）", password); accept(d); ready();
        check(ui(() -> row(fieldUnchecked(workspace, "users", JTable.class), 1, login)) >= 0, "Created student not visible");
    }
    private static void prepareAssignment() throws Exception {
        selectTab("作业管理"); click(workspace, "发布作业"); AssignmentDialog form = showingDialog(AssignmentDialog.class);
        JComboBox<?> courses = first(form, JComboBox.class); choose(courses, courseName);
        fill(form, "作业名称", assignmentName); fill(form, "作业要求", "【合成验收材料】请分析模块化、测试和异常处理；不要署名。评分由学生独立完成。");
        fill(form, "互评开始", "2026-02-30 25:99"); click(form, "保存作业"); warning("时间格式不合法");
        reviewOpen = LocalDateTime.now().plusMinutes(3).withSecond(0).withNano(0);
        manifest.setProperty("reviewOpen", reviewOpen.toString()); saveManifest();
        fill(form, "互评开始", AssignmentDialog.TIME.format(reviewOpen)); fill(form, "互评截止", AssignmentDialog.TIME.format(reviewOpen.plusMinutes(15)));
        JTable rules = first(form, JTable.class); edit(rules, 0, 1, "59"); click(form, "保存作业"); warning("权重之和");
        edit(rules, 0, 1, "60"); click(form, "添加维度"); ui(() -> { rules.setRowSelectionInterval(3, 3); return null; }); click(form, "删除所选维度");
        check(ui(rules::getRowCount) == 3, "Dynamic criterion add/remove failed");
        edit(rules, 1, 2, "10"); edit(rules, 1, 3, "20"); edit(rules, 2, 3, "10");
        screenshot(form, "01b-assignment-rules"); click(form, "保存作业"); awaitGone(form); ready();
        select(field(workspace, "assignments", JTable.class), 2, assignmentName); click(workspace, "查看要求"); JDialog details = dialogContaining(assignmentName);
        check(ui(() -> firstUnchecked(details, JTextArea.class).getText()).contains("10.00—20.00"), "Saved custom score range should be visible"); ui(() -> { details.dispose(); return null; });
        allocate("2", false); warning("只能在互评开始时间"); ready();
        pass("ASSIGN-01", "非法日期和权重被拒绝，动态维度和不同分值区间保存成功；提前分配被拒绝");
    }
    private static void changeStudentPassword(int i) throws Exception {
        String changed = secret(); click(workspace, "修改密码"); JDialog d = dialogContaining("修改密码"); fill(d, "原密码", passwords[i]); fill(d, "新密码（8位起）", changed); accept(d); info("密码已修改"); ready();
        passwords[i] = changed; manifest.setProperty("student." + i + ".password", changed); manifest.setProperty("student." + i + ".passwordChanged", "true"); saveManifest();
        pass("AUTH-03", "合成学生从真实窗口修改密码，后续以新密码重新登录");
    }
    private static void verifyAllocations(long assignment) {
        List<long[]> rows = database.read(c -> query(c, "SELECT t.submission_id,t.reviewer_id,s.student_id FROM review_tasks t JOIN submissions s ON s.id=t.submission_id WHERE s.assignment_id=?", r -> new long[]{r.getLong(1), r.getLong(2), r.getLong(3)}, assignment));
        check(rows.size() == 6, "Six persistent tasks expected"); Map<Long, Integer> submissionCounts = new HashMap<>(), reviewerCounts = new HashMap<>(); Set<String> pairs = new HashSet<>();
        for (long[] row : rows) { check(row[1] != row[2], "Self-review detected"); check(pairs.add(row[0] + ":" + row[1]), "Duplicate review pair"); submissionCounts.merge(row[0], 1, Integer::sum); reviewerCounts.merge(row[1], 1, Integer::sum); }
        check(submissionCounts.size() == 3 && reviewerCounts.size() == 3 && submissionCounts.values().stream().allMatch(n -> n == 2) && reviewerCounts.values().stream().allMatch(n -> n == 2), "Allocation imbalance");
    }
    private static void verifyForeignTaskDenied(long assignment) throws Exception {
        PeerReviewService service = field(workspace, "service", PeerReviewService.class); User actor = field(workspace, "actor", User.class);
        long foreign = database.read(c -> query(c, "SELECT t.id FROM review_tasks t JOIN submissions s ON s.id=t.submission_id WHERE s.assignment_id=? AND t.reviewer_id<>? LIMIT 1", r -> r.getLong(1), assignment, actor.id()).get(0));
        try { service.task(actor, foreign); throw new AssertionError("Foreign task unexpectedly accessible"); }
        catch (BusinessException expected) { check(expected.getMessage().contains("自己的评审任务"), "Unexpected authorization error"); }
        pass("PERMISSION-01", "真实任务数据中只能读取分配给自己的任务（服务层额外验证）");
    }
    private static void verifyAi(ReviewDialog review, int studentIndex) throws Exception {
        if (Boolean.parseBoolean(manifest.getProperty("aiAttempted", "false"))) throw new IllegalStateException("A model request was already attempted; no automatic paid retry");
        check(ui(() -> descendants(review, JTextField.class).stream().allMatch(t -> t.getText().isEmpty())), "Human score inputs must start empty");
        manifest.setProperty("aiAttempted", "true"); saveManifest();
        click(review, "生成参考意见"); confirm();
        await(() -> uiUnchecked(() -> review.isEnabled()) || visibleWarning() != null, "actual AI response", Duration.ofSeconds(120));
        if (visibleWarning() != null) throw new IllegalStateException("Actual AI generation failed: " + message(visibleWarning()));
        JTextArea reference = ui(() -> descendants(review, JTextArea.class).stream().filter(a -> !a.isEditable()).reduce((a, b) -> b).orElseThrow());
        String text = ui(reference::getText); check(!text.contains("尚未生成") && !text.contains("正在请求") && text.length() > 40, "Actual reference text missing");
        check(ui(() -> descendants(review, JTextField.class).stream().allMatch(t -> t.getText().isEmpty())), "AI must not fill human scores");
        check(ui(() -> lastEditableAreaUnchecked(review).getText().isEmpty()), "AI must not fill human comment");
        screenshot(review, "05a-real-ai-reference");
        PeerReviewService service = field(workspace, "service", PeerReviewService.class); User actor = field(workspace, "actor", User.class);
        long taskId = Long.parseLong(review.getTitle().replace("互评任务 ", ""));
        check(service.reviewDetail(actor, taskId) == null && service.advice(actor, taskId) != null, "AI must persist only advice, not a human review");
        pass("AI-01", "实际 AI 返回参考意见并独立持久化，人工分数/评语仍为空，任务未被代交");
        enterReview(review, studentIndex);
        AppConfig actual = field(workspace, "config", AppConfig.class); String address = actual.get("ai.baseUrl"), timeout = actual.get("ai.timeoutSeconds"), originalKey=actual.get("ai.apiKey"), originalEnvironment=actual.get("ai.useEnvironmentKey");
        try {
            actual.set("ai.baseUrl", "http://127.0.0.1:1"); actual.set("ai.timeoutSeconds", "5");actual.set("ai.apiKey","fault-test-not-a-real-key");actual.set("ai.useEnvironmentKey","false");
            click(review, "生成参考意见"); confirm(); warning("AI 网络");
            check(ui(reference::getText).equals(text), "AI failure must restore previous reference");
            check(ui(() -> lastEditableAreaUnchecked(review).getText()).equals(comment(studentIndex)), "AI failure must retain human comment");
            check(ui(() -> descendants(review, JTextField.class).get(0).getText()).equals(new String[]{"80","90","70"}[studentIndex]), "AI failure must retain human score");
            screenshot(review, "05b-ai-failure-recovered");
            pass("AI-02", "回环连接失败故障注入后恢复旧意见并保留人工输入；未额外触发付费生成");
        } finally { actual.set("ai.baseUrl", address); actual.set("ai.timeoutSeconds", timeout);actual.set("ai.apiKey",originalKey);actual.set("ai.useEnvironmentKey",originalEnvironment); }
        manifest.setProperty("aiVerified", "true"); saveManifest();
    }
    private static void enterReview(ReviewDialog review, int i) throws Exception {
        List<JTextField> fields = ui(() -> descendants(review, JTextField.class)); String[][] values = {{"80", "19", "7"}, {"90", "18", "10"}, {"70", "18", "9"}};
        check(fields.size() == 3, "Three manual criteria expected"); for (int j = 0; j < 3; j++) setText(fields.get(j), values[i][j]); setText(lastEditableArea(review), comment(i));
    }
    private static void verifyDuplicateDenied(long assignment) throws Exception {
        PeerReviewService service = field(workspace, "service", PeerReviewService.class); User actor = field(workspace, "actor", User.class); ReviewTask task = service.tasks(actor, assignment).get(0);
        ReviewDetail detail = service.reviewDetail(actor, task.id());
        try { service.completeReview(actor, task.id(), detail.scores(), detail.comment()); throw new AssertionError("Duplicate review accepted"); }
        catch (BusinessException expected) { check(expected.getMessage().contains("已完成"), "Wrong duplicate error"); }
    }
    private static void verifyFilters() throws Exception {
        selectTab("互评进度"); choose(field(workspace, "statusFilter", JComboBox.class), "待互评");
        check(ui(() -> fieldUnchecked(workspace, "tasks", JTable.class).getRowCount()) == 0, "No pending tasks expected");
        choose(field(workspace, "statusFilter", JComboBox.class), "已完成"); check(ui(() -> fieldUnchecked(workspace, "tasks", JTable.class).getRowCount()) == 6, "Six completed tasks expected");
        setText(field(workspace, "regex", JTextField.class), "评审A"); click(workspace, "筛选");
        check(ui(() -> fieldUnchecked(workspace, "tasks", JTable.class).getRowCount()) == 2, "Regex should match only reviewer A's two comments");
        setText(field(workspace, "regex", JTextField.class), "["); click(workspace, "筛选"); warning("正则表达式不合法");
        setText(field(workspace, "regex", JTextField.class), "模块化"); click(workspace, "筛选");
        check(ui(() -> fieldUnchecked(workspace, "tasks", JTable.class).getRowCount()) == 6, "Content regex should match all six tasks"); screenshot(workspace, "06-combined-filter");
        setText(field(workspace, "regex", JTextField.class), ""); choose(field(workspace, "statusFilter", JComboBox.class), "全部任务状态"); click(workspace, "筛选");
        pass("FILTER-01", "课程+作业+任务状态+正文/评语正则组合筛选和非法正则恢复通过");
    }
    private static void verifyStatistics(JTable table) throws Exception {
        check(ui(table::getRowCount) == 3, "Exactly three statistics rows expected");
        ui(() -> {
            for (int r = 0; r < table.getRowCount(); r++) {
                String name = table.getValueAt(r, 1).toString(); check(expectedAverages.containsKey(name), "Unexpected student in statistics");
                check(table.getValueAt(r, 2).equals("2 / 2"), "All reviews must count");
                decimal(table.getValueAt(r, 3), expectedAverages.get(name)); decimal(table.getValueAt(r, 4), expectedHighest.get(name)); decimal(table.getValueAt(r, 5), expectedLowest.get(name));
            }
            return null;
        });
    }
    private static void verifyCsv(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path); check(bytes.length > 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF, "CSV requires UTF-8 BOM");
        List<List<String>> rows = parseCsv(Files.readString(path, StandardCharsets.UTF_8)); check(rows.size() == 4, "Header and three CSV rows expected");
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i); String exported = row.get(1); String name = exported.startsWith("'") ? exported.substring(1) : exported;
            check(expectedAverages.containsKey(name), "Unexpected CSV student");
            if (name.startsWith("=")) check(exported.startsWith("'="), "Formula prefix was not neutralized");
            check(row.get(2).equals("2") && row.get(3).equals("2"), "CSV review counts mismatch"); decimal(row.get(4), expectedAverages.get(name)); decimal(row.get(5), expectedHighest.get(name)); decimal(row.get(6), expectedLowest.get(name));
        }
    }
    private static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quoted = false;
        for (int i = text.startsWith("﻿") ? 1 : 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') { if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; } else quoted = !quoted; }
            else if (c == ',' && !quoted) { row.add(cell.toString()); cell.setLength(0); }
            else if (c == '\n' && !quoted) { row.add(cell.toString()); cell.setLength(0); rows.add(row); row = new ArrayList<>(); }
            else if (c != '\r' || quoted) cell.append(c);
        }
        if (!cell.isEmpty() || !row.isEmpty()) { row.add(cell.toString()); rows.add(row); } return rows;
    }

    private static void login(String name, String password) throws Exception {
        LoginFrame frame = showing(LoginFrame.class); if (frame == null) throw new IllegalStateException("No product login frame");
        fill(frame, "用户名", name); fill(frame, "密码", password); click(frame, "登录");
        await(() -> showing(WorkspaceFrame.class) != null || visibleWarning() != null, "workspace after login", UI_TIMEOUT);
        if (visibleWarning() != null) throw new IllegalStateException("Login rejected: " + message(visibleWarning()));
        workspace = showing(WorkspaceFrame.class); ready();
    }
    private static void logout() throws Exception { click(workspace, "退出登录"); await(() -> showing(LoginFrame.class) != null, "login after logout", UI_TIMEOUT); workspace = null; }
    private static void ready() throws Exception {
        await(() -> uiUnchecked(() -> workspace != null && workspace.isShowing() && workspace.isEnabled()), "workspace ready", UI_TIMEOUT);
        if (visibleWarning() != null) throw new IllegalStateException("Unexpected product warning: " + message(visibleWarning()));
    }
    private static void scope() throws Exception { choose(field(workspace, "courseFilter", JComboBox.class), courseName); choose(field(workspace, "assignmentFilter", JComboBox.class), assignmentName); }
    private static void selectTab(String name) throws Exception { ui(() -> { JTabbedPane tabs = fieldUnchecked(workspace, "tabs", JTabbedPane.class); for (int i = 0; i < tabs.getTabCount(); i++) if (tabs.getTitleAt(i).equals(name)) { tabs.setSelectedIndex(i); return null; } throw new AssertionError("Missing tab: " + name); }); }
    private static List<String> tabTitles(WorkspaceFrame frame) throws Exception { return ui(() -> { JTabbedPane tabs = fieldUnchecked(frame, "tabs", JTabbedPane.class); List<String> titles = new ArrayList<>(); for (int i = 0; i < tabs.getTabCount(); i++) titles.add(tabs.getTitleAt(i)); return titles; }); }
    private static void allocate(String count, boolean success) throws Exception {
        allocate(count,success,6);
    }
    private static void allocate(String count, boolean success,int expectedTasks) throws Exception {
        click(workspace, "随机分配互评"); JDialog input = anyDialog(); setText(first(input, JTextField.class), count); accept(input); confirm();
        if (success) info("已生成 "+expectedTasks+" 条互评任务");
    }
    private static String synthetic(int i, boolean updated) {
        return "【合成验收文本 " + (i + 1) + "】\n这是公开的合成测试内容，不属于任何真实学生。\n模块化设计将界面、业务规则和数据访问分离，以便独立测试。\n评分应遵守细则，并对非法输入、空文本和截止时间做防御性校验。\n随机分配需要避免自评和重复，并保证每份作业获得相同数量的评审。\n" + (updated ? "更新版：补充 JDBC 事务回滚与回归测试方案。" : "初版：后续还可以补充异常恢复和测试设计。");
    }
    private static String comment(int i) { return "评审" + (char) ('A' + i) + "：模块边界清晰；建议补充异常恢复与边界条件的具体测试证据。此评语为人工验收脚本填写，不取自 AI。"; }
    private static void decimal(Object actual, String expected) { check(actual != null && new BigDecimal(actual.toString()).compareTo(new BigDecimal(expected)) == 0, "Numeric mismatch: " + actual + " vs " + expected); }
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
    private static String secret() { byte[] bytes = new byte[18]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static void saveManifest() throws Exception { try (var writer = Files.newBufferedWriter(manifestPath, StandardCharsets.UTF_8)) { manifest.store(writer, "Live acceptance synthetic accounts; never commit or distribute"); } }
    private static void pass(String id, String description) throws Exception { String line = id + " | PASS | " + description; results.removeIf(previous -> previous.startsWith(id + " | ")); results.add(line); System.out.println(line); Files.write(output.resolve("progress.txt"), results, StandardCharsets.UTF_8); }
    private static void report(String outcome, Throwable error) throws Exception {
        if (output == null) return;
        boolean mutations = "review-mutations".equals(manifest.getProperty("scenario"));
        String method = fullFeatures ? "实际 Swing 全流程 + 远程隔离 MySQL，包含教师与学生文档附件、匿名下载和提取文本；真实 AI 调用以 AI 单项记录为准。" : mutations ? "实际 App.main / Swing 窗口事件 + 远程 MySQL，修改→撤回→重新评分；本轮不调用 AI。" : "实际 App.main / Swing 窗口事件 + 配置的远程 MySQL + 一次合成文本 AI 生成；额外服务层检查单独注明。";
        StringBuilder text = new StringBuilder("# 完整实机验收记录\n\n").append("- 验收批次：").append(run).append("\n- 时间：").append(LocalDateTime.now()).append("\n- 结果：**").append(outcome).append("**\n- 阶段：").append(stage).append("\n- 方法：").append(method).append("\n- 验收课程：").append(courseName).append("\n- 验收账号口令仅在被忽略的本地 manifest 中，不在本报告中。\n\n## 检查结果\n\n| 编号 | 结果 | 内容 |\n|---|---|---|\n");
        results.forEach(line -> text.append("| ").append(line).append(" |\n"));
        if (error != null) text.append("\n## 阻断\n\n").append(error.getClass().getSimpleName()).append(": ").append(error.getMessage()).append('\n');
        text.append("\n## 截图\n\n"); screenshots.forEach(name -> text.append("- [").append(name).append("](").append(name).append(")\n"));
        text.append("\n## 范围说明\n\n合成学生与合成课程独立标记，不修改其他课程、提交和成绩。截图为实际产品窗口组件像素。\n");
        if(Boolean.getBoolean("peerreview.acceptance.packaged"))text.append("本次使用最终便携包独立解压后的 JAR 与包内 Java，测试驱动以独立 javaagent 装载，产品不加载开发 classes。JAR SHA-256：").append(manifest.getProperty("productSha256")).append("。\n");
        Files.writeString(output.resolve("report.md"), text, StandardCharsets.UTF_8);
    }
    private static void screenshot(Window window, String name) throws Exception {
        if (window == null || output == null) return;
        BufferedImage image = ui(() -> {
            check(window.isShowing(), "Evidence must come from an actual opened product window");
            JRootPane pane = window instanceof JFrame frame ? frame.getRootPane() : ((JDialog) window).getRootPane();
            BufferedImage capture = new BufferedImage(pane.getWidth(), pane.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = capture.createGraphics();
            try { graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, capture.getWidth(), capture.getHeight()); pane.printAll(graphics); }
            finally { graphics.dispose(); }
            return capture;
        });
        ImageIO.write(image, "png", output.resolve(name + ".png").toFile());
        if (!screenshots.contains(name + ".png")) screenshots.add(name + ".png");
    }

    private static <T> T ui(Callable<T> action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return action.call();
        FutureTask<T> task = new FutureTask<>(action); SwingUtilities.invokeAndWait(task); return task.get();
    }
    private static <T> T uiUnchecked(Callable<T> action) { try { return ui(action); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static void await(BooleanSupplier condition, String label, Duration timeout) throws Exception {
        long until = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < until) { if (condition.getAsBoolean()) return; Thread.sleep(100); }
        throw new IllegalStateException("Timed out waiting for " + label + "; visible windows=" + ui(() -> Arrays.stream(Window.getWindows()).filter(Window::isShowing).map(w -> w instanceof Frame f ? f.getTitle() : w instanceof Dialog d ? d.getTitle() : w.getClass().getSimpleName()).toList()));
    }
    private static <T extends Window> T showing(Class<T> type) { return uiUnchecked(() -> Arrays.stream(Window.getWindows()).filter(w -> type.isInstance(w) && w.isShowing()).map(type::cast).findFirst().orElse(null)); }
    private static <T extends JDialog> T showingDialog(Class<T> type) throws Exception { await(() -> showing(type) != null, type.getSimpleName(), UI_TIMEOUT); return showing(type); }
    private static JDialog dialogContainingNow(String title) { return Arrays.stream(Window.getWindows()).filter(w -> w instanceof JDialog d && d.isShowing() && d.getTitle().contains(title)).map(JDialog.class::cast).reduce((a, b) -> b).orElse(null); }
    private static JDialog dialogContaining(String title) throws Exception { await(() -> uiUnchecked(() -> dialogContainingNow(title) != null), "dialog " + title, UI_TIMEOUT); return ui(() -> dialogContainingNow(title)); }
    private static JDialog anyDialog() throws Exception { await(() -> uiUnchecked(() -> Arrays.stream(Window.getWindows()).anyMatch(w -> w instanceof JDialog && w.isShowing())), "modal dialog", UI_TIMEOUT); return ui(() -> Arrays.stream(Window.getWindows()).filter(w -> w instanceof JDialog && w.isShowing()).map(JDialog.class::cast).reduce((a, b) -> b).orElseThrow()); }
    private static Window activeWindow() { return uiUnchecked(() -> Arrays.stream(Window.getWindows()).filter(Window::isShowing).reduce((a, b) -> b).orElse(null)); }
    private static JDialog visibleWarning() { return uiUnchecked(() -> dialogContainingNow("操作提示")); }
    private static void awaitGone(Window window) throws Exception { await(() -> !uiUnchecked(window::isShowing), "dialog close", UI_TIMEOUT); }
    private static void confirm() throws Exception { accept(dialogContaining("请确认")); }
    private static void warning(String expected) throws Exception { JDialog warning = dialogContaining("操作提示"); check(message(warning).contains(expected), "Unexpected validation warning: " + message(warning) + "; expected " + expected); accept(warning); awaitGone(warning); }
    private static void info(String expected) throws Exception { JDialog info = dialogContaining("课堂同伴互评"); check(message(info).contains(expected), "Unexpected info: " + message(info)); accept(info); awaitGone(info); }
    private static String message(JDialog dialog) { return uiUnchecked(() -> descendants(dialog, JOptionPane.class).stream().map(JOptionPane::getMessage).map(LiveAcceptance::plain).findFirst().orElse("")); }
    private static String plain(Object object) { if (object instanceof Object[] array) return String.join("\n", Arrays.stream(array).map(LiveAcceptance::plain).toList()); return String.valueOf(object); }
    private static void accept(JDialog dialog) throws Exception {
        JButton button = ui(() -> { JButton primary = dialog.getRootPane().getDefaultButton(); return primary != null ? primary : descendants(dialog, JButton.class).stream().filter(Component::isShowing).findFirst().orElseThrow(); });
        SwingUtilities.invokeLater(button::doClick); Thread.sleep(80);
    }
    private static void click(Window window, String text) throws Exception {
        JButton b = ui(() -> button(window, text)); check(ui(b::isEnabled), "Disabled button: " + text);
        SwingUtilities.invokeLater(b::doClick); Thread.sleep(100);
    }
    private static JButton button(Component root, String text) { return descendants(root, JButton.class).stream().filter(b -> text.equals(b.getText()) && b.isShowing()).findFirst().orElseThrow(() -> new IllegalStateException("No visible button: " + text)); }
    private static void fill(Component root, String label, String value) throws Exception {
        ui(() -> {
            JLabel name = descendants(root, JLabel.class).stream().filter(l -> label.equals(l.getText())&&l.isShowing()).findFirst().orElseThrow(() -> new IllegalStateException("No visible field label: " + label));
            Container row = name.getParent(); Component input = ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.CENTER);
            if (input instanceof JScrollPane scroll) input = scroll.getViewport().getView();
            if (input instanceof javax.swing.text.JTextComponent text) text.setText(value); else throw new IllegalStateException("Field is not text: " + label); return null;
        });
    }
    private static void setText(javax.swing.text.JTextComponent field, String text) throws Exception { ui(() -> { field.setText(text); return null; }); }
    private static void choose(JComboBox<?> box, String text) throws Exception { ui(() -> { for (int i = 0; i < box.getItemCount(); i++) if (text.equals(box.getItemAt(i).toString())) { box.setSelectedIndex(i); return null; } throw new IllegalStateException("Choice not found: " + text); }); }
    private static void select(JTable table, int column, String text) throws Exception { ui(() -> { int r = row(table, column, text); if (r < 0) throw new IllegalStateException("Table row not found: " + text); table.setRowSelectionInterval(r, r); table.scrollRectToVisible(table.getCellRect(r, column, true)); return null; }); }
    private static int row(JTable table, int column, String text) { for (int r = 0; r < table.getRowCount(); r++) if (text.equals(Objects.toString(table.getValueAt(r, column), ""))) return r; return -1; }
    private static void edit(JTable table, int row, int column, String text) throws Exception {
        ui(() -> { check(table.editCellAt(row, column), "Cannot edit criteria cell"); ((JTextField) table.getEditorComponent()).setText(text); TableCellEditor editor = table.getCellEditor(); check(editor.stopCellEditing(), "Cell editing failed"); return null; });
    }
    private static JTextArea lastEditableArea(Component root) throws Exception { return ui(() -> lastEditableAreaUnchecked(root)); }
    private static JTextArea lastEditableAreaUnchecked(Component root) { return descendants(root, JTextArea.class).stream().filter(JTextArea::isEditable).reduce((a, b) -> b).orElseThrow(); }
    private static JFileChooser activeChooser() { return uiUnchecked(() -> Arrays.stream(Window.getWindows()).filter(Window::isShowing).flatMap(w -> descendants(w, JFileChooser.class).stream()).findFirst().orElse(null)); }
    private static <T extends Component> T first(Component root, Class<T> type) throws Exception { return ui(() -> firstUnchecked(root, type)); }
    private static <T extends Component> T firstUnchecked(Component root, Class<T> type) { return descendants(root, type).stream().filter(Component::isShowing).findFirst().orElseThrow(() -> new IllegalStateException("No visible " + type.getSimpleName())); }
    private static <T extends Component> List<T> descendants(Component root, Class<T> type) { List<T> result = new ArrayList<>(); if (type.isInstance(root)) result.add(type.cast(root)); if (root instanceof Container c) for (Component child : c.getComponents()) result.addAll(descendants(child, type)); return result; }
    private static <T> T field(Object instance, String name, Class<T> type) throws Exception { return ui(() -> fieldUnchecked(instance, name, type)); }
    private static <T> T fieldUnchecked(Object instance, String name, Class<T> type) { try { Field f = instance.getClass().getDeclaredField(name); f.setAccessible(true); return type.cast(f.get(instance)); } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); } }
}
