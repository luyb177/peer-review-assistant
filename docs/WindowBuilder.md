# WindowBuilder 实际使用与验证

日期：2026-10-16。

## 1. 本次完成了什么

不是只添加 Maven 依赖或写一个 WindowBuilder 标记，而是在本机安装并启动真正的 Eclipse + WindowBuilder：

1. 将实际登录页的视图提取为 `LoginPanel`，继续由 `LoginFrame` 使用。
2. 在 Eclipse 的 WindowBuilder Editor 中打开该产品 Java 文件，进入 Design 页面。
3. 在组件树选择登录表单的 `headingLabel`，通过真实的属性表文本编辑控件修改 `JLabel.text`。
4. 将“进入课堂”改成“登录互评课堂”，由 WindowBuilder 生成 AST/Java 修改并保存到产品源文件。
5. 切到 Source 查看生成代码，再切回 Design，核对预览中的标题已改变。
6. 对保存后的产品代码编译、回归测试，并启动实际应用验证登录事件。

设计器操作使用本地的显式 Eclipse 验证扩展自动驱动编辑器及属性控件，不冒充学生手动拖拽的经历。扩展没有直接用字符串替换或直接修改模型属性来生成这次标题变更；保存来自 WindowBuilder 属性编辑器。产品运行不需要该扩展或 Eclipse。

**范围：本次是登录表单的真实可视化编辑，不声称其余所有 Swing 窗口都由 WindowBuilder 从零生成。** 当前 Design 图聚焦 `formPanel` 登录表单区域，程序运行时仍展示完整双栏登录页。

## 2. 环境

- Eclipse IDE for Java Developers 2026-09，平台 4.41，实际启动 buildId：`4.41.0.20260903-0719`。
- 官方 WindowBuilder Swing Designer 1.25.0 更新站点安装。
- 实际验证使用本机 JetBrains Runtime 25.0.4；WindowBuilder 自身基线为 Java 21+，但本次 Eclipse 2026-09 发行包要求 Java 25 并自带 JustJ 25。后续便携启动脚本使用其自带运行时。这与产品 Maven 编译目标 Java 17 是两件事。
- 环境在本机项目 `.tools/eclipse-java/`，独立 workspace 在 `.tools/windowbuilder-workspace/`。
- 没有更改 IDEA、系统 JAVA_HOME 或全局 Eclipse 安装。

`.tools` 不进入 Git/交付包。在其他电脑上需自行安装 Eclipse + WindowBuilder，或继续使用 IDEA 维护、运行标准 Swing 源码。

## 3. 源码结构与设计器兼容性

- [LoginPanel.java](../src/main/java/edu/course/peerreview/ui/LoginPanel.java)：无参构造，只创建标准 Swing 控件和布局，不读配置、不连数据库、不调用 AI。
- [LoginFrame.java](../src/main/java/edu/course/peerreview/ui/LoginFrame.java)：组装视图，绑定登录、连接配置、测试数据库连接、初始化事件；保留原有业务方法。
- 布局使用 GridLayout、GridBagLayout、BorderLayout、FlowLayout，组件创建和属性设置为设计器可识别的显式语句，不把布局藏在 `Ui.column()` 等自制工厂里。
- 通过 getter 暴露输入框和按钮；视图自身不注册数据库/登录事件，控制器再绑定。
- `@wbp.parser.entryPoint` 是辅助解析标记，不作为“已经使用工具”的证据；真实工具结果在截图与校验文件中。

本机独立 Eclipse 项目的 src 是链接到实际 `src/main/java` 的资源，未复制一份演示登录页冒充产品实现。设计器保存后，IDEA/Maven 使用的正是同一份源文件。

## 4. 后续如何继续编辑

### 本机已准备的便携环境

运行 `scripts/windowbuilder.cmd` 打开独立 Eclipse workspace。验证扩展默认不执行自动编辑（只有显式 JVM 验证开关才启动）。

在 Package Explorer 中找到：

```text
peer-review-windowbuilder
  src
    edu.course.peerreview.ui
      LoginPanel.java
```

如果默认打开 Java 源码编辑器，右键文件 → Open With → WindowBuilder Editor，切到底部 Design。选择组件树中的 `headingLabel` 或输入框/按钮，在 Properties 中查看和编辑属性；`text` 不在可见区域时向下滚动属性表。

保存后切到 Source 检查生成的 Java，再运行 Maven 测试。

### 在其他 Eclipse 环境

1. 使用带 JDT 的 Eclipse Java 发行版。
2. Help → Install New Software，填写官方稳定更新站点，安装 Swing Designer 及其依赖。
3. 导入本 Maven 项目并等待依赖解析，配置可用 JDK。
4. 对 `LoginPanel.java` 执行 Open With → WindowBuilder Editor。
5. 确认项目文件编码为 UTF-8，以免中文属性显示或回写错误。

WindowBuilder 内部可能按局部容器选择可编辑的根；本次测试的根为 `formPanel`。不要据此把设计时单栏预览当成运行时完整双栏界面。

## 5. 证据与可核对性

实际证据统一放在 `docs/windowbuilder/20261016/`：

- Design 变更前、保存后图和 Source 图。
- `result.json`：实际编辑器类、产品文件路径、修改属性、前后文字和文件 SHA-256。
- 变更前源码及真实差异，便于确认不是只写了一句使用说明。
- 产品运行登录页与教师工作台图。
- Maven 测试结果摘要和实际远程登录结果。

Eclipse 图使用只针对该 Eclipse 窗口句柄的原生窗口捕获，不截用户桌面；运行期产品图从本应用组件提取，不包含配置密码或 API Key。

## 6. 工具效果与局限（可作为第 7 章报告素材）

### 应用的知识

Swing 容器与布局、组件属性、事件驱动、视图与业务分离、可视化设计器对 Java 源码的双向解析。

### 达到的效果

- 真实设计器能解析产品表单、展示组件树和属性表，并将属性编辑同步回 Java。
- 事件仍由业务窗口绑定，设计期不依赖 MySQL 或 AI，也不会误初始化业务数据。
- 设计器生成的是普通 Swing 代码，离开 Eclipse 后仍可在 IDEA/Maven 编译运行。
- 新增测试检查组件、标签关联、默认按钮、事件绑定和实际登录，避免“预览能看、运行失效”。

### 深入思考与限制

- 可视化工具能减少布局试错，但不能替代权限、评分、事务、截止时间和异常恢复设计。
- 原有 `Ui` 工厂和动态界面代码不一定能完整逆向解析；需要把适合设计器的静态布局与业务回调分开，而不是把整个业务类硬塞进设计器。
- 多容器根选择、工具默认 Look and Feel、平台字体及窗口大小会使设计预览与 FlatLaf 实际运行不同，必须同时做运行期检查。
- 自动化需适配内部编辑器 API，属于测试工具的维护成本，不应把 WindowBuilder 内部库引入产品依赖。
- 本次只对代表性登录表单完成实际工具使用；其他业务窗口保留手写 Swing。课程报告应准确描述范围，不写成“所有窗口均拖拽生成”。

## 7. 官方资料

- WindowBuilder 项目：https://eclipse.dev/windowbuilder/
- 官方安装与更新站点：https://eclipse.dev/windowbuilder/downloads/
- 本次固定更新站点：https://download.eclipse.org/windowbuilder/updates/release/1.25.0/
- Eclipse Java 发行包：https://www.eclipse.org/downloads/packages/release/2026-09/r/eclipse-ide-java-developers

没有使用非官方改包或向项目加入来历不明的 GUI 依赖。WindowBuilder 阶段未打包；后续的可运行包与配置切换见 [发行验收](release/20261016/report.md)。未提交或推送代码。
