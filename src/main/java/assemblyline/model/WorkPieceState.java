package assemblyline.model;

/**
 * 在制品（车身）在产线上的状态。
 */
public enum WorkPieceState {
    /** 在源端等待进入产线 */
    WAITING_TO_ENTER,
    /** 在某工位等待加工 */
    QUEUED_AT_STATION,
    /** 正在工位加工中 */
    PROCESSING,
    /** 在缓存区等待 */
    IN_BUFFER,
    /** 已下线 */
    COMPLETED,
    /** 因设备故障而阻塞 */
    BLOCKED_BY_FAILURE
}
