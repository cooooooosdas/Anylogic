package assemblyline.model;

/**
 * 工位间缓存区。
 * 容量有限，满则阻塞上游工位，空则导致下游工位饥饿。
 */
public class Buffer {
    private final String id;
    private final String fromLine;
    private final String toLine;
    private final int capacity;

    private int currentCount;
    private long totalBlockedTime; // 上游因缓冲区满而等待的累计时间
    private long totalStarveTime;  // 下游因缓冲区空而等待的累计时间
    private int peakOccupancy;

    public Buffer(String id, String fromLine, String toLine, int capacity) {
        this.id = id;
        this.fromLine = fromLine;
        this.toLine = toLine;
        this.capacity = capacity;
        this.currentCount = 0;
        this.totalBlockedTime = 0;
        this.totalStarveTime = 0;
        this.peakOccupancy = 0;
    }

    public String getId() { return id; }
    public String getFromLine() { return fromLine; }
    public String getToLine() { return toLine; }
    public int getCapacity() { return capacity; }
    public int getCurrentCount() { return currentCount; }
    public int getPeakOccupancy() { return peakOccupancy; }
    public long getTotalBlockedTime() { return blockedTime(); }
    public long getTotalStarveTime() { return starveTime(); }

    public synchronized boolean tryAdd() {
        if (currentCount >= capacity) return false;
        currentCount++;
        if (currentCount > peakOccupancy) peakOccupancy = currentCount;
        return true;
    }

    public synchronized boolean tryRemove() {
        if (currentCount <= 0) return false;
        currentCount--;
        return true;
    }

    public synchronized boolean isFull()  { return currentCount >= capacity; }
    public synchronized boolean isEmpty() { return currentCount <= 0; }

    public void addBlockedTime(long dt) { totalBlockedTime += dt; }
    public void addStarveTime(long dt)  { totalStarveTime += dt; }

    public long blockedTime() { return totalBlockedTime; }
    public long starveTime()  { return totalStarveTime; }

    @Override
    public String toString() {
        return "Buffer[" + id + "] " + fromLine + "→" + toLine
                + " cap=" + capacity + " cur=" + currentCount;
    }
}
