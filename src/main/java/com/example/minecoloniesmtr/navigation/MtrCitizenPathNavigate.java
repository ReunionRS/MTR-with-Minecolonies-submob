package com.example.minecoloniesmtr.navigation;

import com.example.minecoloniesmtr.transit.TransitPlanManager;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.pathjobs.AbstractPathJob;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobMoveCloseToXNearY;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobMoveToLocation;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

public class MtrCitizenPathNavigate extends MinecoloniesAdvancedPathNavigate {

    public MtrCitizenPathNavigate(final Mob entity, final Level world) {
        super(entity, world);
    }

    @Override
    protected PathResult<PathJobMoveToLocation> walkTo(final BlockPos target, final double speedFactor, final boolean safeDestination) {
        final BlockPos resolvedTarget = TransitPlanManager.resolveTransitTarget(ourEntity, target);
        return super.walkTo(resolvedTarget, speedFactor, safeDestination);
    }

    @Override
    protected PathResult<AbstractPathJob> walkTowards(final BlockPos target, final double range, final double speedFactor) {
        final BlockPos resolvedTarget = TransitPlanManager.resolveTransitTarget(ourEntity, target);
        return super.walkTowards(resolvedTarget, range, speedFactor);
    }

    @Override
    public boolean walkTo(final BlockPos target, final double speedFactor) {
        final BlockPos resolvedTarget = TransitPlanManager.resolveTransitTarget(ourEntity, target);
        return super.walkTo(resolvedTarget, speedFactor);
    }

    @Override
    protected PathResult<PathJobMoveCloseToXNearY> walkCloseToXNearY(
        final BlockPos target,
        final BlockPos startRestriction,
        final int range,
        final double speedFactor,
        final boolean safeDestination
    ) {
        final BlockPos resolvedTarget = TransitPlanManager.resolveTransitTarget(ourEntity, target);
        return super.walkCloseToXNearY(resolvedTarget, startRestriction, range, speedFactor, safeDestination);
    }
}
