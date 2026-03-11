package com.mlkymc;

import com.mlkymc.config.MlkyConfig;
import com.mlkymc.dimension.DimensionCommand;
import com.mlkymc.dimension.DimensionListener;
import com.mlkymc.dimension.DimensionManager;
import com.mlkymc.economy.EconomyCommand;
import com.mlkymc.economy.MobDropListener;
import com.mlkymc.ghost.GhostCommand;
import com.mlkymc.ghost.GhostListener;
import com.mlkymc.ghost.GhostManager;
import com.mlkymc.revive.ReviveListener;
import com.mlkymc.revive.ReviveManager;
import com.mlkymc.shop.ShopCommand;
import com.mlkymc.shop.ShopListener;
import com.mlkymc.shop.ShopManager;
import com.mlkymc.skills.SkillCommand;
import com.mlkymc.skills.SkillListener;
import com.mlkymc.skills.SkillManager;
import com.mlkymc.storage.JsonStorage;
import com.mlkymc.twitch.StreamCommand;
import com.mlkymc.twitch.StreamListener;
import com.mlkymc.twitch.StreamerManager;
import com.mlkymc.twitch.TwitchConfig;
import com.mlkymc.twitch.TwitchRedemptionHandler;
import com.mlkymc.twitch.TwitchWebSocketClient;
import com.mlkymc.world.VillagerBlocker;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

@Mod(MlkyMC.MOD_ID)
public class MlkyMC {
    public static final String MOD_ID = "mlkymc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final Path configDir;

    // Storage
    private final JsonStorage storage;

    // Managers
    private final GhostManager ghostManager;
    private final ReviveManager reviveManager;
    private final SkillManager skillManager;
    private final DimensionManager dimensionManager;
    private final ShopManager shopManager;

    // Commands (instance-based)
    private final GhostCommand ghostCommand;
    private final DimensionCommand dimensionCommand;
    private final SkillCommand skillCommand;
    private final ShopCommand shopCommand;

    // Twitch
    private final TwitchConfig twitchConfig;
    private final TwitchRedemptionHandler twitchHandler;
    private TwitchWebSocketClient twitchClient;

    // Streams
    private final StreamerManager streamerManager;
    private final StreamCommand streamCommand;

    public MlkyMC(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("mlkyMC initializing...");

        configDir = FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
        configDir.toFile().mkdirs();

        // Load config
        MlkyConfig.init(configDir);

        // Initialize storage
        storage = new JsonStorage(configDir);
        storage.load();

        // Initialize managers
        ghostManager = new GhostManager(storage);
        reviveManager = new ReviveManager(ghostManager, storage);
        skillManager = new SkillManager(configDir);
        dimensionManager = new DimensionManager(configDir);
        shopManager = new ShopManager(configDir);

        // Initialize commands
        ghostCommand = new GhostCommand(ghostManager, reviveManager);
        dimensionCommand = new DimensionCommand(dimensionManager);
        skillCommand = new SkillCommand(skillManager);
        shopCommand = new ShopCommand(shopManager);

        // Twitch
        twitchConfig = new TwitchConfig();
        twitchHandler = new TwitchRedemptionHandler(ghostManager, reviveManager);

        // Streams
        streamerManager = new StreamerManager(configDir);
        streamCommand = new StreamCommand(streamerManager);

        // Register event listeners on the NeoForge event bus
        IEventBus forgeBus = NeoForge.EVENT_BUS;
        forgeBus.register(this);
        forgeBus.register(new GhostListener(ghostManager));
        forgeBus.register(new ReviveListener(ghostManager, reviveManager));
        forgeBus.register(new MobDropListener(skillManager));
        forgeBus.register(new SkillListener(skillManager));
        forgeBus.register(new DimensionListener(dimensionManager));
        forgeBus.register(new ShopListener(shopManager));
        forgeBus.register(new StreamListener(streamerManager));

        if (MlkyConfig.getDisableVillagerSpawning()) {
            forgeBus.register(new VillagerBlocker());
        }

        LOGGER.info("mlkyMC initialized!");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        EconomyCommand.register(dispatcher);
        ghostCommand.register(dispatcher);
        dimensionCommand.register(dispatcher);
        skillCommand.register(dispatcher);
        shopCommand.register(dispatcher);
        streamCommand.register(dispatcher);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        var server = event.getServer();

        ghostManager.setServer(server);
        shopManager.setServer(server);
        twitchHandler.setServer(server);

        // Spawn shopkeeper villagers
        shopManager.spawnAllVillagers();

        // Start stream status polling
        streamerManager.setServer(server);
        streamerManager.start();

        // Start Twitch integration if configured
        if (twitchConfig.isEnabled()) {
            twitchClient = new TwitchWebSocketClient(twitchConfig, twitchHandler);
            twitchClient.connect();
            LOGGER.info("Twitch integration started");
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // Save all data
        storage.save();
        skillManager.save();
        dimensionManager.save();
        shopManager.save();
        shopManager.despawnAll();

        // Stop stream polling
        streamerManager.stop();
        streamerManager.save();

        // Disconnect Twitch
        if (twitchClient != null) {
            twitchClient.disconnect();
            twitchClient = null;
        }

        LOGGER.info("mlkyMC shutdown complete.");
    }
}
