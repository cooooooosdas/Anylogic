package assemblyline.model;

import java.util.Objects;

/**
 * 车型定义 — 描述一种具体车型的属性组合。
 * 数据来源：data/vehicle_models.json。
 */
public class VehicleModel {
    private final String id;
    private final String name;
    private final VehicleCategory category;
    private final Color color;
    private final DriveType driveType;
    private final EngineType engineType;
    private final int annualVolume; // 年产量（辆），用于排产比例权重

    public VehicleModel(String id, String name, VehicleCategory category,
                        Color color, DriveType driveType, EngineType engineType,
                        int annualVolume) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.category = category;
        this.color = color;
        this.driveType = driveType;
        this.engineType = engineType;
        this.annualVolume = annualVolume;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public VehicleCategory getCategory() { return category; }
    public Color getColor() { return color; }
    public DriveType getDriveType() { return driveType; }
    public EngineType getEngineType() { return engineType; }
    public int getAnnualVolume() { return annualVolume; }

    /**
     * 获取指定换型维度的当前值。
     */
    public Object getDimension(String dimension) {
        return switch (dimension.toUpperCase()) {
            case "COLOR" -> color;
            case "DRIVE" -> driveType;
            case "ENGINE" -> engineType;
            case "PLATFORM" -> category;
            default -> throw new IllegalArgumentException("Unknown dimension: " + dimension);
        };
    }

    @Override
    public String toString() {
        return name + "(" + category.chineseName
                + "/" + color.chineseName
                + "/" + driveType.chineseName
                + "/" + engineType.chineseName + ")";
    }
}
