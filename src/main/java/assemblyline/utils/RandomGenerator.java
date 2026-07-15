package assemblyline.utils;

import java.util.Random;

/**
 * 可重置种子的随机数生成器。
 * 蒙特卡洛仿真要求相同种子序列下可复现。
 */
public class RandomGenerator {
    private long seed;
    private final Random rng;

    public RandomGenerator(long seed) {
        this.seed = seed;
        this.rng = new Random(seed);
    }

    /** 重置为初始种子（用于新的一次蒙特卡洛运行） */
    public void reset() {
        rng.setSeed(seed);
    }

    /** 使用当前种子推进一次（用于确定性跑批） */
    public long nextSeed() {
        return rng.nextLong();
    }

    // 基础随机
    public double nextDouble() { return rng.nextDouble(); }
    public int nextInt(int bound) { return rng.nextInt(bound); }
    public int nextInt(int lo, int hi) { return lo + rng.nextInt(hi - lo + 1); }
    public double nextGaussian() { return rng.nextGaussian(); }

    // 区间均匀
    public double uniform(double lo, double hi) {
        return lo + rng.nextDouble() * (hi - lo);
    }

    // 正态分布（Box-Muller）
    public double normal(double mean, double stdDev) {
        return mean + rng.nextGaussian() * stdDev;
    }

    // 截断正态（拒绝采样）
    public double normalTruncated(double mean, double stdDev, double lo, double hi) {
        double v;
        do { v = normal(mean, stdDev); } while (v < lo || v > hi);
        return v;
    }

    // 指数分布
    public double exponential(double mean) {
        return -mean * Math.log(1 - rng.nextDouble());
    }

    public long getSeed() { return seed; }
}
