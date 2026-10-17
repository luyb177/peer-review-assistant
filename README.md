# 课堂同伴互评助手

软件构造课程个人项目，最终交付版本 1.1.0。Java 17、Maven、Swing、MySQL/JDBC、JUnit；不是 Web 应用。

已实现任务书全部必选与四项选做：课程/作业/学生管理、动态评分、文本/文档提交、随机 N 人分配、盲评、人工互评、AI 参考、正则与表驱动筛选、统计和 CSV，以及防重复、日志、教师/学生文档和匿名开关。支持本人截止前修改或撤回评分。

## 最终验证与文档

- 默认回归 123 项通过，失败/错误/跳过均为 0。
- 最终 Windows 便携包独立解压，以包内 Java 17 和实际 java -jar 完成两批真实 MySQL/Swing 实测：30 个主流程检查点和 25 个修改/撤回及补充检查点均通过。两批有重复启动检查，不混算为 55 个独立功能。
- 实际 AI 内容生成一次，参考意见与人工评分独立保存；另有故障恢复及模型列表 GET 检查。
- 新 JVM 读取三份提交、六次评分、一条 AI 意见和六个附件，字节、SHA-256 与统计均正确。
- 产品 JAR SHA-256：f3d277abc05c176b58872479aec2449811e04ca89213f5db6235076478c595cf。

[课程设计报告](docs/课程设计报告.md)、[用户手册](docs/用户简易使用手册.md)、[最终交付说明](docs/交付说明与当前进度.md)的 DOCX 与 Markdown 均更新为最终状态。

[完整实机验收](docs/release/1.1.0-final-live-20261016/最终运行包实机验收.md)含原始中断、修正和恢复记录；未发现产品业务缺陷，修正的是验收驱动。早期 1.0.0 或开发类路径记录仅为历史证据。

## 运行

完整作业包的 03_运行程序/peer-review-assistant 自带 Java 17，双击 run.cmd，无需 IDEA/Maven 或系统 Java。源码目录含 bin/peer-review-assistant.jar，已安装 Java 17+ 时也可运行 run.cmd 或 java -jar bin/peer-review-assistant.jar。

分发包不含数据库密码、API Key 或维护者演示服务凭据。登录页打开“连接配置”填写自己的 MySQL；新空专用库通过“初始化专用库”设置首个教师，按需选择合成演示数据。数据库账号与应用教师账号不同。AI 暂时关闭时仍可人工互评；启用后填写匹配的 anthropic/openai-compatible 地址、模型及 Key。

程序启动登录页不会自动访问数据库或生成 AI 内容。详细步骤见用户手册和[运行与配置](docs/运行与配置.md)。

## 源码构建与测试

需要 JDK 17+、Maven 和依赖下载网络，在本目录执行：

```bash
mvn test
mvn clean package
```

输出 target/peer-review-assistant.jar 和 target/peer-review-assistant-submission.zip。build.cmd 可执行相同构建。重建会产生新的产物，应重新核对版本、哈希和运行结果。

默认测试使用 H2（仅测试依赖）和本机模拟接口，不访问真实 MySQL 或付费 AI。源码中的显式 LiveAcceptance/PackagedLiveAgent 不属于默认测试发现；真实验收会写入合成数据，须阅读[实机运行说明](docs/live-acceptance.md)并显式启用。

## 数据库与配置

产品使用 MySQL，src/main/resources/schema.sql 定义 11 张表；sql/01-review-version.sql 与 sql/02-document-attachments.sql 用于旧版无损升级。部署和建表可能需要 CREATE/REFERENCES/ALTER 权限，正常业务使用专用库的必要读写权限。结构冲突不自动清库。

config/application.example.properties 是无凭据样例。个人 application.properties、config/profiles 和验收 manifest 都是本地文件，不应提交或分发。配置保存明文口令，应限制权限。备份数据库不能仅复制客户端目录。

## 规则摘要

互评开始也是提交截止。只有已提交者参与，人数 M≥2，1≤N<M，禁止自评及重复。每项按自己的分值区间归一化，权重之和恰为100；无人评分时统计为空。已有提交后冻结作业规则与教师资料。AI 不代填、不代交，失败可继续人工互评。

附件支持 PDF/DOC/DOCX/TXT/MD，最多3个、每个5 MiB；原件存 MySQL，下载校验字节和 SHA-256。盲评隐藏姓名和文件名，不清除正文或文档内部身份线索。无 OCR，预览是提取文字。

## 设计与过程

- [需求符合性核对](docs/需求符合性核对.md)
- [设计与业务说明](docs/设计与业务说明.md)
- [重构记录](docs/重构记录.md)
- [测试说明](docs/测试说明.md)
- [WindowBuilder 实际使用](docs/WindowBuilder.md)
- UML：docs/uml/classes.puml；docs/assets/

WindowBuilder 证据来自代表性 LoginPanel 的真实 Design 编辑与保存，不声称所有窗口都是设计器生成，也不将自动化操作写成提交者私人手工经历。实际图形验收、已用隔离库验证不等于新装操作系统、空库部署或多机压力测试。
