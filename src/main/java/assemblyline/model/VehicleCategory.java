package assemblyline.model;

/**
 * 车型大类枚举。
 * 对应换型矩阵中 PLATFORM 维度的值。
 */
public enum VehicleCategory {
    HEAVY("重卡"),
    MEDIUM("中卡"),
    LIGHT("轻卡"),
    BUS("客车");

    public final String chineseName;
    VehicleCategory(String chineseName) { this.chineseName = chineseName; }
}
