package assemblyline.utils;

/**
 * 概率分布工具类。封装常用的随机采样逻辑。
 * 所有方法均委托给 RandomGenerator，保证蒙特卡洛复现性。
 */
public class Distribution {

    private Distribution() {} // 静态工具类

    /**
     * 三角分布采样。
     * @param lo  最乐观值（下界）
     * @param mode 最可能值（峰值）
     * @param hi  最悲观值（上界）
     */
    public static double triangular(RandomGenerator rng, double lo, double mode, double hi) {
        double u = rng.nextDouble();
        double fc = (mode - lo) / (hi - lo);
        if (u < fc) {
            return lo + Math.sqrt(u * (hi - lo) * (mode - lo));
        } else {
            return hi - Math.sqrt((1 - u) * (hi - lo) * (hi - mode));
        }
    }

    /**
     * Weibull 分布采样（逆变换法）。
     * @param beta 形状参数
     * @param eta  尺度参数
     * @return 寿命（>0）
     */
    public static double weibull(RandomGenerator rng, double beta, double eta) {
        double u = 1 - rng.nextDouble(); // 避免 log(0)
        return eta * Math.pow(-Math.log(u), 1.0 / beta);
    }

    /**
     * 正态分布采样（截断到 [lo, hi]）。
     */
    public static double normal(RandomGenerator rng, double mean, double stdDev, double lo, double hi) {
        return rng.normalTruncated(mean, stdDev, lo, hi);
    }

    /**
     * 指数分布采样。
     */
    public static double exponential(RandomGenerator rng, double mean) {
        return rng.exponential(mean);
    }

    /**
     * 均匀分布采样。
     */
    public static double uniform(RandomGenerator rng, double lo, double hi) {
        return rng.uniform(lo, hi);
    }

    /**
     * 泊松到达间隔（指数分布）。
     */
    public static double poissonInterArrival(RandomGenerator rng, double meanMinutes) {
        return exponential(rng, meanMinutes);
    }
}
