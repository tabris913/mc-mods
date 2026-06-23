package com.tosshie.rbd;

import com.tosshie.rbd.command.GenerateCommand;
import com.tosshie.rbd.config.RBDConfig;
import com.tosshie.rbd.event.AttackLogHandler;
import com.tosshie.rbd.event.LootTableHandler;
import com.tosshie.rbd.event.MobSpawnHandler;
import com.tosshie.rbd.event.MobStrengthHandler;
import com.tosshie.rbd.event.SpawnDimensionHandler;
import com.tosshie.rbd.event.SunlightHandler;
import com.tosshie.rbd.event.WorldBorderHandler;
import com.tosshie.rbd.portal.PortalHandler;
import com.tosshie.rbd.portal.PortalTeleportHandler;
import com.tosshie.rbd.util.LootTableFileManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(RandomBiomeDimensions.MOD_ID)
public class RandomBiomeDimensions {

    public static final String MOD_ID = "randombimedimensions";
    public static final Logger LOGGER = LogManager.getLogger();

    public RandomBiomeDimensions() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, RBDConfig.SPEC);
        LootTableFileManager.getOrCreateLootTableFile();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new MobStrengthHandler());
        MinecraftForge.EVENT_BUS.register(new LootTableHandler());
        MinecraftForge.EVENT_BUS.register(new SunlightHandler());
        MinecraftForge.EVENT_BUS.register(new MobSpawnHandler());
        MinecraftForge.EVENT_BUS.register(new PortalHandler());
        MinecraftForge.EVENT_BUS.register(new PortalTeleportHandler());
        MinecraftForge.EVENT_BUS.register(new WorldBorderHandler());
        MinecraftForge.EVENT_BUS.register(new SpawnDimensionHandler());
        MinecraftForge.EVENT_BUS.register(new AttackLogHandler());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        GenerateCommand.register(event.getDispatcher());
    }
}
