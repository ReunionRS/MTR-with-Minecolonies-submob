package com.example.minecoloniesmtr.transit;

import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.UUID;

public class CitizenTransitPlan {

    public enum Status {
        IDLE,
        REQUESTING,
        READY_TO_BOARD,
        RIDING,
        WALK_TO_DESTINATION,
        UNAVAILABLE
    }

    private final UUID citizenId;
    private BlockPos requestedTarget;
    private BlockPos boardPos;
    private BlockPos disembarkPos;
    private long requiredRouteId;
    private long boardedTrainId;
    private Status status;
    private String lastDebugMarker;

    public CitizenTransitPlan(final UUID citizenId, final BlockPos requestedTarget) {
        this.citizenId = citizenId;
        this.requestedTarget = requestedTarget;
        this.boardPos = BlockPos.ZERO;
        this.disembarkPos = BlockPos.ZERO;
        this.requiredRouteId = 0;
        this.boardedTrainId = 0;
        this.status = Status.IDLE;
        this.lastDebugMarker = "";
    }

    public UUID getCitizenId() {
        return citizenId;
    }

    public BlockPos getRequestedTarget() {
        return requestedTarget;
    }

    public void setRequestedTarget(final BlockPos requestedTarget) {
        this.requestedTarget = requestedTarget;
    }

    public BlockPos getBoardPos() {
        return boardPos;
    }

    public void setBoardPos(final BlockPos boardPos) {
        this.boardPos = boardPos;
    }

    public BlockPos getDisembarkPos() {
        return disembarkPos;
    }

    public void setDisembarkPos(final BlockPos disembarkPos) {
        this.disembarkPos = disembarkPos;
    }

    public long getRequiredRouteId() {
        return requiredRouteId;
    }

    public void setRequiredRouteId(final long requiredRouteId) {
        this.requiredRouteId = requiredRouteId;
    }

    public long getBoardedTrainId() {
        return boardedTrainId;
    }

    public void setBoardedTrainId(final long boardedTrainId) {
        this.boardedTrainId = boardedTrainId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(final Status status) {
        this.status = status;
    }

    public String getLastDebugMarker() {
        return lastDebugMarker;
    }

    public void setLastDebugMarker(final String lastDebugMarker) {
        this.lastDebugMarker = lastDebugMarker;
    }

    public boolean hasSameTarget(final BlockPos target) {
        return Objects.equals(requestedTarget, target);
    }

    public void resetForTarget(final BlockPos newTarget) {
        requestedTarget = newTarget;
        boardPos = BlockPos.ZERO;
        disembarkPos = BlockPos.ZERO;
        requiredRouteId = 0;
        boardedTrainId = 0;
        status = Status.IDLE;
        lastDebugMarker = "";
    }
}
