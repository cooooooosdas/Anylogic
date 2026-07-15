package assemblyline.model;

import java.util.UUID;

/**
 * 在制品（车身/车架）实体。
 * 在产线中流动，携带车型信息，记录在各工位的时刻用于统计周期。
 */
public class WorkPiece {
    private final String id;
    private final VehicleModel model;
    private final double arrivalTime; // 进入产线的时刻

    private int currentStationIndex;
    private WorkPieceState state;
    private double enterStationTime;
    private double exitStationTime;
    private double completionTime; // 完成全部工位的时刻

    public WorkPiece(VehicleModel model, double arrivalTime) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.model = model;
        this.arrivalTime = arrivalTime;
        this.currentStationIndex = 0;
        this.state = WorkPieceState.WAITING_TO_ENTER;
    }

    // Getters
    public String getId() { return id; }
    public VehicleModel getModel() { return model; }
    public double getArrivalTime() { return arrivalTime; }
    public int getCurrentStationIndex() { return currentStationIndex; }
    public WorkPieceState getState() { return state; }
    public double getEnterStationTime() { return enterStationTime; }
    public double getExitStationTime() { return exitStationTime; }
    public double getCompletionTime() { return completionTime; }

    // Setters (fluent style for simulation engine)
    public void setCurrentStationIndex(int idx) { this.currentStationIndex = idx; }
    public void setState(WorkPieceState state) { this.state = state; }
    public void setEnterStationTime(double t) { this.enterStationTime = t; }
    public void setExitStationTime(double t) { this.exitStationTime = t; }
    public void setCompletionTime(double t) { this.completionTime = t; }

    /**
     * 本工位停留时间（加工 + 等Queue）
     */
    public double getStationDwellTime() {
        if (exitStationTime <= 0 || enterStationTime <= 0) return 0;
        return exitStationTime - enterStationTime;
    }

    /**
     * 全产线周期时间
     */
    public double getTotalCycleTime() {
        if (completionTime <= 0) return 0;
        return completionTime - arrivalTime;
    }

    @Override
    public String toString() {
        return "WP[" + id + "] " + model.getName()
                + " station=" + currentStationIndex
                + " state=" + state;
    }
}
