package assemblyline.model;

/**
 * 发动机类型枚举，对应换型矩阵 ENGINE 维度。
 */
public enum EngineType {
    DIESEL("柴油"),
    GAS("燃气"),
    NEW_ENERGY("新能源");

    public final String chineseName;
    EngineType(String chineseName) { this.chineseName = chineseName; }
}
