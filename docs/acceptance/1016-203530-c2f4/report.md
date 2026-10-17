# 完整实机验收记录

- 验收批次：1016-203530-c2f4
- 时间：2026-10-16T20:39:36.641073200
- 结果：**PASS**
- 阶段：complete
- 方法：实际 Swing 全流程 + 远程隔离 MySQL，包含教师与学生文档附件、匿名下载和提取文本；真实 AI 调用以 AI 单项记录为准。
- 验收课程：[实机验收 1016-203530-c2f4] 软件构造
- 验收账号口令仅在被忽略的本地 manifest 中，不在本报告中。

## 检查结果

| 编号 | 结果 | 内容 |
|---|---|---|
| PRE-01 | PASS | 真实数据库连接成功；使用私有配置，不打印凭据 |
| UI-01 | PASS | 从 App.main 打开真实产品登录窗口 |
| COURSE-01 | PASS | 空课程名称被拒绝 |
| COURSE-02 | PASS | 从真实窗口新建并编辑课程 |
| COURSE-03 | PASS | 空课程删除完成，没有误删其他课程 |
| USER-01 | PASS | 学生账号创建、姓名编辑、未使用账号删除和重名冲突防护均通过 |
| ASSIGN-01 | PASS | 非法日期和权重被拒绝，动态维度和不同分值区间保存成功；提前分配被拒绝 |
| FILE-01 | PASS | 教师通过真实文件选择器发布 DOCX、PDF、TXT 三个资料，正文与附件共同保存 |
| FILE-02 | PASS | 三人实际提交 DOCX-only、DOC+文本、PDF+文本；仅文档提交可替换并移除临时附件 |
| FILE-03 | PASS | 删除测试上传源文件后，教师仍可从 MySQL 预览/下载修订 DOCX，字节与 SHA 对应原文 |
| ALLOC-FULL | PASS | 三份作业产生六条任务，旁观未提交学生未参与，排除自评和重复 |
| FILE-04 | PASS | 未提交、未被分配的学生不能按 ID 读取其他人的提交附件（服务层额外检查） |
| AI-01 | PASS | 实际 AI 返回参考意见并独立持久化，人工分数/评语仍为空，任务未被代交 |
| AI-02 | PASS | 回环连接失败故障注入后恢复旧意见并保留人工输入；未额外触发付费生成 |
| AI-FILE | PASS | 当前默认公网接口实际读取合成 DOCX 与教师资料，参考意见独立保存，没有代填分数或人工评语 |
| FILE-05 | PASS | 各评审人通过匿名另存获取文档真实字节，五种格式参与发布/提交，六条人工评分全部完成 |
| FILTER-01 | PASS | 课程+作业+任务状态+正文/评语正则组合筛选和非法正则恢复通过 |
| FILE-06 | PASS | 仅文档提交的提取正文可正则搜索，课程/作业/状态筛选、均值极值与 CSV 都正确 |
| PERSIST-FULL | PASS | 结束互评及重新登录后，文档内容、任务、评分和统计保留 |

## 截图

- [01-login.png](01-login.png)
- [01b-assignment-rules.png](01b-assignment-rules.png)
- [A01-teacher-three-documents.png](A01-teacher-three-documents.png)
- [A02-document-only-replace-remove.png](A02-document-only-replace-remove.png)
- [A03-teacher-document-read-download.png](A03-teacher-document-read-download.png)
- [A04-blind-file-review-1.png](A04-blind-file-review-1.png)
- [A04-blind-file-review-2.png](A04-blind-file-review-2.png)
- [05a-real-ai-reference.png](05a-real-ai-reference.png)
- [05b-ai-failure-recovered.png](05b-ai-failure-recovered.png)
- [A04-blind-file-review-3.png](A04-blind-file-review-3.png)
- [06-combined-filter.png](06-combined-filter.png)
- [A05-complete-results.png](A05-complete-results.png)

## 范围说明

合成学生与合成课程独立标记，不修改其他课程、提交和成绩。未打包 JAR/ZIP。实机通过不等于 WindowBuilder 操作和最终课程报告已经完成。
