#!/usr/bin/env python3
"""
工具：仿真结果可视化与敏感性分析。

用法：
  python tools/sensitivity_analysis.py                    # 对比实验图
  python tools/sensitivity_analysis.py --sensitivity       # 敏感性分析图
  python tools/sensitivity_analysis.py --both              # 全部输出
  python tools/sensitivity_analysis.py --stability results/experiment_baseline.csv

说明：
  - 仅依赖 matplotlib + numpy（Python 标准科学计算栈，竞赛机器通常预装）
  - 输入：results/ 下的 experiment_*.csv
  - 输出：results/sensitivity/*.png
"""

import argparse
import csv
import os
import sys
from pathlib import Path

# ── 常量 ─────────────────────────────────────────────────
RESULTS_DIR = Path("results")
OUTPUT_DIR = RESULTS_DIR / "sensitivity"
OUTPUT_DIR.mkdir(exist_ok=True)

# 各指标的中文标签与优化方向
METRICS = {
    "lineAvailability":          ("产线可用度",      "%",   True),
    "unplannedDowntimeCount":    ("非计划停机次数",  "次",  False),
    "unplannedDowntimeMinutes":  ("非计划停机分钟",  "min", False),
    "avgCycleMinutes":           ("平均装配周期",    "min", False),
    "changeoverEventCount":      ("换型事件次数",    "次",  False),
    "wipPeak":                   ("WIP 峰值",        "台",  False),
    "rippleEffectMinutes":       ("故障涟漪总时长",  "min", False),
    "failurePredictionLeadTime": ("故障预警提前量",  "h",   True),
    "maintenanceCost":           ("维护总成本",      "元",  False),
    "plannedMaintMinutes":       ("计划维护总分钟",  "min", False),
    "completedCount":            ("完成装配台数",    "台",  True),
}

# 敏感性分析预设的 η 值（小时），覆盖原值 ±40%
ETA_VALUES = [100, 150, 200, 280]  # 对应 equipment_params.json 的三种设备
ETA_COLORS = ["#e74c3c", "#f39c12", "#3498db", "#2ecc71"]

# ── CSV 读取 ─────────────────────────────────────────────
def read_csv(path: str) -> list[dict]:
    """读取 CSV，返回字典列表。"""
    rows = []
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            parsed = {}
            for k, v in row.items():
                try:
                    parsed[k] = float(v)
                except ValueError:
                    parsed[k] = v
            rows.append(parsed)
    return rows


def summarize(rows: list[dict]) -> dict[str, float]:
    """计算均值。"""
    n = len(rows)
    if n == 0:
        return {}
    sums: dict[str, float] = {}
    for r in rows:
        for k, v in r.items():
            if isinstance(v, (int, float)):
                sums[k] = sums.get(k, 0.0) + v
    return {k: v / n for k, v in sums.items()}


def confidence_interval(rows: list[dict], key: str) -> tuple[float, float]:
    """均值 ± 1.96 * std (95% CI)。"""
    vals = [r[key] for r in rows if key in r]
    n = len(vals)
    if n < 2:
        return (0.0, 0.0)
    mean = sum(vals) / n
    var = sum((x - mean) ** 2 for x in vals) / (n - 1)
    sd = var ** 0.5
    half = 1.96 * sd / (n ** 0.5)
    return (mean - half, mean + half)


# ── 绘图函数 ─────────────────────────────────────────────
try:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import numpy as np
    HAS_MATPLOTLIB = True
except ImportError:
    HAS_MATPLOTLIB = False
    print("警告：matplotlib 未安装，跳过绘图。pip install matplotlib")


def set_style():
    """中文字体兼容。"""
    if not HAS_MATPLOTLIB:
        return
    plt.rcParams["font.sans-serif"] = ["SimHei", "Microsoft YaHei", "PingFang SC", "Arial"]
    plt.rcParams["axes.unicode_minus"] = False
    plt.rcParams["figure.dpi"] = 150


def plot_comparison_boxplot(baseline_rows: list[dict], optimized_rows: list[dict]):
    """箱线图：对比基线组与优化组的关键指标分布。"""
    if not HAS_MATPLOTLIB:
        return
    set_style()

    keys = [
        "lineAvailability", "unplannedDowntimeCount",
        "maintenanceCost", "avgCycleMinutes",
    ]
    labels = [METRICS[k][0] for k in keys]
    units  = [METRICS[k][1] for k in keys]

    fig, axes = plt.subplots(2, 2, figsize=(12, 8))
    axes = axes.flatten()

    for idx, (key, label, unit) in enumerate(zip(keys, labels, units)):
        ax = axes[idx]
        b_vals = [r[key] for r in baseline_rows]
        o_vals = [r[key] for r in optimized_rows]

        bp = ax.boxplot([b_vals, o_vals], labels=["基准组 (S1)", "优化组 (S3)"],
                        patch_artist=True)
        bp["boxes"][0].set_facecolor("#3498db")
        bp["boxes"][1].set_facecolor("#2ecc71")

        means = [sum(b_vals) / len(b_vals), sum(o_vals) / len(o_vals)]
        ax.scatter([1, 2], means, color="red", marker="D", zorder=5, label="均值")
        ax.set_title(f"{label}（{unit}）")
        ax.set_ylabel(unit)
        ax.legend()
        ax.grid(axis="y", linestyle="--", alpha=0.4)

    plt.suptitle("基准组 vs 优化组 — 关键指标对比（箱线图）", fontsize=14)
    plt.tight_layout()
    out = OUTPUT_DIR / "comparison_boxplot.png"
    plt.savefig(out)
    plt.close()
    print(f"  [箱线图] 已写入 {out}")


def plot_availability_histogram(baseline_rows: list[dict], optimized_rows: list[dict]):
    """可用度分布直方图（重叠）。"""
    if not HAS_MATPLOTLIB:
        return
    set_style()

    b_vals = [r["lineAvailability"] for r in baseline_rows]
    o_vals = [r["lineAvailability"] for r in optimized_rows]

    b_mean = sum(b_vals) / len(b_vals)
    o_mean = sum(o_vals) / len(o_vals)

    plt.figure(figsize=(10, 5))
    plt.hist(b_vals, bins=30, alpha=0.6, label=f"基准组 (μ={b_mean:.4f})",
             color="#3498db", edgecolor="white")
    plt.hist(o_vals, bins=30, alpha=0.6, label=f"优化组 (μ={o_mean:.4f})",
             color="#2ecc71", edgecolor="white")
    plt.axvline(b_mean, color="#2980b9", linestyle="--", linewidth=1.5)
    plt.axvline(o_mean, color="#27ae60", linestyle="--", linewidth=1.5)
    plt.xlabel("产线可用度")
    plt.ylabel("频次")
    plt.title("产线可用度分布（蒙特卡洛 200 次）")
    plt.legend()
    plt.grid(axis="y", linestyle="--", alpha=0.4)
    plt.tight_layout()
    out = OUTPUT_DIR / "availability_histogram.png"
    plt.savefig(out)
    plt.close()
    print(f"  [直方图] 已写入 {out}")


def plot_bar_comparison(baseline_rows: list[dict], optimized_rows: list[dict]):
    """柱状图：均值 + 95% CI。"""
    if not HAS_MATPLOTLIB:
        return
    set_style()

    keys = [
        "lineAvailability", "unplannedDowntimeCount",
        "maintenanceCost", "rippleEffectMinutes",
        "failurePredictionLeadTime",
    ]
    labels = [METRICS[k][0] for k in keys]
    units  = [METRICS[k][1] for k in keys]
    is_higher_better = [METRICS[k][2] for k in keys]

    b_means, b_cis = [], []
    o_means, o_cis = [], []
    for key in keys:
        bm = sum(r[key] for r in baseline_rows) / len(baseline_rows)
        om = sum(r[key] for r in optimized_rows) / len(optimized_rows)
        b_means.append(bm)
        o_means.append(om)
        b_ci = confidence_interval(baseline_rows, key)
        o_ci = confidence_interval(optimized_rows, key)
        b_cis.append([bm - b_ci[0], b_ci[1] - bm])
        o_cis.append([om - o_ci[0], o_ci[1] - om])

    x = np.arange(len(keys))
    width = 0.35

    fig, ax = plt.subplots(figsize=(12, 5))
    b_err = np.array(b_cis).T
    o_err = np.array(o_cis).T
    bars1 = ax.bar(x - width / 2, b_means, width, yerr=b_err,
                   label="基准组 (S1)", color="#3498db", capsize=3, alpha=0.85)
    bars2 = ax.bar(x + width / 2, o_means, width, yerr=o_err,
                   label="优化组 (S3)", color="#2ecc71", capsize=3, alpha=0.85)

    ax.set_xticks(x)
    ax.set_xticklabels([f"{l}\n({u})" for l, u in zip(labels, units)], fontsize=9)
    ax.set_ylabel("指标值（95% CI）")
    ax.set_title("关键指标对比：基准组 vs 优化组")
    ax.legend()
    ax.grid(axis="y", linestyle="--", alpha=0.4)

    # 标注改进率
    for i, (bm, om, higher) in enumerate(zip(b_means, o_means, is_higher_better)):
        if bm == 0:
            continue
        rate = (bm - om) / bm * 100 if higher else (om - bm) / bm * 100
        color = "#27ae60" if rate > 0 else "#e74c3c"
        ax.text(i, max(bm, om) * 1.02, f"{rate:+.1f}%", ha="center", fontsize=8, color=color)

    plt.tight_layout()
    out = OUTPUT_DIR / "comparison_bars.png"
    plt.savefig(out)
    plt.close()
    print(f"  [柱状图] 已写入 {out}")


def plot_scatter_ripple_vs_cost(baseline_rows: list[dict], optimized_rows: list[dict]):
    """散点图：故障涟漪时长 vs 维护成本。"""
    if not HAS_MATPLOTLIB:
        return
    set_style()

    b_ripple = [r["rippleEffectMinutes"] for r in baseline_rows]
    b_cost   = [r["maintenanceCost"]    for r in baseline_rows]
    o_ripple = [r["rippleEffectMinutes"] for r in optimized_rows]
    o_cost   = [r["maintenanceCost"]    for r in optimized_rows]

    plt.figure(figsize=(8, 6))
    plt.scatter(b_ripple, b_cost, alpha=0.5, label="基准组 (S1)", color="#3498db", s=30)
    plt.scatter(o_ripple, o_cost, alpha=0.5, label="优化组 (S3)", color="#2ecc71", s=30)
    plt.xlabel("故障涟漪总时长（min）")
    plt.ylabel("维护总成本（元）")
    plt.title("故障涟漪 vs 维护成本（蒙特卡洛散点）")
    plt.legend()
    plt.grid(linestyle="--", alpha=0.4)
    plt.tight_layout()
    out = OUTPUT_DIR / "scatter_ripple_vs_cost.png"
    plt.savefig(out)
    plt.close()
    print(f"  [散点图] 已写入 {out}")


# ── 敏感性分析图 ─────────────────────────────────────────
def plot_sensitivity_curve():
    """
    读取敏感性分析汇总 CSV（sensitivity_summary.csv），画 η 灵敏度曲线。
    X 轴：Weibull η（小时），Y 轴：产线可用度均值。
    """
    if not HAS_MATPLOTLIB:
        print("  [敏感性曲线] matplotlib 不可用，跳过。")
        return

    set_style()

    summary_csv = OUTPUT_DIR / "sensitivity_summary.csv"
    if not summary_csv.exists():
        print(f"  [敏感性曲线] 汇总文件不存在：{summary_csv}")
        return

    rows = read_csv(str(summary_csv))
    if not rows:
        print("  [敏感性曲线] 汇总文件为空。")
        return

    etas = [r.get("eta_h", 0) for r in rows]
    avail_means = [r.get("lineAvailability_mean", 0) for r in rows]
    avail_ci_low = [r.get("lineAvailability_ci95_low", 0) for r in rows]
    avail_ci_high = [r.get("lineAvailability_ci95_high", 0) for r in rows]
    lead_means = [r.get("predictionLeadTimeHours_mean", 0) for r in rows]

    fig, ax1 = plt.subplots(figsize=(9, 5))

    color1 = "#3498db"
    ax1.plot(etas, avail_means, marker="o", color=color1, linewidth=2,
             label="产线可用度")
    ax1.fill_between(etas, avail_ci_low, avail_ci_high,
                     color=color1, alpha=0.15, label="95% CI")
    ax1.set_xlabel("Weibull η（特征寿命，小时）")
    ax1.set_ylabel("产线可用度", color=color1)
    ax1.tick_params(axis="y", labelcolor=color1)
    ax1.set_ylim([0.9, 1.01])

    ax2 = ax1.twinx()
    color2 = "#e74c3c"
    ax2.plot(etas, lead_means, marker="s", color=color2, linewidth=2,
             linestyle="--", label="故障预警提前量 (h)")
    ax2.set_ylabel("故障预警提前量（小时）", color=color2)
    ax2.tick_params(axis="y", labelcolor=color2)

    fig.legend(loc="lower right", bbox_to_anchor=(0.95, 0.05))
    plt.title("敏感性分析：Weibull η 对产线可用度与故障预警提前量的影响（S3 预测性维修）")
    plt.tight_layout()
    out = OUTPUT_DIR / "sensitivity_eta.png"
    plt.savefig(out)
    plt.close()
    print(f"  [敏感性曲线] 已写入 {out}")


# ── 主函数 ───────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(description="仿真结果可视化与敏感性分析")
    parser.add_argument("--both",        action="store_true", help="输出对比图 + 敏感性曲线")
    parser.add_argument("--sensitivity", action="store_true", help="只输出敏感性分析图")
    parser.add_argument("--stability",   type=str, default=None,
                        help="压测 CSV 路径（如 results/experiment_stability.csv）")
    parser.add_argument("--baseline",    type=str, default=str(RESULTS_DIR / "experiment_baseline.csv"),
                        help="基准组 CSV 路径")
    parser.add_argument("--optimized",   type=str, default=str(RESULTS_DIR / "experiment_optimized.csv"),
                        help="优化组 CSV 路径")
    args = parser.parse_args()

    do_comparison = args.both or (not args.sensitivity)
    do_sensitivity = args.both or args.sensitivity

    print("══════════════════════════════════════════════")
    print(" 仿真结果可视化工具")
    print("══════════════════════════════════════════════")

    if not HAS_MATPLOTLIB:
        print("matplotlib 不可用，请安装：pip install matplotlib")
        return

    # ── 对比实验图 ────────────────────────────────────────
    if do_comparison:
        print("\n[对比实验图]")
        b_path = Path(args.baseline)
        o_path = Path(args.optimized)
        if not b_path.exists() or not o_path.exists():
            print(f"  找不到对比 CSV：{b_path} 或 {o_path}")
            print("  请先运行 ComparisonExperiment.java。")
        else:
            b_rows = read_csv(str(b_path))
            o_rows = read_csv(str(o_path))
            print(f"  基准组: {len(b_rows)} 行 | 优化组: {len(o_rows)} 行")
            plot_comparison_boxplot(b_rows, o_rows)
            plot_availability_histogram(b_rows, o_rows)
            plot_bar_comparison(b_rows, o_rows)
            plot_scatter_ripple_vs_cost(b_rows, o_rows)

            # 打印统计摘要
            print("\n  统计摘要：")
            print(f"  {'指标':<30} {'基准均值':>12} {'优化均值':>12} {'改进率':>10}")
            print("  " + "─" * 68)
            for key, (label, unit, higher_is_better) in METRICS.items():
                bm = summarize(b_rows).get(key, 0)
                om = summarize(o_rows).get(key, 0)
                if bm == 0:
                    rate = "—"
                else:
                    rate_val = (bm - om) / bm * 100 if higher_is_better else (om - bm) / bm * 100
                    rate = f"{rate_val:+.1f}%"
                print(f"  {label:<30} {bm:>12.4f} {om:>12.4f} {rate:>10}")

    # ── 敏感性曲线 ────────────────────────────────────────
    if do_sensitivity:
        print("\n[敏感性分析图]")
        plot_sensitivity_curve()

    print("\n══════════════════════════════════════════════")
    print(f" 输出目录：{OUTPUT_DIR.resolve()}")
    print("══════════════════════════════════════════════")


if __name__ == "__main__":
    main()
