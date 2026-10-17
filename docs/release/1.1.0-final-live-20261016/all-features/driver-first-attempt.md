# 完整实机验收记录

- 验收批次：1016-212856-fd6e
- 时间：2026-10-16T21:32:10.659919300
- 结果：**FAIL**
- 阶段：full mandatory and optional classroom setup
- 方法：实际 Swing 全流程 + 远程隔离 MySQL，包含教师与学生文档附件、匿名下载和提取文本；真实 AI 调用以 AI 单项记录为准。
- 验收课程：[实机验收 1016-212856-fd6e] 软件构造
- 验收账号口令仅在被忽略的本地 manifest 中，不在本报告中。

## 检查结果

| 编号 | 结果 | 内容 |
|---|---|---|
| PRE-01 | PASS | 真实数据库连接成功；使用私有配置，不打印凭据 |
| PACKAGE-01 | PASS | 实际 java -jar 启动最终解压 JAR，产品来源与 SHA-256 已核对；使用包内 Java 17.0.20.1 |
| UI-01 | PASS | 从 App.main 打开真实产品登录窗口 |
| AUTH-01 | PASS | 错误密码在最终运行包实际登录窗口被拒绝 |
| COURSE-01 | PASS | 空课程名称被拒绝 |
| COURSE-02 | PASS | 从真实窗口新建并编辑课程 |
| COURSE-03 | PASS | 空课程删除完成，没有误删其他课程 |
| USER-01 | PASS | 学生账号创建、姓名编辑、未使用账号删除和重名冲突防护均通过 |
| ASSIGN-01 | PASS | 非法日期和权重被拒绝，动态维度和不同分值区间保存成功；提前分配被拒绝 |
| FILE-01 | PASS | 教师通过真实文件选择器发布 DOCX、PDF、TXT 三个资料，正文与附件共同保存 |
| SUB-01 | PASS | 最终包拒绝空文本且无附件的提交，原表单可继续使用 |
| AUTH-03 | PASS | 合成学生从真实窗口修改密码，后续以新密码重新登录 |
| FILE-02 | PASS | 三人实际提交 DOCX-only、DOC+文本、PDF+文本；仅文档提交可替换并移除临时附件 |
| FILE-03 | PASS | 删除测试上传源文件后，教师仍可从 MySQL 预览/下载修订 DOCX，字节与 SHA 对应原文 |
| ALLOC-FULL | PASS | 三份作业产生六条任务，旁观未提交学生未参与，排除自评和重复 |

## 阻断

ExecutionException: java.lang.IllegalStateException: No visible JTextField

## 截图

- [01-login.png](01-login.png)
- [01b-assignment-rules.png](01b-assignment-rules.png)
- [A01-teacher-three-documents.png](A01-teacher-three-documents.png)
- [A02-document-only-replace-remove.png](A02-document-only-replace-remove.png)
- [A03-teacher-document-read-download.png](A03-teacher-document-read-download.png)
- [failure.png](failure.png)

## 范围说明

合成学生与合成课程独立标记，不修改其他课程、提交和成绩。截图为实际产品窗口组件像素。
本次使用最终便携包独立解压后的 JAR 与包内 Java，测试驱动以独立 javaagent 装载，产品不加载开发 classes。JAR SHA-256：f3d277abc05c176b58872479aec2449811e04ca89213f5db6235076478c595cf。
