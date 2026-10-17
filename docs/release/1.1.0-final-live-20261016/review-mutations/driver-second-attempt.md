# 完整实机验收记录

- 验收批次：1016-213515-c6bf
- 时间：2026-10-16T21:44:02.779828200
- 结果：**FAIL**
- 阶段：non-blind classroom and all attachment formats
- 方法：实际 App.main / Swing 窗口事件 + 远程 MySQL，修改→撤回→重新评分；本轮不调用 AI。
- 验收课程：[实机验收 1016-213515-c6bf] 软件构造
- 验收账号口令仅在被忽略的本地 manifest 中，不在本报告中。

## 检查结果

| 编号 | 结果 | 内容 |
|---|---|---|
| COURSE-01 | PASS | 空课程名称被拒绝 |
| COURSE-02 | PASS | 从真实窗口新建并编辑课程 |
| COURSE-03 | PASS | 空课程删除完成，没有误删其他课程 |
| USER-01 | PASS | 学生账号创建、姓名编辑、未使用账号删除和重名冲突防护均通过 |
| ASSIGN-01 | PASS | 非法日期和权重被拒绝，动态维度和不同分值区间保存成功；提前分配被拒绝 |
| MUTATE-01 | PASS | 真实窗口提交人工评分 82.00，版本为 1 |
| MUTATE-02 | PASS | 真实窗口显式修改后保存 88.00 和人工评语，版本递增，评分明细没有重复 |
| MUTATE-03 | PASS | 真实 MySQL 上的旧版本修改和撤回均被拒绝（服务层额外验证） |
| MUTATE-04 | PASS | 教师实际统计面板立即显示修改后的 88.00，人数未增加 |
| MUTATE-05 | PASS | 本人真实窗口撤回后评分及明细删除，任务恢复待互评，分配关系保留 |
| MUTATE-06 | PASS | 撤回最后一份评分后已评人数为 0、均值和极值为空，不伪装成 0 分 |
| MUTATE-07 | PASS | 撤回后重新填写得到新评分记录，旧窗口即使版本同为 1 也不能撤回新记录 |
| MUTATE-08 | PASS | 教师重新登录及导出 CSV 读取新成绩，修改/撤回日志各一条，无重复评分 |
| PRE-01 | PASS | 真实数据库连接成功；使用私有配置，不打印凭据 |
| PACKAGE-01 | PASS | 实际 java -jar 启动最终解压 JAR，产品来源与 SHA-256 已核对；使用包内 Java 17.0.20.1 |
| UI-01 | PASS | 从 App.main 打开真实产品登录窗口 |
| FILE-07 | PASS | 最终包真实教师编辑窗口替换 TXT 为 Markdown 并移除临时附件，存库重新读取与提取正确 |
| SUB-02 | PASS | 最终包真实学生窗口完成 Markdown 附件提交和纯文本提交更新，再读内容一致 |

## 阻断

AssertionError: Unexpected info: 已生成 2 条互评任务。

## 截图

- [01-login.png](01-login.png)
- [01b-assignment-rules.png](01b-assignment-rules.png)
- [11-edit-review.png](11-edit-review.png)
- [12-statistics-after-edit.png](12-statistics-after-edit.png)
- [13-teacher-read-only.png](13-teacher-read-only.png)
- [14-withdraw-confirmation.png](14-withdraw-confirmation.png)
- [15-task-pending-again.png](15-task-pending-again.png)
- [16-statistics-after-withdrawal.png](16-statistics-after-withdrawal.png)
- [17-statistics-after-resubmission.png](17-statistics-after-resubmission.png)
- [failure.png](failure.png)

## 范围说明

合成学生与合成课程独立标记，不修改其他课程、提交和成绩。截图为实际产品窗口组件像素。
本次使用最终便携包独立解压后的 JAR 与包内 Java，测试驱动以独立 javaagent 装载，产品不加载开发 classes。JAR SHA-256：f3d277abc05c176b58872479aec2449811e04ca89213f5db6235076478c595cf。
