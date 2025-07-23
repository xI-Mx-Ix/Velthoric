package net.xmx.xbullet.init;

import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.xmx.xbullet.init.registry.CommandRegistry;
import net.xmx.xbullet.init.registry.ModRegistries;
import net.xmx.xbullet.network.NetworkHandler;
import net.xmx.xbullet.builtin.BuiltInPhysicsRegistry;
import net.xmx.xbullet.natives.NativeJoltInitializer;
import net.xmx.xbullet.physics.constraint.serializer.registry.ConstraintSerializerRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(XBullet.MODID)
public class XBullet {
    public static final String MODID = "xbullet";
    public static final Logger LOGGER = LogManager.getLogger();

    public static final IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
    public static final IEventBus forgeEventBus = MinecraftForge.EVENT_BUS;

    private static volatile XBullet instance;

    public XBullet() {

        if (instance != null) {
            throw new IllegalStateException("XBullet has already been instantiated!");
        }
        instance = this;

        ModRegistries.register(eventBus);
        RegisterEvents.register(eventBus, forgeEventBus);

        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
        eventBus.addListener(this::commonSetup);
        eventBus.addListener(this::clientSetup);

    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BuiltInPhysicsRegistry.register();
            ConstraintSerializerRegistry.registerDefaults();
            NetworkHandler.register();

            try {
                NativeJoltInitializer.initialize();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {

            RegisterEvents.registerClient(forgeEventBus, MinecraftForge.EVENT_BUS);
            BuiltInPhysicsRegistry.registerClientRenderers();
        });
    }

    public static XBullet getInstance() {
        XBullet localInstance = instance;
        if (localInstance == null) {

            synchronized (XBullet.class) {
                localInstance = instance;
                if (localInstance == null) {
                    LOGGER.error("XBullet instance is null! This should not happen.");
                    throw new IllegalStateException("XBullet instance not initialized!");
                }
            }
        }
        return localInstance;
    }



    private void onRegisterCommands(RegisterCommandsEvent event) {
        CommandRegistry.registerCommon(event.getDispatcher());
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandRegistry.registerClient(event.getDispatcher());
    }
}