# 团队分工与里程碑

> 三人团队：队长（技术总负责）+ 队友 A（AnyLogic 可视化）+ 队友 B（报告 + 数据可视化）。
> 按「可并行 + 边界清晰」设计，最大程度减少互相等待。

---

## 总览

| | 队长（你） | 队友 A | 队友 B |
|---|---|---|---|
| **定位** | 技术总负责 + 算法 | AnyLogic 可视化数字孪生 | 报告 + 数据可视化 |
| **核心交付** | Java 仿真稳定 + 实验验证 | AnyLogic 动画模型 | 竞赛报告 + 答辩 PPT |
| **上游依赖** | 框架（已完成） | 仿真 API + 参数定义 | CSV 结果 + 框架说明 |
| **下游依赖** | A 的联调 + B 的参数答疑 | 无 | A 的截图 + 队长的实验结论 |

---

## 队长职责细目

### 1. 仿真算法收尾（1–2 天）

- `SimulationEngine` 稳定性压测：跑 1000 次蒙特卡洛，确认无 OOM / 无死循环。
- 修复队友提的 bug（肯定会有）。
- 把 `SequenceOptimizer` 的迭代改进跑通，生成「优化序列 vs 启发式序列」的对比数据。

### 2. 实验验证（与 B 并行）

- 跑完 200 次基准 + 200 次优化，把 CSV 交给 B。
- 灵敏度分析：把 Weibull η 扰动 ±10%，再跑一轮，验证 S3 鲁棒性。
- 整理实验结论（非计划停机降低多少、故障预警提前量多少），写成一段话给 B 贴进报告。

### 3. 给 A 提供联调接口

- 把仿真核心逻辑封装成 A 能调用的 API（`SimulationEngine.run()` 返回 `RunResult`，A 不需要懂内部）。
- 回答 A 的「这个参数在 AnyLogic 里怎么设」的问题。

### 4. 最终整合

- A 的 AnyLogic 模型 + B 的报告 + 你的实验结果 = 最终交付。
- 检查三份产物风格统一。

---

## 队友 A 职责细目 —— AnyLogic 可视化数字孪生

**前提**：装 AnyLogic PLE（免费）或专业版。不需要懂 Java，但要会 AnyLogic 的 Process Modeling Library。

### Day 1：Hello World 装配线（今天开始）

1. 装 AnyLogic PLE：https://www.anylogic.com/downloads/
2. 新建 Process Modeling Library 项目。
3. 画 Source → Delay → Sink 的单工位流水线，跑通一个工件从进入系统到离开。
4. 确认：AnyLogic 的 `Delay` 时间可以读自参数文件（见下方 `data/stations.json` 的格式）。

### Day 2–3：装配线动画建模

1. 画出 4 条主线：
   - 内饰线（6 站）：INT-01 ~ INT-06
   - 底盘线（7 站）：CHS-01 ~ CHS-07
   - 外饰线（5 站）：EXT-01 ~ EXT-05
   - 检测线（3 站）：INS-01 ~ INS-03
2. 用 `ResourcePool` 表示工位，`Source` 生成车身，`MoveTo` 转运，`Sink` 接收完成车辆。
3. 加工时间设成三角分布（从 `stations.json` 读，或手动设）。
4. 用 `Seize` + `Delay` + `Release` 的标准范式建模。

### Day 3–4：换型动画

1. 用 AnyLogic 的 `Statechart` 表示换型状态：正常加工 ↔ 换型中。
2. 前后车型相同时直接过，不同时触发 `Delay`（时间从 `data/changeover_matrix.json` 读）。
3. 换型时工位变黄，换型结束后恢复绿色。

### Day 4：PHM 可视化

1. 对拧紧机（CHS-06）画一个 Health Index 仪表盘：
   - 用 `Chart` + `Variable` 实时绘制 HI 曲线。
   - 正常 = 绿色，劣化 = 黄色，故障 = 红色。
2. 维修时显示 MTTR 倒计时。

### Day 5：S1 vs S3 对比演示

1. 做两个场景按钮：`Scenario S1` / `Scenario S3`。
2. 点击后跑完显示统计面板（可用度、非计划停机次数、WIP 曲线）。
3. **这是答辩时的 killer feature**——评委能直观看到 S3 比 S1 少停机。

### Day 6：与 Java 后端联调

1. 在 AnyLogic 里嵌入 Java 代码片段，调用 `SimulationEngine` 的结果做动画驱动。
2. 或者折中方案：AnyLogic 做动画，Java 做实验（两者独立跑，结论一致即可）。

---

## 队友 B 职责细目 —— 报告 + 数据可视化

**前提**：不需要会 Java，但要会 Excel / Python(matplotlib) / PPT。

### Day 1：报告框架（今天开始）

1. 按竞赛要求搭好报告结构（背景 → 方案 → 模型 → 实验 → 结论 → 创新点）。
2. 把 `CLAUDE.md` 和 `README.md` 里的项目描述翻译成「方案文档」风格。
3. 等你给的 CSV（Day 1 必须给）开始画第一张图。

### Day 2–3：数据可视化（与队长并行）

用 `results/experiment_baseline.csv` 和 `experiment_optimized.csv` 画：

1. **箱线图**：非计划停机次数、故障涟漪（证明 S3 显著降低）。
2. **直方图**：WIP 峰值、装配周期（对比两组分布）。
3. **折线图**：故障预警提前量随时间变化（如需时间序列，可从 Java 加一行导出）。
4. **灵敏度分析图**：Weibull η 在 [120, 150, 180, 210] 四个点上画改进率曲线。

### Day 3–4：验证逻辑撰写

1. 报告 4.5 节「验证逻辑」：
   - 配对 t 检验（代码层已配好，B 只需要描述「我们用了 200 次蒙特卡洛，p < 0.05」）。
   - 大数定律说明（200 次 ≥ 通常要求的 30 次）。
   - 95% 置信区间在表格里标出来。
2. 实验结论段落（队长会给一段现成文字）。

### Day 4–5：答辩 PPT

1. 10–15 页：问题 → 方案 → 模型 → 实验 → 结论 → 创新点。
2. A 的 AnyLogic 截图贴进去（装配线全景、PHM 仪表盘、S1/S3 对比）。
3. 队长的实验结论图贴进去。
4. 准备 3–5 分钟自述 + 2 分钟问答。

---

## 关键依赖与里程碑

```
Day 0（今天）
  ├── 队长：框架稳定 + 拉群 + 发分工 + 给 A/B 开 30 分钟同步会
  ├── A：装 AnyLogic，画 Hello World 装配线
  └── B：搭报告 Word，写方案概述

Day 1–2
  ├── 队长：跑通 200 次蒙特卡洛，生成 CSV，交给 B；整理实验结论
  ├── A：装配线动画 + 换型 + PHM 仪表盘
  └── B：数据可视化初稿（箱线图 + 直方图）

Day 3
  ├── 队长：灵敏度分析 + 联调 A 的问题
  ├── A：S1/S3 对比演示 + 截图
  └── B：报告主体写完 + PPT 初稿

Day 4
  ├── 队长：最终实验验证 + 代码整理
  ├── A：AnyLogic 模型调精致 usable
  └── B：报告终稿 + PPT 美化

Day 5（提交前）
  └── 三人一起过一遍，格式统一，打包提交
```

### 硬依赖（不能跳）

- **B 的图表依赖队长的 CSV**（Day 1 必须给）。
- **A 的截图依赖 AnyLogic 建模完成**（Day 2）。
- **最终整合依赖 A + B 都完成**（Day 4）。

### 软依赖（可以随时插空）

- A 的问题队长随时答（不需要等里程碑）。
- B 的措辞问题队长随时改。

---

## 每个人第一天该做什么

| 人 | 今天开始 |
|---|---|
| **队长（你）** | 把 CLAUDE.md + README.md + 本文件发到三人群。给 A 发 AnyLogic 下载链接，给 B 发第一版 CSV（已在本仓库 `results/` 目录下）。开 30 分钟同步会确认对齐。 |
| **队友 A** | 装 AnyLogic PLE，新建 Process Modeling 项目，画 Source → Delay → Sink 的流水线跑通。遇到问题随时在群里问。 |
| **队友 B** | 新建 Word 文档，按「背景 → 方案 → 模型 → 实验 → 结论 → 创新点」搭好标题层级。把 CLAUDE.md 里的项目描述翻译成方案概述。 |

---

## 附录：常用资源

- AnyLogic 下载：https://www.anylogic.com/downloads/
- AnyLogic 官方教程（Process Modeling Library）：https://anylogic.help/
- 本仓库：https://github.com/cooooooosdas/Anylogic
- 仿真参数目录：`data/`（改参数改这里，不要硬编码）
- 竞赛演示入口：`experiments/ComparisonExperiment.java`
