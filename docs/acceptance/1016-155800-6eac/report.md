# 完整实机验收记录

- 验收批次：1016-155800-6eac
- 时间：2026-10-16T16:01:53.523255200
- 结果：**PASS**
- 阶段：complete
- 方法：实际 App.main / Swing 窗口事件 + 远程 MySQL，修改→撤回→重新评分；本轮不调用 AI。
- 验收课程：[实机验收 1016-155800-6eac] 软件构造
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
| MUTATE-01 | PASS | 真实窗口提交人工评分 82.00，版本为 1 |
| MUTATE-02 | PASS | 真实窗口显式修改后保存 88.00 和人工评语，版本递增，评分明细没有重复 |
| MUTATE-03 | PASS | 真实 MySQL 上的旧版本修改和撤回均被拒绝（服务层额外验证） |
| MUTATE-04 | PASS | 教师实际统计面板立即显示修改后的 88.00，人数未增加 |
| MUTATE-05 | PASS | 本人真实窗口撤回后评分及明细删除，任务恢复待互评，分配关系保留 |
| MUTATE-06 | PASS | 撤回最后一份评分后已评人数为 0、均值和极值为空，不伪装成 0 分 |
| MUTATE-07 | PASS | 撤回后重新填写得到新评分记录，旧窗口即使版本同为 1 也不能撤回新记录 |
| MUTATE-08 | PASS | 教师重新登录及导出 CSV 读取新成绩，修改/撤回日志各一条，无重复评分 |

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

## 范围说明

合成学生与合成课程独立标记，不修改其他课程、提交和成绩。未打包 JAR/ZIP。实机通过不等于 WindowBuilder 操作和最终课程报告已经完成。

## 本轮说明

使用正式配置中的公网 MySQL 地址，不使用 SSH 隧道，不调用 AI。本批次共 15 个通过检查点，其中 8 个为修改/撤回专项，7 个为环境与建课准备。默认回归另有 72 项，不混为同一测试数量。

评分变化为：82.00（首次提交、版本 1）→88.00（修改、版本 2）→撤回（无有效评分、任务待评）→75.00（新记录、版本 1）。修改前后的记录 ID 相同；重新提交获得新的记录 ID，旧窗口不能操作新记录。

截图通过已打开窗口的组件渲染获取，不截取桌面。11-edit-review 是点击计算预览之前的编辑状态，最终生效分数以随后统计面板及断言为准。期间界面提示已进一步改为“修改中，请重新计算”，最终回归通过。

[实际产品导出的 CSV](review-mutation-results.csv)。本轮账号口令仅在本机 config/acceptance-1016-155800-6eac.local.properties，未复制到证据目录。原 2026-10-15 已结束课程没有重开或修改。
