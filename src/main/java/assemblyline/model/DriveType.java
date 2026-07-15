package assemblyline.model;

/**
 * 驱动形式枚举，对应换型矩阵 DRIVE 维度。
 */
public enum DriveType {
    FWD("前驱"),
    RWD("后驱"),
    AWD("四驱");

    public final String chineseName;
    DriveType(String chineseName) { this.chineseName = chineseName; }
}
