# 队长日常任务清单

> 队长专用。列明每日/每周必须完成的管控项与交付检查点。
> 建议打印贴在工位旁，每完成一项打 ✓。

---

## 一、职责总览

| 职责 | 核心动作 |
|------|----------|
| **进度管控** | 每日晨会跟踪两名队友的产出，对齐本清单 |
| **代码集成** | 管理 `main` 分支，确保代码始终可编译 |
| **接口对齐** | 确认队友 A 的 AnyLogic 参数已与 `data/*.json` 同步 |
| **实验排期** | 提前 2 天向指导老师确认实验设备/机房可预约 |
| **报告定稿** | 周日晚统一审阅当日提交物，汇总到 `docs/REPORT.md` |
| **对外沟通** | 遇到卡点第一时间拉群或找指导老师，不要拖到赛前 |

---

## 二、每日检查清单（比赛前 7 天）

### 第 N-7 天：功能冻结

- [ ] 确认 `src/main/java/assemblyline/` 所有类编译通过（`mvn compile` 零报错）
- [ ] 确认 `data/*.json` 无 `"source": "TODO"` 占位符
- [ ] 将本日完成的代码 `git commit` 并 `git push`
- [ ] 与队友 A 确认 AnyLogic 模型可读取 `data/stations.json`（无运行时异常）
- [ ] 与队友 B 确认 matplotlib 依赖已装好（Python ≥ 3.8，`pip install matplotlib numpy`）
- [ ] **记录风险项**：`docs/ISSUES.md` 当前遇到的卡点

### 第 N-6 天：仿真稳定性验证

- [ ] 运行 `java -cp target/classes:target/dependency/* experiments.StabilityExperiment`（1000 次蒙特卡洛）
- [ ] 核对输出文件 `results/sensitivity/stability_summary.csv` 出现
- [ ] 将 `stability_summary.csv` 粘贴到 `docs/REPORT.md` 的"仿真稳定性验证"小节
- [ ] 将本日产物 `git commit && git push`

### 第 N-5 天：对比实验与配对 t 检验

- [ ] 运行 `mvn exec:java -Dexec.mainClass=experiments.ComparisonExperiment`
- [ ] 核对控制台输出含"配对 t 检验"结论（p 值）
- [ ] 确保 `results/experiment_baseline.csv` 和 `experiment_optimized.csv` 有 200 行
- [ ] 运行 `python tools/sensitivity_analysis.py` 生成 `results/sensitivity/comparison_*.png`
- [ ] 将图表插入 `docs/REPORT.md` 对应小节

### 第 N-4 天：敏感性分析

- [ ] 运行 `mvn exec:java -Dexec.mainClass=experiments.SensitivityExperiment`
- [ ] 核对 `results/sensitivity/eta_{100,150,200,280}h/` 各目录下有 `run_summary.csv`
- [ ] 运行 `python tools/sensitivity_analysis.py --sensitivity` 生成敏感性曲线图
- [ ] 与队友 B 一起解读曲线（η 增大时可用度趋势是否符合直觉）
- [ ] **如有不合理的曲线趋势**，立刻标记 ISSUE，不要等赛前

### 第 N-3 天：图表完善 + AnyLogic 联调

- [ ] 与队友 A 一起跑一次 AnyLogic 单次演示（Verify 动画无卡顿、参数一致）
- [ ]  teammate A 提供 AnyLogic 截图/录屏，放入报告
- [ ]  检查 `docs/API.md` 里的字段映射是否仍正确（如有改动及时更新）
- [ ]  补充 `results/sensitivity/` 缺少的图（散点图、柱状图等）

### 第 N-2 天：报告终稿

- [ ] 打开 `docs/REPORT.md`，每小节核对"论点 → 数据来源"对应关系
- [ ] 数据来源只能是 `results/sensitivity/` 下的 CSV/PNG，不许脑补数字
- [ ] 报告字数 ≤ 竞赛要求上限（提前确认字数上限）
- [ ] 打印 1 份纸质版，发给队友和指导老师做最后一次校对

### 第 N-1 天：赛前彩排

- [ ] 团队 15 分钟演示彩排（按比赛 PPT 顺序过一遍）
- [ ] 确认队友 A 的 AnyLogic 演示机器能用（开箱即用）
- [ ] 确认实验数据备份（U 盘 + 云盘双份）
- [ ] 准备纸质版报告 1 份 + U 盘电子版

### 比赛当天

- [ ] 提前 1 小时到场
- [ ] 带清单：纸质报告、U 盘、AnyLogic 安装包（备用）
- [ ] 开场前与队友最后一次确认角色分工（谁主讲、谁演示、谁答疑）

---

## 三、队友交付检查表

### 队友 A（AnyLogic + 动画）

| 截止时间 | 交付物 | 验收标准 |
|-----------|--------|---------|
| 第 N-5 天 | `anylogic_model/` 目录下模型文件 | 导入 AnyLogic PLE 后可直接 Run，无报错 |
| 第 N-4 天 | 单次演示录屏（30 秒以上） | 包含换型、故障触发动画、仪表盘刷新 |
| 第 N-3 天 | 参数映射表截图 | 与 `docs/API.md` 3.x 节一致 |
| 比赛前一天 | AnyLogic 模型 + 数据读取逻辑 | 现场机器能跑通 |

### 队友 B（Python 可视化 + 敏感性分析）

| 截止时间 | 交付物 | 验收标准 |
|-----------|--------|---------|
| 第 N-5 天 | `tools/sensitivity_analysis.py` | `python tools/sensitivity_analysis.py --both` 无报错，输出 4 张 PNG |
| 第 N-4 天 | `results/sensitivity/comparison_*.png` | 分辨率 ≥ 150 DPI，中文标签无乱码 |
| 第 N-3 天 | 敏感性分析解读（口头） | 能解释为什么 η 增大导致可用度变化 |

---

## 四、每日晨会模板（5 分钟，站立形式）

```
队友 A 昨天完成了 [X] / 卡在 [Y]，今天计划 [Z]，需要队长协调 [W]
队友 B 昨天完成了 [X] / 卡在 [Y]，今天计划 [Z]，需要队长协调 [W]
队长 昨天完成了 [X] / 卡在 [Y]，今天计划 [Z]，风险项 [W]
共识：今天共同推进 [1-2 项]，不各自为战
```

**要求**：晨会必须有"共识行动项"，否则不算有效同步。

---

## 五、风险项登记（`docs/ISSUES.md`）

模板：

```
## ISSUE-001 | 状态: Open/Closed
日期：2026-07-15
报告人：队长
描述：[卡点描述]
影响：如果不解决，会导致 [具体后果]
决策：需要 [指导老师 / 队友 A / 队友 B] 配合
截止时间：2026-07-18
实际解决：
```

---

## 六、Git 提交规范

```bash
# 普通提交（队长或队友均可）
git add <具体文件>
git commit -m "feat: 描述"

# 禁止行为（为了审计）
# ❌ git add -A          # 可能误提交敏感文件
# ❌ git commit -m "a"   # 提交信息过于模糊
# ❌ git push --force    # 强制推送会覆盖队友的提交
```

---

## 七、关键联系人

| 角色 | 姓名 | 联系方式 | 备注 |
|------|------|----------|------|
| 队长 | 胡希 | — | 本文档维护人 |
| 队友 A（AnyLogic）| — | — | 负责模型与动画 |
| 队友 B（可视化） | — | — | 负责 Python 工具 |
| 指导老师 | — | — | 遇到技术/资源卡点时第一时间联系 |

---

## 八、参赛当周详细排期

### Day -14 ~ Day -8：核心功能开发

- 队长：完善 SimulationEngine + PHM 逻辑，确保 `mvn compile` 始终通过
- 队友 A：阅读 `docs/API.md`，开始 AnyLogic 模型草图
- 队友 B：跑通 `ComparisonExperiment`，记录基线数据

### Day -7 ~ Day -4：验证与可视化

- 队长：1000 次稳定性测试 + 敏感性分析实验
- 队友 A：AnyLogic 动画完整 + 参数映射验证
- 队友 B：`tools/sensitivity_analysis.py` 所有图表生成

### Day -3 ~ Day -1：报告定稿

- 队长：整合所有图表、数据表，撰写报告终稿
- 队友 A：提供录屏、截图、API 说明
- 队友 B：数据解读文字、图表 caption

### Day 0：比赛日

- 按本清单"比赛当天"执行
