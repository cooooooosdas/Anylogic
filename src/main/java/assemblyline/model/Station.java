package assemblyline.model;

/**
 * 装配工位。
 * 对应 stations.json 中的每个条目，是产线的处理单元。
 */
public class Station {
    private final String id;
    private final String name;
    private final String lineId;
    private final double processingTimeMean; // 最可能值（三角分布）
    private final double processingTimeMin;  // 乐观值
    private final double processingTimeMax;  // 悲观值

    private final boolean changeoverPoint;
    private final String changeoverType; // COLOR / DRIVE / ENGINE / CONFIGURATION / null

    private final boolean hasTighteningMachine; // 是否为智能拧紧机工位

    // 运行时统计
    private int queueLength;
    private double totalBusyTime;
    private double utilization; // 0~1
    private boolean underChangeover;
    private double changeoverEndTime;

    public Station(String id, String name, String lineId,
                   double processingTimeMin, double processingTimeMean, double processingTimeMax,
                   boolean changeoverPoint, String changeoverType,
                   boolean hasTighteningMachine) {
        this.id = id;
        this.name = name;
        this.lineId = lineId;
        this.processingTimeMin = processingTimeMin;
        this.processingTimeMean = processingTimeMean;
        this.processingTimeMax = processingTimeMax;
        this.changeoverPoint = changeoverPoint;
        this.changeoverType = changeoverType;
        this.hasTighteningMachine = hasTighteningMachine;
        this.queueLength = 0;
        this.totalBusyTime = 0;
        this.utilization = 0;
        this.underChangeover = false;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getLineId() { return lineId; }
    public double getProcessingTimeMean() { return processingTimeMean; }
    public double getProcessingTimeMin() { return processingTimeMin; }
    public double getProcessingTimeMax() { return processingTimeMax; }
    public boolean isChangeoverPoint() { return changeoverPoint; }
    public String getChangeoverType() { return changeoverType; }
    public boolean hasTighteningMachine() { return hasTighteningMachine; }
    public int getQueueLength() { return queueLength; }
    public double getUtilization() { return utilization; }
    public boolean isUnderChangeover() { return underChangeover; }
    public double getChangeoverEndTime() { return changeoverEndTime; }

    // Runtime updates
    public void incrementQueue() { queueLength++; }
    public void decrementQueue() { if (queueLength > 0) queueLength--; }
    public void addBusyTime(double dt) { totalBusyTime += dt; }
    public void setUtilization(double u) { this.utilization = u; }

    public void startChangeover(double currentTime, double changeoverDuration) {
        this.underChangeover = true;
        this.changeoverEndTime = currentTime + changeoverDuration;
    }

    public void endChangeover() {
        this.underChangeover = false;
        this.changeoverEndTime = 0;
    }

    /** 蒙特卡洛每次运行前重置，避免跨 run 污染 */
    public void resetRuntimeState() {
        this.queueLength = 0;
        this.totalBusyTime = 0;
        this.utilization = 0;
        this.underChangeover = false;
        this.changeoverEndTime = 0;
    }

    @Override
    public String toString() {
        return "Station[" + id + "] " + name
                + " line=" + lineId
                + " proc=" + processingTimeMean + "min"
                + (changeoverPoint ? " [换型点:" + changeoverType + "]" : "");
    }
}
