package assemblyline.phm;

/**
 * 设备健康状态枚举。
 * 对应三状态模型：正常 → 劣化 → 故障。
 */
public enum HealthState {
    NORMAL("正常"),
    DEGRADED("劣化"),
    FAILED("故障");

    public final String label;
    HealthState(String label) { this.label = label; }
}
