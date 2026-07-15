# 算法逻辑文档

> 面向队友与后续迭代。描述本仿真框架的核心算法、数据流与关键设计决策。
> 版本：v1.1 — 2026-07-16（新增 DataSource 抽象层、配对 t 检验，修复 SequenceOptimizer 2 个 bug，新增 3 个审计 bug 记录）

---

## 目录

1. [整体数据流](#1-整体数据流)
2. [离散事件仿真引擎（SimulationEngine）](#2-离散事件仿真引擎simulationengine)
3. [设备三状态 PHM 模型（ThreeStateModel）](#3-设备三状态-phm-模型threestatemodel)
4. [三种维修策略（S1/S2/S3）](#4-三种维修策略s1s2s3)
5. [装配序列优化（SequenceOptimizer）](#5-装配序列优化sequenceoptimizer)
6. [蒙特卡洛统计（MonteCarloRunner + StatisticsCollector）](#6-蒙特卡洛统计montecarlorunner--statisticscollector)
7. [敏感性分析（SensitivityExperiment）](#7-敏感性分析sensitivityexperiment)
8. [已知限制与真实数据对接方案](#8-已知限制与真实数据对接方案)
9. [Bug 修复日志](#9-bug-修复日志)

---

## 1. 整体数据流

```
data/*.json
    │
    ▼
ConfigLoader.loadXxx() ──► 领域对象（AssemblyLine, VehicleModel, ...）
    │
    ▼
MonteCarloRunner.runRaw()   对每次蒙特卡洛运行：
    ├─ 构造 RandomGenerator(seed_i)
    ├─ 构造 EquipmentHealth + ThreeStateModel（新实例，干净状态）
    ├─ 构造 SimulationEngine + setEquipments()
    └─ engine.run() ──► RunResult
            │
            ▼
    StatisticsCollector.summarize() ──► Summary（均值/标准差/CI）
            │
            ▼
    exportRunCsv() ──► results/*.csv
```

**关键约束**：每次蒙特卡洛运行必须从干净状态开始（新 RandomGenerator、新 EquipmentHealth、新 ThreeStateModel）。任何跨 run 的状态残留都会破坏蒙特卡洛的独立性假设。

---

## 2. 离散事件仿真引擎（SimulationEngine）

### 2.1 事件类型与优先级队列

采用**下一事件时间推进法（Next-Event Time Advance）**：

| 事件类型 | 触发时机 | 处理要点 |
|----------|----------|----------|
| ARRIVAL | 新车身 Poisson 到达 | 尝试放入首站，或排队 |
| PROC_COMPLETE | 工位加工完成 | 流转到下一站，触发换型判断 |
| CHANGEOVER_END | 换型完成 | 恢复加工 |
| FAILURE | 设备故障 | 队列 MTTR 维修事件 |
| MAINT_END | 维修完成 | 恢复设备，计入计划维护 |

核心循环（`run()` 方法）：

```java
init();                              // 重置所有运行时状态
scheduleArrival(firstArrival);       // 首个到达事件
while (!queue.isEmpty()) {
    SimEvent ev = queue.poll();       // 取最小时间事件
    if (ev.time > simDuration) break; // 超出仿真窗口则停
    currentTime = ev.time;
    dispatch(ev, result);            // 分发处理
}
computeStats(result);                // 汇总统计
```

### 2.2 确定性保证

- **SimEvent 序列计数器**：同一时刻的事件按入队顺序处理（FIFO tie-breaking），避免 JVM PriorityQueue 非确定性行为。
- **RandomGenerator 种子推进**：每次 run 用 `seed → rng.nextSeed() → 下一 run 的 seed`，保证不同 run 的随机流独立且可复现。
- **Station 状态重置**：`init()` 调用 `station.resetRuntimeState()`，避免跨 run 污染。

### 2.3 工位状态机

```
idle ──startProc()──► processing ──PROC_COMPLETE──► idle
                            │
                            ├─ 下一站忙 → 放入下一站队列
                            └─ 需要换型 → changeover ──CHANGEOVER_END──► idle
```

`StationState.isFreeAt(t)` 判定条件：`!inChangeover && availableFrom <= t && currentPiece == null`。

### 2.4 换型判断

在 `onProcComplete` 中，本站加工完成后：
1. 取队列下一工件
2. 若本站是换型点 (`changeoverPoint=true`) 且上一工件模型 ≠ 下一工件模型
3. 查 `changeoverMatrix.changeoverTime(prevModel, nextModel)` 得到换型时间
4. 若换型时间 > 0，触发 `triggerChangeover`（站内延迟，其他站继续处理各自工件）

### 2.5 设备健康推进

每次 `startProc()` 调用时，对本站所属设备推进 `procMin/60` 小时：

```java
advanceEquipFor(station, procMin / 60.0, result);
```

推进顺序：
1. `eq.incrementHours(hours)` — 累计运行时间 +1
2. `m.advance(hours)` — 三状态转移，可能触发故障
3. 若故障：计入 unplanned downtime，队列 MAINT_END
4. **对 DEGRADED/NORMAL 设备调用 `m.predictRUL()` 更新 remainingLife**（S3 前提）
5. 调用 `maintenanceStrategy.shouldMaintain()` 决定是否计划维护
6. 若触发维护：执行维护，记录 plannedMaintMinutes

---

## 3. 设备三状态 PHM 模型（ThreeStateModel）

### 3.1 状态定义

```
NORMAL (HI: 70-100)
  │  Weibull 随机寿命耗尽 或 累计 HI 降至 ≤70
  ▼
DEGRADED (HI: 20-69)
  │  劣化计时器耗尽（指数分布，均值 50h）
  ▼
FAILED (HI: 0-19)
  │  MTTR 维修
  ▼
NORMAL (重置为 HI=100)
```

### 3.2 状态转移方程

**NORMAL → DEGRADED**：

采样 Weibull 随机寿命 `T_W ~ Weibull(β, η)`：
```
P(T_W ≤ t) = 1 - exp(-(t/η)^β)
```

在 `advance(hours)` 中：
```
cumulativeHours += hours
if cumulativeHours >= nextFailureTime:
    HI -= (100 - 60)      // 降至预警区间 HI=60
    state = DEGRADED
    nextFailureTime = -1
    degradedRemaining ~ Exp(λ=1/50h)   // 采样劣化→故障剩余寿命
```

**DEGRADED → FAILED**：
```
degradedRemaining -= hours
if degradedRemaining <= 0:
    HI -= 100            // 强制降至 0
    state = FAILED
    return true           // 触发故障
```

**FAILED → NORMAL**：
```
performMaintenance():
    HI = 100
    state = NORMAL
    cumulativeHours 保留（反映设备真实年龄）
    timeSinceMaintenance = 0
    remainingLife = -1
```

### 3.3 RUL 预测

```java
public double predictRUL() {
    if (state == DEGRADED && degradedRemaining > 0) {
        return degradedRemaining;  // 直接返回剩余劣化计时器
    }
    // NORMAL 状态：线性外推
    double rate = normalDegradationRate;  // 0.018/h
    return HI / rate;                     // 保守估计
}
```

**注意**：S3 预测性维修依赖此方法更新 `EquipmentHealth.remainingLife`，必须在 `shouldMaintain()` 之前调用。

### 3.4 参数来源

| 参数 | 当前值（模拟） | 来源 | 真实数据替换方式 |
|------|----------------|------|-----------------|
| Weibull β | 2.5 / 2.0 / 1.8 | 一汽卡车厂数据说明书 | 直接替换 equipment_params.json 的 `weibull.beta` |
| Weibull η | 150 / 200 / 280h | 经调整适配 720h 仿真窗口 | 同上 |
| DEGRADED→FAILED 均值 | 50h | 经验估计 | 同上 |
| 劣化速率 | 0.018 / 0.014 / 0.010 /h | 假设线性劣化 | 同上 |

---

## 4. 三种维修策略（S1/S2/S3）

### 4.1 S1 计划性维修（ScheduledMaintenance）

```java
if (eq.getTimeSinceMaintenance() >= intervalHours) {
    triggerMaintenance(eq);
}
```

- 固定间隔（如 48h），到点即保
- 优点：实现简单，无状态依赖
- 缺点：可能过度维修（设备尚好就保）或维修不足（故障发生在两次计划之间）

### 4.2 S2 状态维修（ConditionBasedMaintenance）

```java
if (eq.getHealthIndex() <= threshold) {
    triggerMaintenance(eq);
}
```

- 基于健康指数阈值
- 需 HI 传感器数据支持
- 阈值调优依赖历史数据

### 4.3 S3 预测性维修（PredictiveMaintenance）

```java
m.predictRUL();  // 必须在 shouldMaintain() 前调用，更新 remainingLife
if (eq.getRemainingLife() >= 0 && eq.getRemainingLife() < rulWindowHours) {
    triggerMaintenance(eq);
}
```

- 基于 RUL 预测，在故障前窗口期维修
- 前提：`predictRUL()` 在策略检查前被调用（见 advanceEquipFor 的修正）
- 效果：消除非计划停机，降低维护成本（见实验结果）

### 4.4 策略决策树

```
每次加工推进（advanceEquipFor）：
    ├─ 设备刚进入 FAILED？
    │   └─ 是 → 队列 MAINT_END（非计划停机，计入 ripple）
    │
    ├─ 预测 RUL（更新 remainingLife）
    │
    └─ 策略检查 shouldMaintain():
        ├─ S1：timeSinceMaintenance >= interval
        ├─ S2：healthIndex <= threshold
        └─ S3：remainingLife >= 0 && remainingLife < window
            └─ 是 → performMaintenance()（计划维护）
```

---

## 5. 装配序列优化（SequenceOptimizer）

### 5.1 问题定义

给定车型库 `M = {m_1, ..., m_k}`（按年产量比例扩充），寻找排列 `π = (π_1, ..., π_n)` 最小化：

```
f(π) = Σ_i changeoverTime(π_i, π_{i+1}) + α * utilization_penalty(π)
```

其中 `utilization_penalty` 通过瓶颈利用率估计（避免硬编码 0.85）。

### 5.2 爬山算法

```java
current = 启发式贪心初始解
best = current
for iter in 1..maxIterations:
    neighbor = swapMove(current)          // 随机交换两辆
    if score(neighbor) < score(current):  // 更优
        current = neighbor
        if score(neighbor) < score(best):
            best = neighbor
    else if 连续 50 次未改进且 random() < 0.05:
        current = neighbor  // 随机跳逸，逃离局部最优
    if 连续 200 次未改进:
        current = 重新生成启发式解  // 重启
```

**邻域算子**：随机交换两辆（swap move）。每次迭代 O(1) 邻域生成，O(n) 评分（需遍历序列算换型代价）。

**重启条件**：连续 200 次未改进时，丢弃当前解，重新用启发式贪心生成新初始解。

### 5.3 初始解生成（SequenceGenerator）

三种策略：

| 策略 | 逻辑 | 适用场景 |
|------|------|----------|
| RANDOM | 完全随机打乱 | 基线对比 |
| HEURISTIC | 最近邻贪心（每次选换型代价最小的下一辆） | 快速可行解 |
| PROPORTIONAL | 按年产量比例轮询穿插 | 均衡化排产 |

---

## 6. 蒙特卡洛统计（MonteCarloRunner + StatisticsCollector）

### 6.1 独立运行与配对设计

```java
long seed = baseSeed;
for i in 0..numRuns-1:
    rng = new RandomGenerator(seed)
    seed = rng.nextSeed()  // 推进种子，供下次使用
    // ... 构造并运行一次仿真
```

基准组与优化组使用**相同的 baseSeed**，保证每次 run 的随机输入（Weibull 采样、加工时间、MTTR）完全相同。差异仅来自维修策略，实现**配对比较**。

### 6.2 统计汇总

对 n 次运行的指标值 `x_1, ..., x_n`：

```
均值  x̄ = (1/n) Σ x_i
标准差 s = sqrt( (1/(n-1)) Σ (x_i - x̄)^2 )
95% CI = x̄ ± 1.96 * s / sqrt(n)   (n≥30 时正态近似)
```

### 6.3 配对 t 检验

对配对样本 `d_i = baseline_i - optimized_i`：

```
t = d̄ / (s_d / sqrt(n))
df = n - 1
p = 2 * (1 - Φ(|t|))  双侧
```

其中 `Φ` 为标准正态 CDF（Abramowitz & Stegun 近似）。若 `p < α`（默认 0.05），认为两组差异显著。

---

## 7. 敏感性分析（SensitivityExperiment）

### 7.1 方法

变化关键参数 Weibull η，观察对产线可用度的影响：

```
η ∈ {100, 150, 200, 280} 小时  （覆盖当前值 ±30%）
每 η 值：50 次蒙特卡洛
总计：200 次仿真
```

### 7.2 实现机制

通过 `EquipmentHealth.setEtaOverride(η)` 覆盖设备的 Weibull 特征寿命，不修改 JSON：

```java
for each run:
    eqs = ConfigLoader.loadEquipmentHealths(runRng)
    for each eq in eqs:
        eq.setEtaOverride(testEta)  // 覆盖
    engine.setEquipments(eqs, models)
```

`getWeibullEta()` 优先返回 `etaOverride`（若 > 0），否则返回 JSON 原始值。

### 7.3 预期结果

- η 越小 → 故障越频繁 → 非计划停机增加（若维修策略不及时）
- η 越小 → RUL 更短 → S3 预测提前量更大（可在故障前更早维修）
- 产线可用度随 η 变化的曲线反映策略鲁棒性

---

## 8. 已知限制与真实数据对接方案

### 8.1 当前数据局限性

| 维度 | 当前状态 | 对结果的影响 |
|------|----------|-------------|
| 设备故障率 | 模拟 Weibull（η=150/200/280h） | 比真实数据更频繁，需校准 |
| 换型时间 | 假设正态分布 | 真实换型时间离散、多峰 |
| 加工时间 | 三角分布（min/mode/max） | 真实加工时间可能有偏态 |
| 到达间隔 | Poisson（λ=40min） | 实际到达受订单约束，非纯随机 |
| 维护成本 | 固定单价（2667 元/停机分钟，150 元/维护小时） | 需替换为真实成本数据 |
| 年产量 | 模拟比例 | 需替换为真实 SOR（销售订单记录） |

### 8.2 真实数据对接路径

开学后对接一汽企业数据时，按以下优先级替换：

**Phase 1：核心参数校准（1-2 周）**
- 获取设备故障历史 → 拟合真实 Weibull 参数 → 更新 `equipment_params.json`
- 获取换型历史记录 → 更新 `changeover_matrix.json` 的 `pairwiseTimes`

**Phase 2：产线参数校准（2-3 周）**
- 获取工位节拍数据 → 更新 `stations.json` 的 `processingTime`
- 获取真实 SOR → 更新 `vehicle_models.json` 的 `annualVolume`

**Phase 3：验证**
- 对比仿真输出（产出率、WIP、换型次数）与真实 MES 数据
- 调整分布假设（如加工时间改为经验分布而非三角分布）

**Phase 4：替代数据源（若企业数据无法获取）**
- 公开数据集：Kaggle "Automotive Manufacturing" 相关数据集
- 文献基准：SAE 论文中的装配线参数
- 在 `docs/API.md` 中标注数据来源，满足竞赛可追溯性要求

### 8.3 数据层抽象设计

```java
// 接口
public interface DataSource {
    AssemblyLine loadAssemblyLine();
    List<VehicleModel> loadVehicleModels();
    ChangeoverMatrix loadChangeoverMatrix(RandomGenerator rng);
    Map<String, EquipmentHealth> loadEquipmentHealths(RandomGenerator rng);
    Map<String, ThreeStateModel> loadThreeStateModels(...);
    ExperimentConfig loadExperimentConfig();
}

// 当前实现（模拟数据，开箱即用）
DataSource ds = JsonDataSource.INSTANCE;

// 真实数据对接实现（Phase 1）
DataSource ds = new EnterpriseDataSource(
        "jdbc:mysql://faw-prod-db:3306/mes", credentials);
```

现有三个实现类：

| 类 | 职责 |
|----|------|
| `DataSource`（interface） | 定义数据加载契约 |
| `JsonDataSource`（enum singleton） | 默认实现，委托 ConfigLoader 静态方法，零行为变更 |
| `EnterpriseDataSource`（stub） | 桩实现，所有方法抛 `UnsupportedOperationException`，附 TODO 清单 |

**切换方式**：在 MonteCarloRunner 或实验入口处传入不同 DataSource 实例，仿真核心零改动。

**遗留**：
- SequenceOptimizer 的 `defaultBottleneckUtilization` 仍为启发式估计（原硬编码 0.85），需根据实际仿真标定
- ChangeoverMatrix 的 color pairwise 表在 JSON 中缺失，真实数据对接时补齐
- EnterpriseDataSource 的 JDBC 凭证应通过环境变量 / 密钥管理服务注入，禁止硬编码

---

## 9. Bug 修复日志

| 日期 | Bug | 位置 | 影响 | 修复 |
|------|-----|------|------|------|
| 2026-07-15 | plannedMaintMinutes 被硬编码覆盖 | SimulationEngine.java:464 | 计划维护时间恒为 `720 * count`，与真实累计值脱节 | 删除覆盖行，保留 advanceEquipFor 中逐次累加的值 |
| 2026-07-15 | predictionHits 重复计数 | SimulationEngine.java:424+431 | 每次成功预测被计两次，RUL 提前量被高估 | 将第二处 if 改为 else if |
| 2026-07-15 | rippleStart > 0 漏判 t=0 的故障涟漪 | SimulationEngine.java:347 | 仿真初期（t∈[0, warmUp]）发生的故障涟漪不被计入 | 改为 `>= 0` |
| 2026-07-15 | S3 预测性维修失效 | SimulationEngine.java:413-416 | predictRUL() 未在 shouldMaintain() 前调用，remainingLife 恒为 -1，S3 从不触发 | 在策略检查前对 DEGRADED/NORMAL 设备调用 predictRUL() |
| 2026-07-15 | bottleneckUtilization 硬编码 0.85 | SequenceOptimizer.java:52,61,87 | 优化目标函数的利用率惩罚项为静态估计，随实际产线状态变化时偏差 | 改为可配置字段 `defaultBottleneckUtilization`（默认 0.85，保持向后兼容） |
| 2026-07-15 | swapMove 随机性名存实亡 | SequenceOptimizer.java:108-110 | `rngLike()` 每次新建 `new Random(42)`，固定种子导致每次迭代尝试完全相同的交换对，邻域搜索退化为确定性遍历 | 改为构造时注入的单例 `java.util.Random`，种子可配置（默认 42 保持可复现） |
| 2026-07-15 | ChangeoverMatrix color pairwise 缺失 | ChangeoverMatrix.java:89-94 | 换色换型时间忽略 pairwise 表，一律返回 colorMean，精度不足 | 数据层问题（JSON 未包含 color pairwise）。代码已预留扩展位置，真实数据对接后补齐 |

---

## 附录 A：文件职责索引

| 文件 | 职责 |
|------|------|
| `src/.../simulation/SimulationEngine.java` | 单次蒙特卡洛事件循环 |
| `src/.../simulation/MonteCarloRunner.java` | 批量运行 + 设备参数修改 hook |
| `src/.../simulation/StatisticsCollector.java` | 统计汇总 + 配对 t 检验 + CSV 导出 |
| `src/.../phm/ThreeStateModel.java` | 设备三状态转移 + RUL 预测 |
| `src/.../phm/EquipmentHealth.java` | 设备健康快照 + η 覆盖（用于敏感性分析）|
| `src/.../phm/PredictiveMaintenance.java` | S3 策略实现 |
| `src/.../optimization/SequenceOptimizer.java` | 装配序列爬山优化 |
| `src/.../data/ConfigLoader.java` | JSON 参数加载 |
| `experiments/ComparisonExperiment.java` | 基准 vs 优化对比实验 |
| `experiments/StabilityExperiment.java` | 1000x 稳定性压测 |
| `experiments/SensitivityExperiment.java` | η 敏感性分析 |
| `tools/sensitivity_analysis.py` | 可视化（matplotlib）|
| `data/*.json` | 仿真参数（模拟数据）|

## 附录 B：运行命令速查

```bash
# 编译
javac -cp <gson.jar> -d build/classes -sourcepath src/main/java src/main/java/**/*.java

# 对比实验（200 次蒙特卡洛，基准 vs 优化）
java -cp build/classes:<gson.jar> experiments.ComparisonExperiment

# 稳定性压测（1000 次）
java -cp build/classes:<gson.jar> experiments.StabilityExperiment

# 敏感性分析（4 η × 50 runs）
java -cp build/classes:<gson.jar> experiments.SensitivityExperiment

# 可视化
python tools/sensitivity_analysis.py --both
```
