# 完整实机验收记录

- 验收批次：1015-163842-c97c
- 时间：2026-10-15T16:52:38.575920700
- 结果：**PASS**
- 阶段：complete
- 方法：实际 App.main / Swing 窗口事件 + 配置的远程 MySQL + 一次合成文本 AI 生成；额外服务层检查单独注明。
- 验收课程：[实机验收 1015-163842-c97c] 软件构造
- 验收账号口令仅在被忽略的本地 manifest 中，不在本报告中。

## 检查结果

| 编号 | 结果 | 内容 |
|---|---|---|
| COURSE-01 | PASS | 空课程名称被拒绝 |
| COURSE-02 | PASS | 从真实窗口新建并编辑课程 |
| COURSE-03 | PASS | 空课程删除完成，没有误删其他课程 |
| USER-01 | PASS | 学生账号创建、姓名编辑、未使用账号删除和重名冲突防护均通过 |
| ASSIGN-01 | PASS | 非法日期和权重被拒绝，动态维度和不同分值区间保存成功；提前分配被拒绝 |
| SUB-01 | PASS | 空提交在真实窗口被拒绝，填写窗口仍保留 |
| SUB-02 | PASS | 提交后从窗口重新读取并更新文本 |
| AUTH-03 | PASS | 合成学生从真实窗口修改密码，后续以新密码重新登录 |
| AUTH-01 | PASS | 错误密码被实际登录窗口拒绝 |
| AUTH-02 | PASS | 真实教师账号登录成功 |
| RESUME-01 | PASS | 恢复同一批次，复用已完成的建课、账号和规则验收；不复制已有数据 |
| SUB-03 | PASS | 三名学生分别提交，学生界面只显示自己的文本记录 |
| SUB-04 | PASS | 教师从实际窗口读取三份提交 |
| STATE-01 | PASS | 已有提交的作业被保护，不能删除 |
| ALLOC-01 | PASS | 人数不足 N=3 被拒绝，未留下部分分配 |
| ALLOC-02 | PASS | 三人每份两评：共六条任务，无自评、重复或负载不均 |
| STATE-02 | PASS | 重复分配和未完成时提前结束均被真实界面拒绝 |
| PERMISSION-01 | PASS | 真实任务数据中只能读取分配给自己的任务（服务层额外验证） |
| AI-01 | PASS | 实际 AI 返回参考意见并独立持久化，人工分数/评语仍为空，任务未被代交 |
| AI-02 | PASS | 回环连接失败故障注入后恢复旧意见并保留人工输入；未额外触发付费生成 |
| SCORE-01 | PASS | 真实评分窗口拒绝越界分数，修正输入后可继续 |
| REVIEW-01 | PASS | 空人工评语不能保存，原评分输入未丢失 |
| REVIEW-2 | PASS | 合成学生 1 的两条人工互评已保存，完成后只读且服务端拒绝重复提交 |
| REVIEW-3 | PASS | 合成学生 2 的两条人工互评已保存，完成后只读且服务端拒绝重复提交 |
| REVIEW-4 | PASS | 合成学生 3 的两条人工互评已保存，完成后只读且服务端拒绝重复提交 |
| FILTER-01 | PASS | 课程+作业+任务状态+正文/评语正则组合筛选和非法正则恢复通过 |
| STAT-01 | PASS | 真实界面均值、最高分、最低分与独立计算结果一致 |
| CSV-01 | PASS | 经真实文件选择器导出，中文、BOM、三行统计和公式前缀转义正确 |
| STATE-03 | PASS | 全部互评完成后可正常结束作业 |
| PERSIST-01 | PASS | 退出并重新登录后统计仍正确，提交/人工评分/AI 参考操作有持久化记录 |
| PRE-01 | PASS | 真实数据库连接成功；使用私有配置，不打印凭据 |
| UI-01 | PASS | 从 App.main 打开真实产品登录窗口 |
| ASSIGN-02 | PASS | 无提交作业从真实窗口创建、编辑、删除，盲评开关关闭/开启均能保存并重新读取 |
| PROTECT-01 | PASS | 已有作业的课程及已有业务历史的学生不能删除，原验收成绩保留 |
| SUB-05 | PASS | 真实截止后/已结束作业禁止再次提交，原文本和成绩未被覆盖 |
| PERMISSION-02 | PASS | 学生成绩窗口只展示自己的汇总结果，真实平均分正确 |

## 截图

- [01-login.png](01-login.png)
- [01b-saved-assignment-rules.png](01b-saved-assignment-rules.png)
- [02-student-1-submission.png](02-student-1-submission.png)
- [02-student-2-submission.png](02-student-2-submission.png)
- [02-student-3-submission.png](02-student-3-submission.png)
- [03-teacher-submissions.png](03-teacher-submissions.png)
- [04-teacher-six-tasks.png](04-teacher-six-tasks.png)
- [05-manual-review-1.png](05-manual-review-1.png)
- [05-manual-review-2.png](05-manual-review-2.png)
- [05-manual-review-3.png](05-manual-review-3.png)
- [05a-real-ai-reference.png](05a-real-ai-reference.png)
- [05b-ai-failure-recovered.png](05b-ai-failure-recovered.png)
- [06-combined-filter.png](06-combined-filter.png)
- [07-statistics.png](07-statistics.png)
- [08-operation-history.png](08-operation-history.png)
- [09-empty-assignment-edited.png](09-empty-assignment-edited.png)
- [10-student-own-result.png](10-student-own-result.png)

## 范围说明

合成学生与合成课程独立标记，不修改其他课程、提交和成绩。未打包 JAR/ZIP。实机通过不等于 WindowBuilder 操作和最终课程报告已经完成。

## 证据采集与恢复说明

所有图片均为实际已打开产品窗口的组件专属快照（JRootPane.printAll），数据来自真实远程 MySQL；不是 H2 预览，也不是操作系统桌面截图。第一轮因桌面截图混入其他窗口而暂停，相关图片已删除，同批次恢复后重新采集应用组件证据，未再次触发已完成的建课/提交写入。

本记录含 35 个业务/环境检查点及 1 条恢复记录，不把恢复操作作为额外功能测试，也不等同于 53 项默认 JUnit 用例。有效模型生成仅 1 次，另有 1 次本机未监听端口的连接失败注入，后者未到达模型服务。

## 实际统计

| 合成学生 | 已评/分配 | 平均分 | 最高分 | 最低分 |
|---|---|---|---|---|
| A | 2/2 | 81.50 | 88.00 | 75.00 |
| B | 2/2 | 78.50 | 82.00 | 75.00 |
| C（名字以 = 开头用于检验 CSV 防公式注入） | 2/2 | 85.00 | 88.00 | 82.00 |

[通过产品导出的 CSV](live-review-results.csv)。合成账号凭据留在本机 config/acceptance-1015-163842-c97c.local.properties，未复制到证据目录。
