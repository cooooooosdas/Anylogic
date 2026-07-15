package assemblyline.model;

/**
 * 颜色枚举，对应换型矩阵 COLOR 维度。
 */
public enum Color {
    WHITE("白"),
    RED("红"),
    BLUE("蓝"),
    BLACK("黑");

    public final String chineseName;
    Color(String chineseName) { this.chineseName = chineseName; }
}
