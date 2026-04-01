package com.mlkymc;

import com.mlkymc.classes.ClassCommand;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import com.mlkymc.classes.ClassExpHandler;
import com.mlkymc.classes.ClassManager;
import com.mlkymc.classes.ItemBaseValues;
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
    private SpawnerAgingListener spawnerAgingListener;
    private final RegionManager regionManager;
    private final MarketManager marketManager;
    private final com.mlkymc.grave.GraveManager graveManager;
    private static com.mlkymc.grave.GraveManager graveManagerInstance;
    private com.mlkymc.classes.ActiveSkillHandler activeSkillHandler;
    private com.mlkymc.classes.PassiveSkillHandler passiveSkillHandler;
    private com.mlkymc.classes.PowerHandler powerHandler;
    private final com.mlkymc.altar.SoulAltarManager soulAltarManager;
    private final com.mlkymc.ghost.GhostDataManager ghostDataManager;
    private static ClassManager classManagerInstance;
    private static com.mlkymc.altar.SoulAltarManager soulAltarManagerInstance;
    private static com.mlkymc.ghost.GhostDataManager ghostDataManagerInstance;
    private static com.mlkymc.classes.PassiveSkillHandler passiveSkillHandlerInstance;

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

        // Initialize storage (data will be loaded on server start from world folder)
        storage = new JsonStorage(configDir);
        // Don't load here — reload() in onServerStarting will load from the world folder

        // Initialize managers (data loaded on server start via reload())
        classManager = new ClassManager(configDir);
        ghostManager = new GhostManager(storage);
        reviveManager = new ReviveManager(ghostManager, storage);
        reviveManagerInstance = reviveManager;
        dimensionManager = new DimensionManager(configDir);
        shopManager = new ShopManager(configDir);
        spawnerAgingManager = new SpawnerAgingManager(configDir);
        regionManager = new RegionManager(configDir);
        marketManager = new MarketManager(configDir);
        graveManager = new com.mlkymc.grave.GraveManager();
        soulAltarManager = new com.mlkymc.altar.SoulAltarManager(configDir);
        ghostDataManager = new com.mlkymc.ghost.GhostDataManager(configDir);
        graveManagerInstance = graveManager;
        ghostManagerInstance = ghostManager;
        classManagerInstance = classManager;
        soulAltarManagerInstance = soulAltarManager;
        ghostDataManagerInstance = ghostDataManager;

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
        forgeBus.register(new com.mlkymc.pvp.PvPTagManager());
        var rl = new ReviveListener(ghostManager, reviveManager, classManager, graveManager);
        reviveListenerInstance = rl;
        forgeBus.register(rl);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            forgeBus.register(new com.mlkymc.client.ModKeybinds());
            forgeBus.register(new com.mlkymc.client.ClientSyncHandler());
            forgeBus.register(new com.mlkymc.client.MinimapHud());
            forgeBus.register(new com.mlkymc.client.PowerChargeHud());
            forgeBus.register(new com.mlkymc.client.ResurrectionCountdownHud());
            forgeBus.register(new com.mlkymc.client.DevotedLifeHud());
            forgeBus.register(new com.mlkymc.client.SoulEnergyHud());
            forgeBus.register(new com.mlkymc.client.SpectralEnergyHud());
            forgeBus.register(new com.mlkymc.registry.ItemTooltipHandler());
            forgeBus.register(new com.mlkymc.client.BlockOwnerRenderer());
        }
        forgeBus.register(new MobDropListener());
        forgeBus.register(new DebuffHandler(classManager));
        forgeBus.register(new ClassExpHandler(classManager));
        passiveSkillHandler = new com.mlkymc.classes.PassiveSkillHandler(classManager);
        passiveSkillHandlerInstance = passiveSkillHandler;
        forgeBus.register(passiveSkillHandler);
        activeSkillHandler = new com.mlkymc.classes.ActiveSkillHandler(classManager);
        activeSkillHandlerInstance = activeSkillHandler;
        forgeBus.register(activeSkillHandler);
        forgeBus.register(new com.mlkymc.classes.SpecialEffectHandler(classManager));
        forgeBus.register(new com.mlkymc.classes.CraftRestrictionHandler(classManager));
        powerHandler = new com.mlkymc.classes.PowerHandler(classManager);
        powerHandler.setGraveManager(graveManager);
        forgeBus.register(powerHandler);
        forgeBus.register(new com.mlkymc.classes.TrophyBuffHandler());
        forgeBus.register(new com.mlkymc.classes.ItemFunctionHandler());
        forgeBus.register(new com.mlkymc.classes.ClassLoginHandler(classManager));
        forgeBus.register(new com.mlkymc.classes.IngredientBuffHandler(classManager));
        forgeBus.register(new com.mlkymc.classes.IngredientBuffApplier());
        forgeBus.register(new com.mlkymc.classes.SmithGambleListener(classManager));
        forgeBus.register(new DimensionListener(dimensionManager));
        forgeBus.register(new ShopListener(shopManager));
        spawnerAgingListener = new SpawnerAgingListener(spawnerAgingManager);
        forgeBus.register(spawnerAgingListener);
        forgeBus.register(new RegionListener(regionManager));
        forgeBus.register(new MarketListener(marketManager));
        forgeBus.register(new WalletListener());
        forgeBus.register(new StreamListener(streamerManager));
        forgeBus.register(new com.mlkymc.grave.GraveListener(graveManager));
        forgeBus.register(new com.mlkymc.altar.SoulAltarListener());
        forgeBus.register(new com.mlkymc.world.BellRaidListener());
        forgeBus.register(new com.mlkymc.world.AntiExploitListener());
        forgeBus.register(new com.mlkymc.world.ElytraRemover());
        forgeBus.register(new com.mlkymc.world.SpawnerHandler());

        if (MlkyConfig.getDisableVillagerSpawning()) {
            forgeBus.register(new VillagerBlocker());
        }

        forgeBus.register(new com.mlkymc.world.DragonEggHandler());

        LOGGER.info("mlkyMC initialized!");
    }

    public static com.mlkymc.grave.GraveManager getGraveManager() {
        return graveManagerInstance;
    }

    private static com.mlkymc.ghost.GhostManager ghostManagerInstance;
    public static com.mlkymc.ghost.GhostManager getGhostManager() {
        return ghostManagerInstance;
    }

    public static ClassManager getClassManager() {
        return classManagerInstance;
    }

    public static com.mlkymc.altar.SoulAltarManager getSoulAltarManager() {
        return soulAltarManagerInstance;
    }

    public static com.mlkymc.ghost.GhostDataManager getGhostDataManager() {
        return ghostDataManagerInstance;
    }

    private static com.mlkymc.revive.ReviveManager reviveManagerInstance;
    public static com.mlkymc.revive.ReviveManager getReviveManager() {
        return reviveManagerInstance;
    }

    private static com.mlkymc.revive.ReviveListener reviveListenerInstance;
    public static com.mlkymc.revive.ReviveListener getReviveListener() {
        return reviveListenerInstance;
    }

    public static com.mlkymc.classes.PassiveSkillHandler getPassiveSkillHandler() {
        return passiveSkillHandlerInstance;
    }

    private static com.mlkymc.classes.ActiveSkillHandler activeSkillHandlerInstance;
    private final java.util.Map<java.util.UUID, String> lastBlockOwnerMsg = new java.util.HashMap<>();
    public static com.mlkymc.classes.ActiveSkillHandler getActiveSkillHandler() {
        return activeSkillHandlerInstance;
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

        // Initialize item base values (must be after registries are frozen)
        ItemBaseValues.init();

        // World-specific data goes in the world save folder, not global config
        Path worldDataDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("mlkymc");
        worldDataDir.toFile().mkdirs();

        // Relocate world-specific managers to use world save folder
        classManager.reload(worldDataDir);
        storage.reload(worldDataDir);
        dimensionManager.reload(worldDataDir);
        spawnerAgingManager.reload(worldDataDir);
        regionManager.reload(worldDataDir);
        marketManager.reload(worldDataDir);
        soulAltarManager.reload(worldDataDir);
        ghostDataManager.reload(worldDataDir);

        ghostManager.setServer(server);
        graveManager.setServer(server);
        graveManager.reload(worldDataDir);
        com.mlkymc.ghost.GhostListener.setPendingGhostsFile(worldDataDir.resolve("pending_ghosts.json"));
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
        soulAltarManager.save();
        ghostDataManager.save();

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

    // Performance profiling — log slow ticks (cumulative ns over 30s window)
    private final long[] tickTimings = new long[12];
    private boolean checkedExpiredSpawners = false;

    @SubscribeEvent
    public void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        var level = event.getLevel();
        if (level.isClientSide()) return;
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) return;

        long gameTime = sl.getGameTime();

        // One-time check for expired spawners after server starts (wait 100 ticks for chunks to load)
        if (!checkedExpiredSpawners && gameTime > 100
                && sl.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            checkedExpiredSpawners = true;
            spawnerAgingManager.checkExpiredSpawners(sl.getServer());
        }
        long t0;

        // Every-tick handlers (only if active state exists)
        t0 = System.nanoTime();
        com.mlkymc.registry.GrapplingHookItem.tickHooks(level);
        if (powerHandler != null) {
            powerHandler.tickJackhammer(level);
            powerHandler.tickCharmedMobs(level);
        }
        // Sync mimic mob positions (overworld only to avoid duplicate ticks)
        if (sl.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            ghostDataManager.tickMimicEntities(sl.getServer());
        }
        // (spawner depletion is handled directly in SpawnerAgingListener.onMobSpawn)
        tickTimings[0] += System.nanoTime() - t0;

        // Every 2 ticks
        if (gameTime % 2 == 0) {
            if (powerHandler != null) {
                t0 = System.nanoTime();
                powerHandler.tickNaturesCall(level);
                tickTimings[8] += System.nanoTime() - t0;

                t0 = System.nanoTime();
                powerHandler.tickAutoSmelt(level);
                tickTimings[9] += System.nanoTime() - t0;
            }
            if (passiveSkillHandler != null) {
                t0 = System.nanoTime();
                passiveSkillHandler.tickLavaFishing(level);
                tickTimings[10] += System.nanoTime() - t0;
            }
        }

        // Every 10 ticks (0.5 seconds)
        if (gameTime % 10 == 0) {
            t0 = System.nanoTime();
            com.mlkymc.registry.ScarecrowBlock.tickCropBoost(sl);
            if (activeSkillHandler != null) {
                activeSkillHandler.tickAnimalFollowing(level);
            }
            if (passiveSkillHandler != null) {
                passiveSkillHandler.tickComposters(level);
            }
            // Periodic data saves (dirty flag based)
            com.mlkymc.world.BlockOwnerData.get(sl.getServer()).tickSave();
            com.mlkymc.world.PlacedOreTracker.get(sl.getServer()).saveIfDirty();
            tickTimings[2] += System.nanoTime() - t0;
        }

        // Every 40 ticks (2 seconds)
        if (gameTime % 40 == 0) {
            t0 = System.nanoTime();
            if (powerHandler != null) {
                powerHandler.tickSmithPassives(level);
            }
            // Check grave expiry (60 minutes)
            if (sl.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                graveManager.tick(sl.getServer());
            }
            tickTimings[3] += System.nanoTime() - t0;
        }

        // Ghost system ticks (once per second, overworld only)
        if (sl.dimension() == net.minecraft.world.level.Level.OVERWORLD
                && gameTime % 20 == 0) {
            t0 = System.nanoTime();
            ghostDataManager.tickSpectralEnergy(sl.getServer());
            ghostDataManager.tickHauntZones(sl.getServer());
            ghostDataManager.tickSpectralVision(sl.getServer());
            if (reviveListenerInstance != null) {
                reviveListenerInstance.tickPendingRevives(gameTime, sl.getServer());
            }
        }

        // Block owner sync (every 5 ticks = 0.25s)
        if (gameTime % 5 == 0) {
            t0 = System.nanoTime();
            var ownerData = com.mlkymc.world.BlockOwnerData.get(sl.getServer());
            for (var player : sl.players()) {
                if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) continue;
                String newMsg = null;
                var hitResult = sp.pick(5.0, 1.0f, false);
                if (hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit
                        && hitResult.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                    var pos = blockHit.getBlockPos();
                    com.mlkymc.world.BlockOwnerData.OwnerEntry owner = ownerData.getOwner(pos);
                    if (owner != null) {
                        var state = sl.getBlockState(pos);
                        String id = state.getBlock().getDescriptionId();
                        if (id.contains("furnace") || id.contains("smoker") || id.contains("blast")
                                || id.contains("composter") || id.contains("brewing") || id.contains("anvil")
                                || id.contains("enchanting")) {
                            newMsg = "[MLKYMC_BLOCK_OWNERS:" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                                    + "," + owner.uuid() + "," + owner.name() + "]";
                        }
                    }
                }
                // Anyone looking at a grave head: show timer as action bar text
                if (hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit2
                        && hitResult.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                    var gravePos = blockHit2.getBlockPos();
                    String graveDim = sl.dimension().identifier().toString();
                    var grave = graveManager.getGrave(graveDim, gravePos);
                    if (grave != null) {
                        long elapsed = gameTime - grave.deathTime;
                        long freeWindowTicks = 1200; // 60 seconds
                        long graveLifeTicks = 72000; // 60 minutes

                        String graveMsg;
                        if (elapsed < freeWindowTicks) {
                            // Within free revive window
                            int freeRemaining = (int)((freeWindowTicks - elapsed) / 20);
                            graveMsg = grave.ownerName + "'s Grave | Cleric free revive: " + freeRemaining + "s";
                        } else {
                            // After free window — show despawn timer
                            long despawnRemaining = graveLifeTicks - elapsed;
                            if (despawnRemaining > 0) {
                                int mins = (int)(despawnRemaining / 20 / 60);
                                int secs = (int)(despawnRemaining / 20 % 60);
                                graveMsg = grave.ownerName + "'s Grave | Despawning in: " + mins + "m " + secs + "s";
                            } else {
                                graveMsg = grave.ownerName + "'s Grave | Expired";
                            }
                        }
                        // Send as action bar (overrides block owner text when looking at grave)
                        sp.displayClientMessage(
                                net.minecraft.network.chat.Component.literal(graveMsg).withColor(0xFFAA00), true);
                        // Skip the block owner CLEAR for this tick
                        newMsg = null;
                    }
                }

                if (newMsg == null) newMsg = "[MLKYMC_BLOCK_OWNERS:CLEAR]";
                String lastMsg = lastBlockOwnerMsg.get(sp.getUUID());
                if (!newMsg.equals(lastMsg)) {
                    lastBlockOwnerMsg.put(sp.getUUID(), newMsg);
                    sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(newMsg));
                }
            }
            tickTimings[7] += System.nanoTime() - t0;
        }

        // Report timings every 30 seconds (600 ticks) to server log
        if (sl.dimension() == net.minecraft.world.level.Level.OVERWORLD && gameTime % 600 == 0 && gameTime > 0) {
            String[] names = {"everyTick", "every2tick", "every10tick", "every40tick",
                    "ghostSE", "hauntZones", "spectralVision", "blockOwnerSync",
                    "naturesCall", "autoSmelt", "lavaFishing"};
            var sb = new StringBuilder("[mlkyMC PERF] ");
            for (int i = 0; i < 11; i++) {
                sb.append(names[i]).append("=").append(tickTimings[i] / 1_000_000).append("ms ");
                tickTimings[i] = 0;
            }
            LOGGER.info(sb.toString());
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
