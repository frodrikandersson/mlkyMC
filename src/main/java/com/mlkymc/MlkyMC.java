package com.mlkymc;

import com.mlkymc.classes.ClassCommand;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import com.mlkymc.classes.ClassExpHandler;
import com.mlkymc.classes.ClassManager;
import com.mlkymc.classes.DebuffHandler;
import com.mlkymc.config.MlkyConfig;
import com.mlkymc.registry.ModCreativeTabs;
import com.mlkymc.registry.ModItems;
import com.mlkymc.dimension.DimensionCommand;
import com.mlkymc.dimension.DimensionListener;
import com.mlkymc.dimension.DimensionManager;
import com.mlkymc.economy.EconomyCommand;
import com.mlkymc.economy.MobDropListener;
import com.mlkymc.economy.WalletListener;
import com.mlkymc.ghost.GhostCommand;
import com.mlkymc.ghost.GhostListener;
import com.mlkymc.ghost.GhostManager;
import com.mlkymc.revive.ReviveListener;
import com.mlkymc.revive.ReviveManager;
import com.mlkymc.shop.ShopCommand;
import com.mlkymc.shop.ShopListener;
import com.mlkymc.shop.ShopManager;
import com.mlkymc.storage.JsonStorage;
import com.mlkymc.twitch.StreamCommand;
import com.mlkymc.twitch.StreamListener;
import com.mlkymc.twitch.StreamerManager;
import com.mlkymc.twitch.TwitchConfig;
import com.mlkymc.twitch.TwitchRedemptionHandler;
import com.mlkymc.twitch.TwitchWebSocketClient;
import com.mlkymc.market.MarketCommand;
import com.mlkymc.market.MarketListener;
import com.mlkymc.market.MarketManager;
import com.mlkymc.region.RegionCommand;
import com.mlkymc.region.RegionListener;
import com.mlkymc.region.RegionManager;
import com.mlkymc.spawner.SpawnerAgingListener;
import com.mlkymc.spawner.SpawnerAgingManager;
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
    private final ClassManager classManager;
    private final GhostManager ghostManager;
    private final ReviveManager reviveManager;
    private final DimensionManager dimensionManager;
    private final ShopManager shopManager;
    private final SpawnerAgingManager spawnerAgingManager;
    private final RegionManager regionManager;
    private final MarketManager marketManager;
    private final com.mlkymc.grave.GraveManager graveManager;
    private static com.mlkymc.grave.GraveManager graveManagerInstance;
    private com.mlkymc.classes.ActiveSkillHandler activeSkillHandler;
    private com.mlkymc.classes.PassiveSkillHandler passiveSkillHandler;

    // Commands (instance-based)
    private final ClassCommand classCommand;
    private final GhostCommand ghostCommand;
    private final DimensionCommand dimensionCommand;
    private final ShopCommand shopCommand;
    private final RegionCommand regionCommand;
    private final MarketCommand marketCommand;

    // Twitch
    private final TwitchConfig twitchConfig;
    private final TwitchRedemptionHandler twitchHandler;
    private TwitchWebSocketClient twitchClient;

    // Streams
    private final StreamerManager streamerManager;
    private final StreamCommand streamCommand;

    public MlkyMC(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("mlkyMC initializing...");

        // Register blocks, items, entities, creative tabs, and keybinds on the mod event bus
        com.mlkymc.registry.ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        com.mlkymc.registry.ModEntities.ENTITIES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.addListener(com.mlkymc.client.ModKeybinds::registerKeys);
            modEventBus.addListener(com.mlkymc.client.ClientEntityRenderers::register);
        }

        configDir = FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
        configDir.toFile().mkdirs();

        // Load config
        MlkyConfig.init(configDir);

        // Initialize storage
        storage = new JsonStorage(configDir);
        storage.load();

        // Initialize managers
        classManager = new ClassManager(configDir);
        ghostManager = new GhostManager(storage);
        reviveManager = new ReviveManager(ghostManager, storage);
        dimensionManager = new DimensionManager(configDir);
        shopManager = new ShopManager(configDir);
        spawnerAgingManager = new SpawnerAgingManager(configDir);
        regionManager = new RegionManager(configDir);
        marketManager = new MarketManager(configDir);
        graveManager = new com.mlkymc.grave.GraveManager();
        graveManagerInstance = graveManager;

        // Initialize commands
        classCommand = new ClassCommand(classManager);
        ghostCommand = new GhostCommand(ghostManager, reviveManager);
        dimensionCommand = new DimensionCommand(dimensionManager);
        shopCommand = new ShopCommand(shopManager);
        regionCommand = new RegionCommand(regionManager);
        marketCommand = new MarketCommand(marketManager);

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
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            forgeBus.register(new com.mlkymc.client.ModKeybinds());
            forgeBus.register(new com.mlkymc.client.ClientSyncHandler());
            forgeBus.register(new com.mlkymc.client.MinimapHud());
            forgeBus.register(new com.mlkymc.registry.ItemTooltipHandler());
        }
        forgeBus.register(new MobDropListener());
        forgeBus.register(new DebuffHandler(classManager));
        forgeBus.register(new ClassExpHandler(classManager));
        passiveSkillHandler = new com.mlkymc.classes.PassiveSkillHandler(classManager);
        forgeBus.register(passiveSkillHandler);
        activeSkillHandler = new com.mlkymc.classes.ActiveSkillHandler(classManager);
        activeSkillHandler.setGraveManager(graveManager);
        forgeBus.register(activeSkillHandler);
        forgeBus.register(new com.mlkymc.classes.SpecialEffectHandler(classManager));
        forgeBus.register(new com.mlkymc.classes.CraftRestrictionHandler(classManager));
        forgeBus.register(new com.mlkymc.classes.TrophyBuffHandler());
        forgeBus.register(new com.mlkymc.classes.ItemFunctionHandler());
        forgeBus.register(new com.mlkymc.classes.ClassLoginHandler(classManager));
        forgeBus.register(new DimensionListener(dimensionManager));
        forgeBus.register(new ShopListener(shopManager));
        forgeBus.register(new SpawnerAgingListener(spawnerAgingManager));
        forgeBus.register(new RegionListener(regionManager));
        forgeBus.register(new MarketListener(marketManager));
        forgeBus.register(new WalletListener());
        forgeBus.register(new StreamListener(streamerManager));
        forgeBus.register(new com.mlkymc.grave.GraveListener(graveManager));
        forgeBus.register(new com.mlkymc.world.BellRaidListener());
        forgeBus.register(new com.mlkymc.world.AntiExploitListener());
        forgeBus.register(new com.mlkymc.world.ElytraRemover());
        forgeBus.register(new com.mlkymc.world.SpawnerHandler());

        if (MlkyConfig.getDisableVillagerSpawning()) {
            forgeBus.register(new VillagerBlocker());
        }

        LOGGER.info("mlkyMC initialized!");
    }

    public static com.mlkymc.grave.GraveManager getGraveManager() {
        return graveManagerInstance;
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        EconomyCommand.register(dispatcher);
        classCommand.register(dispatcher);
        ghostCommand.register(dispatcher);
        dimensionCommand.register(dispatcher);
        shopCommand.register(dispatcher);
        streamCommand.register(dispatcher);
        regionCommand.register(dispatcher);
        marketCommand.register(dispatcher);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        var server = event.getServer();

        ghostManager.setServer(server);
        shopManager.setServer(server);
        marketManager.setServer(server);
        twitchHandler.setServer(server);

        // Spawn shopkeeper villagers
        shopManager.spawnAllVillagers();

        // Spawn market vendors and player stalls
        marketManager.spawnAllVillagers();

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
        classManager.save();
        dimensionManager.save();
        shopManager.save();
        shopManager.despawnAll();
        spawnerAgingManager.save();
        regionManager.save();
        marketManager.save();
        marketManager.despawnAll();

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

    @SubscribeEvent
    public void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        com.mlkymc.registry.GrapplingHookItem.tickHooks(event.getLevel());
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
            com.mlkymc.registry.ScarecrowBlock.tickCropBoost(sl);
        }
        if (activeSkillHandler != null) {
            activeSkillHandler.tickNurtureFields(event.getLevel());
            activeSkillHandler.tickAnimalFollowing(event.getLevel());
        }
        if (passiveSkillHandler != null) {
            passiveSkillHandler.tickLavaFishing(event.getLevel());
        }
    }

    @SubscribeEvent
    public void onMobSpawnScarecrow(net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent event) {
        if (event.getLevel().isClientSide()) return;
        // Only block hostile mobs
        if (!(event.getEntity() instanceof net.minecraft.world.entity.monster.Monster)) return;
        if (com.mlkymc.registry.ScarecrowBlock.isProtectedByScarecrow(event.getEntity().blockPosition())) {
            event.setSpawnCancelled(true);
        }
    }
}
