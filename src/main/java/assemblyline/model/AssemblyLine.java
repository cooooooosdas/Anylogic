package assemblyline.model;

import java.util.*;

/**
 * 整条混流装配线的拓扑结构。
 * 包含四条主线（内饰→底盘→外饰→检测）的工位、缓存区和路由逻辑。
 * 数据来源：data/stations.json。
 */
public class AssemblyLine {
    private final String name;
    private final List<Line> lines;
    private final List<Buffer> buffers;

    public AssemblyLine(String name) {
        this.name = name;
        this.lines = new ArrayList<>();
        this.buffers = new ArrayList<>();
    }

    // Getters
    public String getName() { return name; }
    public List<Line> getLines() { return lines; }
    public List<Buffer> getBuffers() { return buffers; }

    public void addLine(Line line) { lines.add(line); }
    public void addBuffer(Buffer buffer) { buffers.add(buffer); }

    /**
     * 获取所有工位的扁平列表。
     */
    public List<Station> getAllStations() {
        List<Station> all = new ArrayList<>();
        for (Line line : lines) {
            all.addAll(line.getStations());
        }
        return all;
    }

    /**
     * 根据工位 ID 查找工位。
     */
    public Optional<Station> findStation(String stationId) {
        return getAllStations().stream()
                .filter(s -> s.getId().equals(stationId))
                .findFirst();
    }

    /**
     * 获取指定工位之后的下一个工位。
     */
    public Optional<Station> nextStation(Station current) {
        List<Station> all = getAllStations();
        int idx = all.indexOf(current);
        if (idx >= 0 && idx < all.size() - 1) {
            return Optional.of(all.get(idx + 1));
        }
        return Optional.empty(); // 已是最后一个工位
    }

    /**
     * 获取指定工位所属的产线。
     */
    public Optional<Line> findLineOf(Station station) {
        return lines.stream()
                .filter(l -> l.getStations().contains(station))
                .findFirst();
    }

    /**
     * 获取两工位之间的缓存区（如果存在）。
     */
    public Optional<Buffer> findBufferBetween(Station from, Station to) {
        return buffers.stream()
                .filter(b -> b.getFromLine().equals(from.getLineId())
                        && b.getToLine().equals(to.getLineId()))
                .findFirst();
    }

    @Override
    public String toString() {
        return "AssemblyLine[" + name + "] "
                + lines.size() + " lines, "
                + getAllStations().size() + " stations, "
                + buffers.size() + " buffers";
    }

    // ── Nested types ──────────────────────────────────────────────

    /**
     * 一条子线（如内饰线、底盘线）。
     */
    public static class Line {
        private final String id;
        private final String name;
        private final List<Station> stations;

        public Line(String id, String name) {
            this.id = id;
            this.name = name;
            this.stations = new ArrayList<>();
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public List<Station> getStations() { return stations; }
        public void addStation(Station s) { stations.add(s); }

        @Override
        public String toString() {
            return "Line[" + id + "] " + name + " (" + stations.size() + " stations)";
        }
    }
}
