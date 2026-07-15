# CLAUDE.md — 本项目协作指南

## 项目身份

这是 **工程实践与创新能力大赛** 的参赛项目，核心产出是一套基于 AnyLogic 的数字孪生仿真模型 +
配套 Java 代码，用于优化汽车混流装配线。

## 技术边界

- **主仿真平台**：AnyLogic（商业软件，用 Java 编写模型逻辑）
- **本仓库的代码**：纯 Java，可嵌入 AnyLogic 的 Java 代码片段，也可独立编译运行
- **不要引入** Spring / Hibernate / 任何 Web 框架 — 会与 AnyLogic 冲突
- **最小化外部依赖** — 目前只有 Gson（JSON 解析），其余全部手写

## 目录含义

- `data/*.json` — 仿真参数。改参数改这里，**不要硬编码在 Java 里**
- `experiments/` — 竞赛演示用的入口主类，跑 `mvn exec:java`
- `src/main/java/assemblyline/` — 核心框架代码
- `docs/` — 竞赛报告草稿

## 编码规范

- Java 11 语法，包名 `assemblyline.*`
- 领域枚举放在 `model/` 包下
- 概率分布（三角 / Weibull / 正态）统一走 `utils/Distribution`
- 随机数统一走 `utils/RandomGenerator`，支持重置种子（蒙特卡洛复现性）
- 类和方法加 Javadoc，但只写非 obvious 的内容
- 中文注释只用在领域特定术语处

## 竞赛特定约束

- 每次提交的代码必须**可编译通过**（`mvn compile`）
- 仿真实验的结果要能导出为 CSV（供竞赛报告绘图）
- 与队友和 AI 一起生成的文档中，如果某参数来源不明确，在对应 JSON 里加 `"source": "TODO: 与队友确认"` 字段
- 凡使用 AnyLogic 内置 API 的地方，加注释标记 `// AnyLogic API`，方便后续移植

## 不能做的事

- 不要删除或重命名 `data/` 下的 JSON 文件（队友会基于这些参数写 AnyLogic 模型）
- 不要引入超过 3 个外部依赖
- 不要把数据说明书里的参数直接散落在代码里，统一进 JSON
