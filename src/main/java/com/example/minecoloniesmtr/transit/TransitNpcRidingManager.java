package com.example.minecoloniesmtr.transit;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class TransitNpcRidingManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<Long, Map<UUID, RiderState>> RIDERS_BY_TRAIN = new HashMap<>();

    private TransitNpcRidingManager() {
    }

    public static void tickTrainCar(
        final ServerLevel world,
        final long trainId,
        final long routeId,
        final double carX,
        final double carY,
        final double carZ,
        final float carYaw,
        final boolean doorsOpen,
        final boolean canMount
    ) {
        if (canMount && doorsOpen) {
            tryBoard(world, trainId, routeId, carX, carY, carZ, carYaw);
        }

        moveAndDismount(world, trainId, routeId, carX, carY, carZ, carYaw, doorsOpen);
    }

    private static void tryBoard(
        final ServerLevel world,
        final long trainId,
        final long routeId,
        final double carX,
        final double carY,
        final double carZ,
        final float carYaw
    ) {
        final double range = 20.0D;
        final AABB box = new AABB(carX - range, carY - 3, carZ - range, carX + range, carY + 4, carZ + range);

        world.getEntitiesOfClass(AbstractEntityCitizen.class, box, citizen -> citizen.isAlive() && !citizen.isPassenger()).forEach(citizen -> {
            if (!TransitPlanManager.canBoard(citizen, routeId, carX, carY, carZ)) {
                return;
            }

            final Map<UUID, RiderState> riders = RIDERS_BY_TRAIN.computeIfAbsent(trainId, id -> new HashMap<>());
            if (!riders.containsKey(citizen.getUUID())) {
                final float offsetX = (citizen.getRandom().nextFloat() - 0.5F) * 0.9F;
                final float offsetZ = (citizen.getRandom().nextFloat() - 0.5F) * 1.2F;
                riders.put(citizen.getUUID(), new RiderState(offsetX, offsetZ));
                TransitPlanManager.markBoarded(citizen, trainId);
                citizen.getNavigation().stop();
                final CitizenTransitPlan plan = TransitPlanManager.getPlan(citizen.getUUID());
                TransitPlanManager.notifyCitizenOnce(
                    citizen,
                    plan,
                    "boarded_" + trainId + "_" + routeId,
                    "сел в поезд (маршрут " + routeId + ", поезд " + trainId + ")"
                );
                LOGGER.info(
                    "[minecolonies_mtr_integration] Citizen {} boarded trainId={} routeId={} at {}",
                    citizen.getUUID(),
                    trainId,
                    routeId,
                    citizen.blockPosition()
                );
            }
        });
    }

    private static void moveAndDismount(
        final ServerLevel world,
        final long trainId,
        final long routeId,
        final double carX,
        final double carY,
        final double carZ,
        final float carYaw,
        final boolean doorsOpen
    ) {
        final Map<UUID, RiderState> riders = RIDERS_BY_TRAIN.get(trainId);
        if (riders == null || riders.isEmpty()) {
            return;
        }

        final Iterator<Map.Entry<UUID, RiderState>> iterator = riders.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<UUID, RiderState> entry = iterator.next();
            final AbstractEntityCitizen citizen = TransitPlanManager.getCitizenByUuid(world, entry.getKey());
            if (citizen == null || !citizen.isAlive()) {
                iterator.remove();
                continue;
            }

            final RiderState riderState = entry.getValue();
            final double yawRad = Math.toRadians(-carYaw);
            final double rotatedX = riderState.offsetX * Mth.cos((float) yawRad) - riderState.offsetZ * Mth.sin((float) yawRad);
            final double rotatedZ = riderState.offsetX * Mth.sin((float) yawRad) + riderState.offsetZ * Mth.cos((float) yawRad);

            citizen.setDeltaMovement(0, 0, 0);
            citizen.fallDistance = 0;
            citizen.teleportTo(carX + rotatedX, carY + 0.15D, carZ + rotatedZ);

            if (doorsOpen && TransitPlanManager.shouldDisembark(citizen, routeId, BlockPos.containing(carX, carY, carZ))) {
                final double exitX = carX + Mth.cos((float) yawRad) * 1.4D;
                final double exitZ = carZ - Mth.sin((float) yawRad) * 1.4D;
                citizen.teleportTo(exitX, carY, exitZ);
                TransitPlanManager.markDisembarked(citizen);
                final CitizenTransitPlan plan = TransitPlanManager.getPlan(citizen.getUUID());
                TransitPlanManager.notifyCitizenOnce(
                    citizen,
                    plan,
                    "disembarked_" + trainId + "_" + routeId,
                    "вышел из поезда на маршруте " + routeId
                );
                LOGGER.info(
                    "[minecolonies_mtr_integration] Citizen {} disembarked trainId={} routeId={} near {}",
                    citizen.getUUID(),
                    trainId,
                    routeId,
                    BlockPos.containing(exitX, carY, exitZ)
                );
                iterator.remove();
            }
        }

        if (riders.isEmpty()) {
            RIDERS_BY_TRAIN.remove(trainId);
        }
    }

    private static class RiderState {
        private final float offsetX;
        private final float offsetZ;

        private RiderState(final float offsetX, final float offsetZ) {
            this.offsetX = offsetX;
            this.offsetZ = offsetZ;
        }
    }
}
