package com.example.minecoloniesmtr.mixin;

import com.example.minecoloniesmtr.transit.TransitNpcRidingManager;
import com.mojang.logging.LogUtils;
import mtr.data.NameColorDataBase;
import mtr.data.TrainServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrainServer.class)
public abstract class TrainServerMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static long lastDebugLogMillis = 0L;
    @Inject(method = "simulateCar", at = @At("TAIL"), remap = false)
    private void minecoloniesMtrIntegration$simulateCitizenTransit(
        final Level world,
        final int ridingCar,
        final float ticksElapsed,
        final double carX,
        final double carY,
        final double carZ,
        final float carYaw,
        final float carPitch,
        final double prevCarX,
        final double prevCarY,
        final double prevCarZ,
        final float prevCarYaw,
        final float prevCarPitch,
        final boolean doorLeftOpen,
        final boolean doorRightOpen,
        final double realSpacing,
        final CallbackInfo ci
    ) {
        if (!(world instanceof ServerLevel serverLevel)) {
            return;
        }

        final TrainServer self = (TrainServer) (Object) this;
        final long routeId = readLongField(self, "routeId");
        final boolean doorsOpen = doorLeftOpen || doorRightOpen;
        final boolean canMount = self.isManualAllowed || doorsOpen;

        final long now = System.currentTimeMillis();
        if (now - lastDebugLogMillis > 5000L && doorsOpen) {
            lastDebugLogMillis = now;
            LOGGER.info(
                "[minecolonies_mtr_integration] Train car tick trainId={} routeId={} doorsOpen={} car=({}, {}, {})",
                ((NameColorDataBase) self).id,
                routeId,
                doorsOpen,
                Math.round(carX),
                Math.round(carY),
                Math.round(carZ)
            );
        }

        TransitNpcRidingManager.tickTrainCar(
            serverLevel,
            ((NameColorDataBase) self).id,
            routeId,
            carX,
            carY,
            carZ,
            carYaw,
            doorsOpen,
            canMount
        );
    }

    private static long readLongField(final Object target, final String name) {
        try {
            final var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getLong(target);
        } catch (final ReflectiveOperationException e) {
            return 0L;
        }
    }
}
