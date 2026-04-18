package com.example.minecoloniesmtr;

import com.example.minecoloniesmtr.navigation.MtrCitizenPathNavigate;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.pathfinding.registry.IPathNavigateRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;

import java.lang.reflect.Field;

@Mod(MinecoloniesMtrIntegrationMod.MOD_ID)
public class MinecoloniesMtrIntegrationMod {

    public static final String MOD_ID = "minecolonies_mtr_integration";
    private static final String BUILD_MARKER = "diag-chat-v2";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Field PATH_NAV_FIELD;

    static {
        Field field = null;
        try {
            field = AbstractEntityCitizen.class.getDeclaredField("pathNavigate");
            field.setAccessible(true);
        } catch (final ReflectiveOperationException ex) {
            LOGGER.error("[minecolonies_mtr_integration] Failed to resolve citizen pathNavigate field", ex);
        }
        PATH_NAV_FIELD = field;
    }

    public MinecoloniesMtrIntegrationMod() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(this::registerCustomNavigatorFactory);
    }

    @SubscribeEvent
    public void onServerAboutToStart(final ServerAboutToStartEvent event) {
        registerCustomNavigatorFactory();
    }

    @SubscribeEvent
    public void onEntityJoinLevel(final EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        final Entity entity = event.getEntity();
        if (entity instanceof AbstractEntityCitizen citizen) {
            ensureCustomNavigator(citizen);
        }
    }

    private void registerCustomNavigatorFactory() {
        IPathNavigateRegistry.getInstance().registerNewPathNavigate(
            mob -> mob instanceof AbstractEntityCitizen,
            mob -> new MtrCitizenPathNavigate(mob, mob.level())
        );
        LOGGER.info("[minecolonies_mtr_integration] Registered custom citizen navigator ({})", BUILD_MARKER);
    }

    private static void ensureCustomNavigator(final AbstractEntityCitizen citizen) {
        if (citizen.getNavigation() instanceof MtrCitizenPathNavigate) {
            return;
        }

        if (PATH_NAV_FIELD == null) {
            return;
        }

        try {
            PATH_NAV_FIELD.set(citizen, null);
            citizen.getNavigation();
            if (citizen.getNavigation() instanceof MtrCitizenPathNavigate) {
                LOGGER.info("[minecolonies_mtr_integration] Replaced navigator for citizen {}", citizen.getUUID());
            }
        } catch (final IllegalAccessException ex) {
            LOGGER.error("[minecolonies_mtr_integration] Failed to replace navigator for citizen {}", citizen.getUUID(), ex);
        }
    }
}
