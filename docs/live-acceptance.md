# 显式实机验收：运行说明

## 最终便携包实测入口

`scripts/verify-packaged-live.ps1` 以独立解压目录为目标，真正执行包内 Java 的 `java -jar`。测试专用 javaagent 只携带验收驱动类；产品类必须来自该目录的 JAR，并记录其 SHA-256。启动工作目录是另一空目录，不依赖 IDEA、系统 Java 或开发目录的产品 classes。

先编译测试驱动（`mvn test-compile`），独立解压最终 ZIP，再通过产品界面配置已有的专用测试 MySQL 和可用 AI 服务。测试配置还需本地填写 `acceptance.teacher.username`、`acceptance.teacher.password`，用于专用测试教师；不要分发这份配置或带测试配置的解压目录。可用已初始化的专用验收库，测试不会要求管理员创建新账号或扩大权限。

```powershell
scripts/verify-packaged-live.ps1 -Bundle 'D:/path/to/extracted/peer-review-assistant' -Mode '--all-features' -AllowAiOnce
scripts/verify-packaged-live.ps1 -Bundle 'D:/path/to/extracted/peer-review-assistant' -Mode '--review-mutations'
```

第一条会生成一次真实 AI 参考意见，须有用户授权后显式传 `-AllowAiOnce`；第二条不生成 AI 内容，但会查询真实 AI 模型列表。两个模式均写入独立标记的合成业务数据。第二条还补验非匿名作者与文件名、Markdown、教师附件替换/移除、学生文本更新、操作日志、配置方案与重复初始化保护。

驱动支持从“已分配、尚未评分”的完整批次恢复：使用 `--resume-all-features -Resume <私有批次manifest> -AllowAiOnce`，每批次的 AI 尝试标记与 manifest 防止重复生成。`--configuration-only -Resume <私有批次manifest>` 只恢复已完成业务批次的连接配置检查，不再创建课程或调用生成。`--resume-supplement -Resume <私有批次manifest>` 可恢复修改撤回后的补验；仅允许重建本批尚无提交的临时非匿名作业，或继续已分配且尚未评分的非匿名任务。恢复须先核对实际阶段；不支持任意断点重放。

新 JVM 的只读核验入口为 `PackagedPersistenceProbe`，它通过最终产品 JAR 再读三份提交、六条评分、一条独立 AI 意见和六个附件，并逐个核对附件长度/SHA-256。成功意味着该批次跨 JVM 持久化通过，不代表验证了全新操作系统环境。

日志位于 `target/packaged-live-*.log`，业务报告和窗口组件截图位于 `target/live-acceptance/<批次>/`。可分发经过检查的报告和合成材料，不分发 `config/*local.properties`、私有配置方案或整个测试解压目录。

## 与普通测试的区别

普通 `mvn test` 不访问远程数据库，不调用付费 AI。`LiveAcceptance` 没有 JUnit 注解，名称也不匹配默认测试发现规则，必须显式启动。

实机验收会：

- 通过 `App.main` 打开真实 Swing 应用，使用当前私有配置连接 MySQL。
- 在 Swing 事件线程填写实际表单、触发实际按钮、选择表格和标签页，走正常事件处理及业务保存路径。
- 创建独立标记的验收课程、作业及三名合成学生；不修改原有课程和成绩。
- 做一次合成作业的真实 AI 生成，并注入一次未监听回环端口的连接失败；后者不会到达模型服务。
- 通过真实文件选择器导出 CSV，独立核对预期统计和公式转义。
- 为核对分配、持久化及越权防护，额外执行只读 SQL 和明确标记的业务层反向测试。

这不是在后台直接写好分数后展示界面，也不把 H2 测试假称为远程验收。

## 环境与风险

- 需要真实图形桌面、JDK 17+ 和可用 Maven；不支持在 headless 环境跳过后声称验收成功。
- `config/application.properties` 必须已有可用 MySQL/AI 配置。
- 教师用户名及初始密码从 `config/initial-login.local.properties` 读取。如果教师密码已改，可用 `PEER_REVIEW_TEACHER_PASSWORD` 环境变量覆盖，不要写入命令参数或提交代码。
- 模型请求可能计费。每批次在请求前记录 `aiAttempted`，失败不自动重发。
- 这是有写入副作用的验收，不要对不允许新增合成记录的生产库运行。
- 窗口操作只作用于验收 JVM 内本应用的组件，不使用全局键盘/鼠标操作其他软件。

## 手动启动

在 Windows 终端明确设置环境变量 `PEER_REVIEW_LIVE_ACCEPTANCE=1` 后，运行 `scripts/live-acceptance.cmd`。脚本不会构建 JAR/ZIP。

也可在 IDEA 的测试源集中运行 `edu.course.peerreview.ui.LiveAcceptance.main`，参数传项目绝对路径。传第二个参数为原有私有 manifest 的绝对路径时，可恢复「已发布、已提交齐全、尚未分配」的同一批次。已完成主流程的批次可再传第三个参数 `--post-check`，只补验空作业 CRUD、历史数据保护、截止后提交和学生成绩权限，不再次调用 AI。其他阶段的任意断点恢复不在此驱动的承诺范围内；已经过真实提交截止且未提交齐全的批次不能靠修改时钟或跳过校验来恢复，应保留失败记录，重新安排验收。

新建验收作业的互评开始时间约为三分钟后。用例会等待真实时间到达，不伪造时钟或直接修改数据库时间。

## 仅验收修改/撤回（不调用 AI）

向 LiveAcceptance 的项目路径后传 `--review-mutations`，或向启用后的脚本传该参数，可新增独立合成课堂并走真实窗口“提交评分→修改→统计→撤回→空统计→重新提交→CSV”。这个模式不调用 AI，适合本次互评 CRUD 验收。

如确需使用独立的私有数据库配置，可设置 `PEER_REVIEW_ACCEPTANCE_CONFIG` 为配置文件的绝对路径；不设置时仍使用原来的公网配置。该入口仅属于显式测试驱动，不改变产品默认连接方式。

## 产物与隐私

- `target/live-acceptance/<批次>/report.md`：结果与范围说明。
- 同目录的 PNG：实际已打开产品窗口的组件专属快照；组件数据来自真实 MySQL。
- 同目录的 `live-review-results.csv`：通过产品导出的 CSV。
- `config/acceptance-<批次>.local.properties`：合成账号凭据、批次和检查点；受 Git 忽略，不分发。

截图用 `JRootPane.printAll` 提取本应用组件像素，不截取用户桌面。Windows 其他窗口遮挡不会进入截图，也不会被脚本关闭或修改。应明确区分这种应用组件快照与操作系统屏幕截图。

验收成功后保留一套带批次标记的合成数据，便于人工复查；不会自动清空业务库。不要把本地私密 manifest 或整个包含配置的目录直接压缩提交。
