package assemblyline.simulation;

import assemblyline.model.*;
import assemblyline.phm.*;
import assemblyline.scheduling.ChangeoverMatrix;
import assemblyline.utils.Distribution;
import assemblyline.utils.RandomGenerator;

import java.util.*;

/**
 * 单次蒙特卡洛仿真运行（事件驱动）。
 * 使用优先级队列管理未来事件。
 *
 * 事件类型：
 *  ARRIVAL        — 新车身进入产线首站
 *  PROC_COMPLETE   — 工位加工完成
 *  CHANGEOVER_END  — 换型完成
 *  FAILURE         — 设备故障
 *  MAINT_END       — 维护完成
 *
 * // AnyLogic API: 对应 AnyLogic 的 Timeout / Schedule 机制
 */
public class SimulationEngine {

    // ── 事件 ─────────────────────────────────────────────────
    public enum EventType { ARRIVAL, PROC_COMPLETE, CHANGEOVER_END, FAILURE, MAINT_END }

    /** 仿真事件。payload 存储附加数据。 */
    public static class SimEvent implements Comparable<SimEvent> {
        private static long seq = 0;
        public final long id;
        public final double time;
        public final EventType type;
        public final Object[] payload;
        public SimEvent(double time, EventType type, Object... payload) {
            this.id = seq++;
            this.time = time; this.type = type; this.payload = payload;
        }
        @Override public int compareTo(SimEvent o) {
            int c = Double.compare(time, o.time);
            return c != 0 ? c : Long.compare(id, o.id);
        }
    }

    // ── 结果 ─────────────────────────────────────────────────
    public static class RunResult {
        public double simDuration;
        public double lineAvailability = 0;
        public int unplannedDowntimeCount = 0;
        public double unplannedDowntimeMinutes = 0;
        public int changeoverEventCount = 0;
        public double totalChangeoverMinutes = 0;
        public int completedCount = 0;
        public double totalCycleMinutes = 0;
        public double avgCycleMinutes = 0;
        public int wipPeak = 0;
        public double plannedMaintMinutes = 0;
        public double rippleEffectMinutes = 0;
        public double failurePredictionLeadTime = 0;
        public int predictionHits = 0;
        public Map<String, Double> stationUtilization = new LinkedHashMap<>();
        public Map<String, Integer> stationPeakQueue = new LinkedHashMap<>();
        public Map<String, Integer> stationFailures = new LinkedHashMap<>();
        public double maintenanceCost = 0;

        @Override
        public String toString() {
            return "RunResult{" +
                    "availability=" + String.format("%.1f%%", lineAvailability * 100) +
                    ", unplannedEvents=" + unplannedDowntimeCount +
                    ", avgCycle=" + String.format("%.1fmin", avgCycleMinutes) +
                    ", completed=" + completedCount +
                    ", changeovers=" + changeoverEventCount +
                    ", rippleMin=" + String.format("%.0f", rippleEffectMinutes) +
                    ", wipPeak=" + wipPeak +
                    '}';
        }
    }

    // ── 配置 ─────────────────────────────────────────────────
    private final AssemblyLine assemblyLine;
    private final List<VehicleModel> modelPool;
    private final ChangeoverMatrix changeoverMatrix;
    private final MaintenanceStrategy maintenanceStrategy;
    private final RandomGenerator rng;
    private final double simDuration;
    private final double warmUp;
    private final double arrivalMean;
    private final double costPerDowntimeMin;
    private final double costPerMaintHour;

    // ── 运行时状态 ────────────────────────────────────────────
    private double currentTime;
    private final PriorityQueue<SimEvent> queue = new PriorityQueue<>();
    private final List<WorkPiece> completed = new ArrayList<>();
    private int wipInSystem = 0;
    private int wipPeak = 0;
    private double rippleStart = -1;
    private double totalRippleMin = 0;
    private int plannedMaintCount = 0;
    private int predictionHits = 0;
    private double totalLeadMin = 0;

    private static class StationState {
        final Queue<WorkPiece> workQueue = new ArrayDeque<>();
        WorkPiece currentPiece = null;
        WorkPiece previousPiece = null;  // 用于换型判断
        double availableFrom = 0;
        boolean inChangeover = false;
        double changeoverEndsAt = 0;
        double busyMinutes = 0;
        int peakQueueLen = 0;
        int failureCount = 0;
        double lastProcStart = -1;

        boolean isFreeAt(double t) {
            return !inChangeover && availableFrom <= t && currentPiece == null;
        }
    }
    private final Map<String, StationState> stationState = new HashMap<>();
    private final Map<String, EquipmentHealth> equipmentMap = new HashMap<>();
    private final Map<String, ThreeStateModel> modelMap = new HashMap<>();

    /** 工位 ID → 归属设备列表（用于全面 PHM 推进）。 */
    private final Map<String, List<EquipmentHealth>> stationEquipMap = new HashMap<>();

    // ── 构造函数 ──────────────────────────────────────────────
    public SimulationEngine(AssemblyLine assemblyLine,
                            List<VehicleModel> modelPool,
                            ChangeoverMatrix changeoverMatrix,
                            MaintenanceStrategy maintenanceStrategy,
                            RandomGenerator rng,
                            double simDurationMinutes,
                            double warmUpMinutes,
                            double arrivalMeanMinutes) {
        this(assemblyLine, modelPool, changeoverMatrix, maintenanceStrategy,
                rng, simDurationMinutes, warmUpMinutes, arrivalMeanMinutes,
                2667, 150);
    }

    public SimulationEngine(AssemblyLine assemblyLine,
                            List<VehicleModel> modelPool,
                            ChangeoverMatrix changeoverMatrix,
                            MaintenanceStrategy maintenanceStrategy,
                            RandomGenerator rng,
                            double simDurationMinutes,
                            double warmUpMinutes,
                            double arrivalMeanMinutes,
                            double costPerDowntimeMin,
                            double costPerMaintHour) {
        this.assemblyLine = assemblyLine;
        this.modelPool = new ArrayList<>(modelPool);
        this.changeoverMatrix = changeoverMatrix;
        this.maintenanceStrategy = maintenanceStrategy;
        this.rng = rng;
        this.simDuration = simDurationMinutes;
        this.warmUp = warmUpMinutes;
        this.arrivalMean = arrivalMeanMinutes;
        this.costPerDowntimeMin = costPerDowntimeMin;
        this.costPerMaintHour = costPerMaintHour;
    }

    public void setEquipments(Map<String, EquipmentHealth> eqs,
                              Map<String, ThreeStateModel> models) {
        equipmentMap.putAll(eqs);
        modelMap.putAll(models);
        stationEquipMap.clear();
        for (EquipmentHealth eq : eqs.values()) {
            String sid = eq.getStationId();
            if (sid != null && !sid.isBlank()) {
                stationEquipMap.computeIfAbsent(sid, k -> new ArrayList<>()).add(eq);
            }
        }
        for (Station s : assemblyLine.getAllStations()) {
            stationState.putIfAbsent(s.getId(), new StationState());
        }
    }

    // ── 主运行 ──────────────────────────────────────────────
    public RunResult run() {
        RunResult result = new RunResult();
        result.simDuration = simDuration;
        init();
        scheduleArrival(Distribution.poissonInterArrival(rng, arrivalMean));
        while (!queue.isEmpty()) {
            SimEvent ev = queue.poll();
            if (ev.time > simDuration) break;
            currentTime = ev.time;
            dispatch(ev, result);
        }
        computeStats(result);
        return result;
    }

    // ── 初始化 ──────────────────────────────────────────────
    private void init() {
        currentTime = 0;
        completed.clear();
        queue.clear();
        wipInSystem = 0;
        wipPeak = 0;
        rippleStart = -1;
        totalRippleMin = 0;
        plannedMaintCount = 0;
        predictionHits = 0;
        totalLeadMin = 0;
        stationState.clear();
        for (Station s : assemblyLine.getAllStations()) {
            // 每次蒙特卡洛运行前重置工位可变状态，避免跨 run 污染
            s.resetRuntimeState();
            stationState.put(s.getId(), new StationState());
        }
    }

    private void scheduleArrival(double at) {
        if (at < simDuration) queue.add(new SimEvent(at, EventType.ARRIVAL));
    }

    // ── 事件分发 ──────────────────────────────────────────────
    private void dispatch(SimEvent ev, RunResult result) {
        switch (ev.type) {
            case ARRIVAL       -> onArrival(result);
            case PROC_COMPLETE -> onProcComplete(ev, result);
            case CHANGEOVER_END-> onChangeoverEnd(ev, result);
            case FAILURE       -> onFailure(ev, result);
            case MAINT_END     -> onMaintEnd(ev, result);
        }
    }

    // ── 具体处理 ──────────────────────────────────────────────

    /** 新车身到达：尝试放入首站 */
    private void onArrival(RunResult result) {
        List<Station> all = assemblyLine.getAllStations();
        if (all.isEmpty()) return;
        Station first = all.get(0);
        StationState ss = stationState.get(first.getId());

        VehicleModel model = pickModel();
        WorkPiece wp = new WorkPiece(model, currentTime);
        wipInSystem++;
        refreshWipPeak();

        if (ss.isFreeAt(currentTime)) {
            startProc(wp, first, result);
        } else {
            ss.workQueue.add(wp);
            ss.peakQueueLen = Math.max(ss.peakQueueLen, ss.workQueue.size());
        }
        scheduleArrival(currentTime + Distribution.poissonInterArrival(rng, arrivalMean));
    }

    /** 加工完成 */
    private void onProcComplete(SimEvent ev, RunResult result) {
        WorkPiece wp = (WorkPiece) ev.payload[0];
        Station station = (Station) ev.payload[1];
        StationState ss = stationState.get(station.getId());

        wp.setExitStationTime(currentTime);
        if (ss.lastProcStart >= 0) {
            ss.busyMinutes += currentTime - ss.lastProcStart;
        }
        // 记录上一件工件（用于换型判断）
        ss.previousPiece = ss.currentPiece;
        ss.currentPiece = null;

        List<Station> all = assemblyLine.getAllStations();
        int idx = all.indexOf(station);
        Station next = (idx + 1 < all.size()) ? all.get(idx + 1) : null;

        if (next == null) {
            wp.setCompletionTime(currentTime);
            wp.setState(WorkPieceState.COMPLETED);
            completed.add(wp);
            wipInSystem--;
            if (currentTime > warmUp) {
                result.completedCount++;
                result.totalCycleMinutes += wp.getTotalCycleTime();
            }
        } else {
            StationState nextSs = stationState.get(next.getId());
            WorkPiece toProcess = wp;
            if (nextSs.isFreeAt(currentTime)) {
                startProc(toProcess, next, result);
            } else {
                nextSs.workQueue.add(toProcess);
                nextSs.peakQueueLen = Math.max(nextSs.peakQueueLen, nextSs.workQueue.size());
            }
        }

        // 本站队列中若有等待工件，继续加工
        if (!ss.workQueue.isEmpty() && ss.isFreeAt(currentTime)) {
            WorkPiece nextWp = ss.workQueue.poll();
            if (ss.previousPiece != null && station.isChangeoverPoint()) {
                double ct = changeoverMatrix.changeoverTime(
                        ss.previousPiece.getModel(), nextWp.getModel());
                if (ct > 0) {
                    triggerChangeover(station, ss, ct, nextWp, result);
                    return;
                }
            }
            startProc(nextWp, station, result);
        }
    }

    /** 换型结束 */
    private void onChangeoverEnd(SimEvent ev, RunResult result) {
        Station station = (Station) ev.payload[0];
        StationState ss = stationState.get(station.getId());
        @SuppressWarnings("unchecked")
        WorkPiece nextWp = (WorkPiece) ev.payload[1];

        ss.inChangeover = false;
        ss.changeoverEndsAt = 0;
        if (!ss.workQueue.isEmpty() && ss.isFreeAt(currentTime)) {
            WorkPiece wp = ss.workQueue.poll();
            startProc(wp, station, result);
        } else if (nextWp != null && ss.isFreeAt(currentTime)) {
            startProc(nextWp, station, result);
        }
    }

    /** 设备故障 */
    private void onFailure(SimEvent ev, RunResult result) {
        @SuppressWarnings("unchecked")
        EquipmentHealth eq = (EquipmentHealth) ev.payload[0];
        eq.getState();
        if (currentTime > warmUp) {
            result.unplannedDowntimeCount++;
            rippleStart = currentTime;
        }
        double mttr = Distribution.triangular(
                rng, eq.getMttrMin(), eq.getMttrMean(), eq.getMttrMax());
        queue.add(new SimEvent(currentTime + mttr, EventType.MAINT_END, eq));
    }

    /** 维护结束 */
    private void onMaintEnd(SimEvent ev, RunResult result) {
        @SuppressWarnings("unchecked")
        EquipmentHealth eq = (EquipmentHealth) ev.payload[0];
        eq.performMaintenance(currentTime);
        plannedMaintCount++;
        double maintMin = eq.getTimeSinceMaintenance() * 60;
        result.plannedMaintMinutes += maintMin;
        result.maintenanceCost += maintMin / 60 * costPerMaintHour;
        if (rippleStart > 0) {
            totalRippleMin += currentTime - rippleStart;
            rippleStart = -1;
        }
    }

    // ── 加工启动 ──────────────────────────────────────────────
    private void startProc(WorkPiece wp, Station station, RunResult result) {
        StationState ss = stationState.get(station.getId());
        wp.setState(WorkPieceState.PROCESSING);
        wp.setEnterStationTime(currentTime);
        ss.currentPiece = wp;
        ss.lastProcStart = currentTime;

        double procMin = Distribution.triangular(
                rng,
                station.getProcessingTimeMin(),
                station.getProcessingTimeMean(),
                station.getProcessingTimeMax());
        double endTime = currentTime + procMin;
        ss.availableFrom = endTime;
        ss.inChangeover = false;

        queue.add(new SimEvent(endTime, EventType.PROC_COMPLETE, wp, station));
        advanceEquipFor(station, procMin / 60.0, result);
    }

    private void triggerChangeover(Station station, StationState ss,
                                   double changeoverMin, WorkPiece nextWp,
                                   RunResult result) {
        ss.inChangeover = true;
        ss.changeoverEndsAt = currentTime + changeoverMin;
        station.startChangeover(currentTime, changeoverMin);
        if (currentTime > warmUp) {
            result.changeoverEventCount++;
            result.totalChangeoverMinutes += changeoverMin;
        }
        queue.add(new SimEvent(
                currentTime + changeoverMin,
                EventType.CHANGEOVER_END, station, nextWp));
    }

    /** 推进指定工位归属设备的健康（已扩展到全设备），并检查维护策略。 */
    private void advanceEquipFor(Station station, double hours, RunResult result) {
        List<EquipmentHealth> equipments = stationEquipMap.get(station.getId());
        if (equipments == null || equipments.isEmpty()) return;
        for (EquipmentHealth eq : equipments) {
            String eid = eq.getEquipmentId();
            ThreeStateModel m = modelMap.get(eid);
            if (m == null) continue;
            eq.incrementHours(hours);
            boolean failed = m.advance(hours);
            if (failed) {
                StationState ss = stationState.get(station.getId());
                ss.failureCount++;
                if (currentTime > warmUp) {
                    result.unplannedDowntimeCount++;
                    rippleStart = currentTime;
                }
                double mttrH = Distribution.triangular(
                        rng, eq.getMttrMin(), eq.getMttrMean(), eq.getMttrMax());
                double mttrMin = mttrH * 60.0;
                result.unplannedDowntimeMinutes += mttrMin;
                result.maintenanceCost += mttrMin / 60.0 * costPerDowntimeMin;
                queue.add(new SimEvent(currentTime + mttrMin, EventType.MAINT_END, eq));
            }
            // 维护策略检查：S1 计划性 / S3 预测性
            List<String> toMaintain = maintenanceStrategy.shouldMaintain(
                    currentTime, Collections.singletonList(eq));
            if (toMaintain.contains(eq.getEquipmentId())) {
                double maintH = eq.getTimeSinceMaintenance();
                double maintMin = maintH * 60.0;
                result.plannedMaintMinutes += maintMin;
                result.maintenanceCost += maintMin / 60.0 * costPerMaintHour;
                eq.performMaintenance(currentTime);
                plannedMaintCount++;
                if (eq.getState() == HealthState.DEGRADED) {
                    predictionHits++;
                    totalLeadMin += m.predictRUL();
                }
            }
            if (eq.getState() == HealthState.DEGRADED) {
                double rul = m.predictRUL();
                if (rul > 0 && rul < 72) {
                    predictionHits++;
                    totalLeadMin += rul;
                }
            }
        }
    }

    // ── 车型选择 ──────────────────────────────────────────────
    private VehicleModel pickModel() {
        int total = modelPool.stream().mapToInt(VehicleModel::getAnnualVolume).sum();
        if (total <= 0) return modelPool.get(0);
        int r = rng.nextInt(total);
        int cum = 0;
        for (VehicleModel m : modelPool) {
            cum += m.getAnnualVolume();
            if (r < cum) return m;
        }
        return modelPool.get(modelPool.size() - 1);
    }

    private void refreshWipPeak() {
        if (wipInSystem > wipPeak) wipPeak = wipInSystem;
    }

    // ── 统计汇总 ──────────────────────────────────────────────
    private void computeStats(RunResult r) {
        if (r.completedCount > 0) r.avgCycleMinutes = r.totalCycleMinutes / r.completedCount;
        r.wipPeak = wipPeak;
        r.rippleEffectMinutes = totalRippleMin;
        r.failurePredictionLeadTime = predictionHits > 0
                ? totalLeadMin / predictionHits : 0;
        double totalPossible = simDuration * Math.max(1, assemblyLine.getAllStations().size());
        r.lineAvailability = Math.max(0, 1 - r.unplannedDowntimeMinutes / totalPossible);
        r.plannedMaintMinutes = plannedMaintCount * 720;
        for (Map.Entry<String, StationState> e : stationState.entrySet()) {
            r.stationUtilization.put(e.getKey(),
                    e.getValue().busyMinutes / Math.max(1, simDuration));
            r.stationPeakQueue.put(e.getKey(), e.getValue().peakQueueLen);
            r.stationFailures.put(e.getKey(), e.getValue().failureCount);
        }
    }
}
