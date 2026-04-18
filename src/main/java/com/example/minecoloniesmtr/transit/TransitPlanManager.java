package com.example.minecoloniesmtr.transit;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.logging.LogUtils;
import mtr.data.Platform;
import mtr.data.RailwayData;
import mtr.data.RailwayDataRouteFinderModule;
import mtr.data.Route;
import mtr.data.ScheduleEntry;
import mtr.data.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TransitPlanManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<UUID, CitizenTransitPlan> PLANS = new ConcurrentHashMap<>();
    private static final int MIN_TRANSIT_DISTANCE = 96;
    private static final int PLATFORM_SEARCH_RADIUS = 192;
    private static final int PLATFORM_SEARCH_BELOW = 16;
    private static final int PLATFORM_SEARCH_ABOVE = 24;
    private static final int BOARD_RADIUS = 20;
    private static final int DISEMBARK_RADIUS = 7;
    private static final int WAITING_SPOT_RADIUS = 6;

    private TransitPlanManager() {
    }

    public static BlockPos resolveTransitTarget(final Mob mob, final BlockPos requestedTarget) {
        if (!(mob instanceof AbstractEntityCitizen) || mob.level().isClientSide) {
            return requestedTarget;
        }

        final int distance = mob.blockPosition().distManhattan(requestedTarget);
        if (distance < MIN_TRANSIT_DISTANCE) {
            PLANS.remove(mob.getUUID());
            return requestedTarget;
        }

        final RailwayData railwayData = RailwayData.getInstance(mob.level());
        if (railwayData == null) {
            return requestedTarget;
        }

        final CitizenTransitPlan plan = PLANS.computeIfAbsent(mob.getUUID(), id -> new CitizenTransitPlan(id, requestedTarget));

        if (!plan.hasSameTarget(requestedTarget)) {
            plan.resetForTarget(requestedTarget);
        }

        if (plan.getStatus() == CitizenTransitPlan.Status.IDLE) {
            requestRoute(plan, railwayData, mob.level(), mob.blockPosition(), requestedTarget);
            return plan.getBoardPos() != BlockPos.ZERO ? plan.getBoardPos() : mob.blockPosition();
        }

        if (plan.getStatus() == CitizenTransitPlan.Status.REQUESTING) {
            return plan.getBoardPos() != BlockPos.ZERO ? plan.getBoardPos() : mob.blockPosition();
        }

        if (plan.getStatus() == CitizenTransitPlan.Status.UNAVAILABLE) {
            return requestedTarget;
        }

        if (plan.getStatus() == CitizenTransitPlan.Status.READY_TO_BOARD) {
            return plan.getBoardPos();
        }

        if (plan.getStatus() == CitizenTransitPlan.Status.RIDING) {
            return mob.blockPosition();
        }

        if (plan.getStatus() == CitizenTransitPlan.Status.WALK_TO_DESTINATION) {
            if (mob.blockPosition().distManhattan(plan.getRequestedTarget()) < 3) {
                PLANS.remove(mob.getUUID());
            }
            return plan.getRequestedTarget();
        }

        return requestedTarget;
    }

    public static boolean canBoard(final AbstractEntityCitizen citizen, final long routeId, final double carX, final double carY, final double carZ) {
        final CitizenTransitPlan plan = PLANS.get(citizen.getUUID());
        if (plan == null || plan.getStatus() != CitizenTransitPlan.Status.READY_TO_BOARD) {
            return false;
        }

        if (plan.getRequiredRouteId() != routeId) {
            return false;
        }

        final BlockPos boardPos = plan.getBoardPos();
        if (citizen.blockPosition().distManhattan(boardPos) > BOARD_RADIUS) {
            return false;
        }
        return true;
    }

    public static void markBoarded(final AbstractEntityCitizen citizen, final long trainId) {
        final CitizenTransitPlan plan = PLANS.get(citizen.getUUID());
        if (plan == null) {
            return;
        }
        plan.setBoardedTrainId(trainId);
        plan.setStatus(CitizenTransitPlan.Status.RIDING);
    }

    public static boolean shouldDisembark(final AbstractEntityCitizen citizen, final long routeId, final BlockPos trainPos) {
        final CitizenTransitPlan plan = PLANS.get(citizen.getUUID());
        if (plan == null || plan.getStatus() != CitizenTransitPlan.Status.RIDING) {
            return false;
        }

        if (plan.getRequiredRouteId() != routeId) {
            return false;
        }

        return trainPos.distManhattan(plan.getDisembarkPos()) <= DISEMBARK_RADIUS;
    }

    public static void markDisembarked(final AbstractEntityCitizen citizen) {
        final CitizenTransitPlan plan = PLANS.get(citizen.getUUID());
        if (plan == null) {
            return;
        }
        plan.setStatus(CitizenTransitPlan.Status.WALK_TO_DESTINATION);
        plan.setBoardedTrainId(0);
    }

    private static void requestRoute(final CitizenTransitPlan plan, final RailwayData railwayData, final Level level, final BlockPos from, final BlockPos to) {
        final long fromPlatformId = RailwayData.getClosePlatformId(
            railwayData.platforms,
            railwayData.dataCache,
            from,
            PLATFORM_SEARCH_RADIUS,
            PLATFORM_SEARCH_BELOW,
            PLATFORM_SEARCH_ABOVE
        );
        final long toPlatformId = RailwayData.getClosePlatformId(
            railwayData.platforms,
            railwayData.dataCache,
            to,
            PLATFORM_SEARCH_RADIUS,
            PLATFORM_SEARCH_BELOW,
            PLATFORM_SEARCH_ABOVE
        );

        final Platform fromPlatform = railwayData.dataCache.platformIdMap.get(fromPlatformId);
        final Platform toPlatform = railwayData.dataCache.platformIdMap.get(toPlatformId);
        final AbstractEntityCitizen citizen = getCitizenByUuid((ServerLevel) level, plan.getCitizenId());

        if (fromPlatform == null) {
            plan.setStatus(CitizenTransitPlan.Status.UNAVAILABLE);
            LOGGER.info("[minecolonies_mtr_integration] No start platform for citizen {} from={} to={}", plan.getCitizenId(), from, to);
            notifyCitizenOnce(citizen, plan, "no_start_platform", "Не найдена стартовая платформа рядом с NPC");
            return;
        }

        // Start moving to boarding platform immediately while MTR route finder is computing.
        plan.setBoardPos(findPlatformWaitingSpot(level, fromPlatform.getMidPos(), from));
        plan.setStatus(CitizenTransitPlan.Status.REQUESTING);

        if (toPlatform == null) {
            plan.setStatus(CitizenTransitPlan.Status.UNAVAILABLE);
            LOGGER.info("[minecolonies_mtr_integration] No destination platform for citizen {} from={} to={}", plan.getCitizenId(), from, to);
            notifyCitizenOnce(citizen, plan, "no_destination_platform", "Не найдена платформа назначения рядом с целевой точкой");
            return;
        }

        // Prefer synchronous route selection to avoid async queue starvation in busy worlds.
        if (buildRoutePlanSynchronously(plan, railwayData, level, from, to, fromPlatform, toPlatform)) {
            final String boardInfo = describePlatform(railwayData, railwayData.dataCache.platformIdMap.values().stream()
                .filter(platform -> platform.getMidPos().equals(plan.getBoardPos()) || findPlatformWaitingSpot(level, platform.getMidPos(), from).equals(plan.getBoardPos()))
                .findFirst()
                .orElse(fromPlatform));
            notifyCitizenOnce(citizen, plan, "sync_ready_" + plan.getRequiredRouteId() + "_" + plan.getBoardPos(), "NPC идет к " + boardInfo + ", маршрут " + plan.getRequiredRouteId());
            return;
        }

        final boolean queued = railwayData.railwayDataRouteFinderModule.findRoute(
            fromPlatform.getMidPos(),
            toPlatform.getMidPos(),
            4,
            (routeData, ms) -> onRouteCalculated(plan.getCitizenId(), routeData, railwayData, level, from)
        );

        if (!queued) {
            plan.setStatus(CitizenTransitPlan.Status.UNAVAILABLE);
            LOGGER.info("[minecolonies_mtr_integration] Route request queue full for citizen {}", plan.getCitizenId());
            notifyCitizenOnce(citizen, plan, "queue_full", "Очередь MTR переполнена, маршрут для NPC не построен");
        }
    }

    private static boolean buildRoutePlanSynchronously(
        final CitizenTransitPlan plan,
        final RailwayData railwayData,
        final Level level,
        final BlockPos from,
        final BlockPos to,
        final Platform fromPlatform,
        final Platform toPlatform
    ) {
        long bestRouteId = 0;
        Platform bestBoardPlatform = null;
        int bestScore = Integer.MAX_VALUE;

        for (final Map.Entry<Long, Route> entry : railwayData.dataCache.routeIdMap.entrySet()) {
            final long routeId = entry.getKey();
            final Route route = entry.getValue();
            if (route == null || route.platformIds == null || route.platformIds.isEmpty()) {
                continue;
            }

            int destinationIndex = -1;
            for (int i = 0; i < route.platformIds.size(); i++) {
                if (route.platformIds.get(i).platformId == toPlatform.id) {
                    destinationIndex = i;
                    break;
                }
            }
            if (destinationIndex < 0) {
                continue;
            }

            for (int i = 0; i < route.platformIds.size(); i++) {
                final long platformId = route.platformIds.get(i).platformId;
                final Platform candidateBoardPlatform = railwayData.dataCache.platformIdMap.get(platformId);
                if (candidateBoardPlatform == null) {
                    continue;
                }

                if (candidateBoardPlatform.getMidPos().distManhattan(from) > PLATFORM_SEARCH_RADIUS) {
                    continue;
                }

                if (forwardStops(i, destinationIndex, route.platformIds.size()) <= 0) {
                    continue;
                }

                final int distanceScore = candidateBoardPlatform.getMidPos().distManhattan(from);
                final int toStartBias = fromPlatform.getMidPos().distManhattan(candidateBoardPlatform.getMidPos());
                final int directionScore = forwardStops(i, destinationIndex, route.platformIds.size()) * 64;
                final int score = distanceScore + toStartBias + directionScore;

                if (score < bestScore) {
                    bestScore = score;
                    bestRouteId = routeId;
                    bestBoardPlatform = candidateBoardPlatform;
                }
            }
        }

        if (bestRouteId == 0 || bestBoardPlatform == null) {
            return false;
        }

        plan.setRequiredRouteId(bestRouteId);
        plan.setBoardPos(findPlatformWaitingSpot(level, bestBoardPlatform.getMidPos(), from));
        plan.setDisembarkPos(findPlatformWaitingSpot(level, toPlatform.getMidPos(), to));
        plan.setStatus(CitizenTransitPlan.Status.READY_TO_BOARD);
        LOGGER.info(
            "[minecolonies_mtr_integration] Citizen {} syncRoute routeId={} board={} disembark={}",
            plan.getCitizenId(),
            bestRouteId,
            plan.getBoardPos(),
            plan.getDisembarkPos()
        );

        final AbstractEntityCitizen citizen = getCitizenByUuid((ServerLevel) level, plan.getCitizenId());
        notifyCitizenOnce(
            citizen,
            plan,
            "sync_route_" + bestRouteId + "_" + plan.getBoardPos(),
            "NPC идет к " + describePlatform(railwayData, bestBoardPlatform) + ", цель: " + describePlatform(railwayData, toPlatform)
        );
        return true;
    }

    private static void onRouteCalculated(
        final UUID citizenId,
        final List<RailwayDataRouteFinderModule.RouteFinderData> routeData,
        final RailwayData railwayData,
        final Level level,
        final BlockPos from
    ) {
        final CitizenTransitPlan plan = PLANS.get(citizenId);
        if (plan == null) {
            return;
        }

        RailwayDataRouteFinderModule.RouteFinderData firstTrainLeg = null;
        RailwayDataRouteFinderModule.RouteFinderData lastTrainLegSameRoute = null;

        for (final RailwayDataRouteFinderModule.RouteFinderData leg : routeData) {
            if (leg.routeId != 0) {
                if (firstTrainLeg == null) {
                    firstTrainLeg = leg;
                    lastTrainLegSameRoute = leg;
                } else if (leg.routeId == firstTrainLeg.routeId) {
                    lastTrainLegSameRoute = leg;
                } else {
                    break;
                }
            }
        }

        if (firstTrainLeg == null) {
            plan.setStatus(CitizenTransitPlan.Status.UNAVAILABLE);
            LOGGER.info("[minecolonies_mtr_integration] No train leg found for citizen {} route data size={}", citizenId, routeData.size());
            return;
        }

        plan.setRequiredRouteId(firstTrainLeg.routeId);

        final long destinationPlatformId = RailwayData.getClosePlatformId(
            railwayData.platforms,
            railwayData.dataCache,
            lastTrainLegSameRoute.pos,
            PLATFORM_SEARCH_RADIUS,
            PLATFORM_SEARCH_BELOW,
            PLATFORM_SEARCH_ABOVE
        );

        final BlockPos boardPlatformPos = chooseBoardingPlatformMidPos(
            railwayData,
            from,
            firstTrainLeg.routeId,
            destinationPlatformId
        );
        if (boardPlatformPos != null) {
            plan.setBoardPos(findPlatformWaitingSpot(level, boardPlatformPos, from));
        }

        plan.setDisembarkPos(findPlatformWaitingSpot(level, lastTrainLegSameRoute.pos, lastTrainLegSameRoute.pos));
        plan.setStatus(CitizenTransitPlan.Status.READY_TO_BOARD);
        LOGGER.info(
            "[minecolonies_mtr_integration] Citizen {} routeId={} board={} disembark={}",
            citizenId,
            firstTrainLeg.routeId,
            plan.getBoardPos(),
            plan.getDisembarkPos()
        );

        if (level instanceof ServerLevel serverLevel) {
            final AbstractEntityCitizen citizen = getCitizenByUuid(serverLevel, citizenId);
            final Platform boardPlatform = RailwayData.getPlatformByPos(railwayData.platforms, plan.getBoardPos());
            notifyCitizenOnce(
                citizen,
                plan,
                "async_route_" + firstTrainLeg.routeId + "_" + plan.getBoardPos(),
                "NPC идет к " + (boardPlatform == null ? ("платформе @" + plan.getBoardPos()) : describePlatform(railwayData, boardPlatform)) + ", маршрут " + firstTrainLeg.routeId
            );
        }
    }

    private static BlockPos chooseBoardingPlatformMidPos(
        final RailwayData railwayData,
        final BlockPos from,
        final long routeId,
        final long destinationPlatformId
    ) {
        final Route route = railwayData.dataCache.routeIdMap.get(routeId);
        if (route == null || route.platformIds.isEmpty()) {
            return railwayData.dataCache.platformIdMap.values()
                .stream()
                .filter(platform -> platform.getMidPos().distManhattan(from) <= PLATFORM_SEARCH_RADIUS)
                .filter(platform -> platformHasRoute(railwayData, platform, routeId))
                .min(Comparator.comparingInt(platform -> platform.getMidPos().distManhattan(from)))
                .map(Platform::getMidPos)
                .orElse(null);
        }

        int destinationIndex = -1;
        for (int i = 0; i < route.platformIds.size(); i++) {
            if (route.platformIds.get(i).platformId == destinationPlatformId) {
                destinationIndex = i;
                break;
            }
        }

        Platform bestPlatform = null;
        int bestScore = Integer.MAX_VALUE;

        for (int i = 0; i < route.platformIds.size(); i++) {
            final long platformId = route.platformIds.get(i).platformId;
            final Platform platform = railwayData.dataCache.platformIdMap.get(platformId);
            if (platform == null) {
                continue;
            }

            if (platform.getMidPos().distManhattan(from) > PLATFORM_SEARCH_RADIUS) {
                continue;
            }

            if (!platformHasRoute(railwayData, platform, routeId)) {
                continue;
            }

            final int distanceScore = platform.getMidPos().distManhattan(from);
            int directionPenalty = 0;
            if (destinationIndex >= 0) {
                directionPenalty = forwardStops(i, destinationIndex, route.platformIds.size()) * 64;
            }

            final int score = distanceScore + directionPenalty;
            if (score < bestScore) {
                bestScore = score;
                bestPlatform = platform;
            }
        }

        return bestPlatform == null ? null : bestPlatform.getMidPos();
    }

    private static int forwardStops(final int fromIndex, final int toIndex, final int size) {
        if (size <= 0) {
            return 0;
        }
        if (toIndex >= fromIndex) {
            return toIndex - fromIndex;
        }
        return size - fromIndex + toIndex;
    }

    private static boolean platformHasRoute(final RailwayData railwayData, final Platform platform, final long routeId) {
        final List<ScheduleEntry> scheduleEntries = railwayData.getSchedulesAtPlatform(platform.id);
        if (scheduleEntries == null || scheduleEntries.isEmpty()) {
            return false;
        }

        for (final ScheduleEntry scheduleEntry : scheduleEntries) {
            if (scheduleEntry.routeId == routeId) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos findPlatformWaitingSpot(final Level level, final BlockPos railMid, final BlockPos preferred) {
        BlockPos best = railMid;
        int bestScore = Integer.MAX_VALUE;

        for (int dx = -WAITING_SPOT_RADIUS; dx <= WAITING_SPOT_RADIUS; dx++) {
            for (int dz = -WAITING_SPOT_RADIUS; dz <= WAITING_SPOT_RADIUS; dz++) {
                for (int dy = -2; dy <= 3; dy++) {
                    final BlockPos ground = railMid.offset(dx, dy, dz);
                    final BlockPos feet = ground.above();
                    final BlockPos head = feet.above();

                    final var groundState = level.getBlockState(ground);
                    final var feetState = level.getBlockState(feet);
                    final var headState = level.getBlockState(head);

                    if (groundState.isAir() || feetState.getBlock() instanceof BaseRailBlock || groundState.getBlock() instanceof BaseRailBlock) {
                        continue;
                    }

                    if (!feetState.isAir() || !headState.isAir()) {
                        continue;
                    }

                    if (!groundState.blocksMotion()) {
                        continue;
                    }

                    final int score = feet.distManhattan(railMid) + feet.distManhattan(preferred);
                    if (score < bestScore) {
                        bestScore = score;
                        best = feet;
                    }
                }
            }
        }

        return best;
    }

    public static AbstractEntityCitizen getCitizenByUuid(final ServerLevel level, final UUID uuid) {
        final var entity = level.getEntity(uuid);
        if (entity instanceof AbstractEntityCitizen citizen) {
            return citizen;
        }
        return null;
    }

    public static CitizenTransitPlan getPlan(final UUID citizenId) {
        return PLANS.get(citizenId);
    }

    public static String describePlatform(final RailwayData railwayData, final Platform platform) {
        final Station station = RailwayData.getStation(railwayData.stations, railwayData.dataCache, platform.getMidPos());
        final String stationName = station == null || station.name == null || station.name.isBlank() ? "Unknown Station" : station.name;
        final String platformName = platform.name == null || platform.name.isBlank() ? String.valueOf(platform.id) : platform.name;
        return "станции \"" + stationName + "\", платформа \"" + platformName + "\"";
    }

    public static void notifyCitizenOnce(
        final AbstractEntityCitizen citizen,
        final CitizenTransitPlan plan,
        final String marker,
        final String message
    ) {
        if (citizen == null || plan == null) {
            return;
        }

        if (marker.equals(plan.getLastDebugMarker())) {
            return;
        }
        plan.setLastDebugMarker(marker);

        if (!(citizen.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        final Component component = Component.literal("[MTR/NPC] " + citizen.getName().getString() + ": " + message);
        serverLevel.getServer().getPlayerList().getPlayers().forEach(player -> player.sendSystemMessage(component));
    }
}
