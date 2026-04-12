package com.mlkymc.classes;

import com.mlkymc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cleric Lv20 exclusive: Concoction Brewing System
 *
 * Clerics can use potions as ingredients in a brewing stand.
 * Each brew adds +1 to a counter on the output potions and adds the ingredient's effects.
 * On the 4th brew, the potions transform into a Concoction with random wacky effects.
 *
 * Tracked via CUSTOM_DATA: mlkymc_brew_count (int), mlkymc_brew_effects (list of potion effect names)
 */
public class ConcoctionHandler {

    private static final String BREW_COUNT_KEY = "mlkymc_brew_count";
    private static final String BREW_EFFECTS_KEY = "mlkymc_brew_effects";
    private static final int MAX_BREWS = 3; // 4th attempt converts to concoction

    private final ClassManager classManager;

    public ConcoctionHandler(ClassManager classManager) {
        this.classManager = classManager;
    }

    /**
     * Cleric Lv20+ right-clicks a brewing stand while holding a potion:
     * Applies the potion as ingredient to all potions in the brewing stand input slots.
     */
    @SubscribeEvent
    public void onRightClickBrewingStand(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        // Check if player is crouching + holding a potion + clicking a brewing stand
        if (!player.isShiftKeyDown()) return;

        ItemStack held = player.getMainHandItem();
        if (!isPotionItem(held)) return;

        var state = serverLevel.getBlockState(event.getPos());
        if (!state.getBlock().getDescriptionId().contains("brewing_stand")) return;

        var be = serverLevel.getBlockEntity(event.getPos());
        if (!(be instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity brewingStand)) return;

        // Check Cleric Lv20+
        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.CLERIC) return;
        if (data.getLevel(ProfessionType.CLERIC) < 20) return;

        // Calculate how much count the ingredient adds: +1 (base) + ingredient's own brew count
        int ingredientBrewCount = getBrewCount(held);
        int countToAdd = 1 + ingredientBrewCount;

        // Process each input slot (0, 1, 2)
        boolean brewed = false;
        for (int slot = 0; slot < 3; slot++) {
            ItemStack inputPotion = brewingStand.getItem(slot);
            if (inputPotion.isEmpty()) continue;
            if (!isPotionItem(inputPotion)) continue;

            // Already a concoction — skip
            if (inputPotion.is(ModItems.CONCOCTION.get())) continue;

            int currentCount = getBrewCount(inputPotion);
            int newCount = currentCount + countToAdd;

            if (newCount >= MAX_BREWS + 1) {
                // Reached 4+ = convert to concoction matching the input potion type
                ItemStack concoction = createConcoction(inputPotion);
                brewingStand.setItem(slot, concoction);
                brewed = true;
            } else {
                // Add count and ingredient effects
                setBrewCount(inputPotion, newCount);
                addIngredientEffects(inputPotion, held);
                updatePotionLore(inputPotion);
                brewed = true;
            }
        }

        if (brewed) {
            // Consume 1 potion from hand
            held.shrink(1);

            // Sound + XP
            serverLevel.playSound(null, event.getPos(), SoundEvents.BREWING_STAND_BREW,
                    SoundSource.BLOCKS, 1.0f, 1.2f);
            classManager.addXp(player, ProfessionType.CLERIC, 5, "concoction brew");
            brewingStand.setChanged();

            player.displayClientMessage(Component.literal("Concoction brewed!").withColor(0xAA55FF), true);
        }

        event.setCanceled(true);
    }

    // =========================================================================
    // Preserve brew counter through vanilla brewing
    // =========================================================================

    // Track brew data per brewing stand position (saved before vanilla brew, restored after)
    private final Map<Long, int[]> savedBrewCounts = new HashMap<>();
    private final Map<Long, List<CompoundTag>> savedBrewEffects = new HashMap<>();

    /**
     * Tick check: detect when a brewing stand with brewed potions is about to brew via vanilla.
     * Save the brew data before vanilla overwrites it.
     */
    public void tickPreserveBrewData(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % 5 != 0) return;

        var ownerData = com.mlkymc.world.BlockOwnerData.get(serverLevel.getServer());
        var loadedOwned = ownerData.getLoadedOwned(serverLevel);

        for (var entry : loadedOwned.entrySet()) {
            BlockPos pos = entry.getKey();
            var be = serverLevel.getBlockEntity(pos);
            if (!(be instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity brewingStand)) continue;

            long posKey = pos.asLong();

                // Check if any input slot has brew data
                boolean hasBrewed = false;
                int[] counts = new int[3];
                @SuppressWarnings("unchecked")
                List<CompoundTag>[] effects = new List[3];
                for (int slot = 0; slot < 3; slot++) {
                    ItemStack potion = brewingStand.getItem(slot);
                    counts[slot] = getBrewCount(potion);
                    if (counts[slot] > 0) {
                        hasBrewed = true;
                        CustomData cd = potion.get(DataComponents.CUSTOM_DATA);
                        if (cd != null) {
                            ListTag list = cd.copyTag().getListOrEmpty(BREW_EFFECTS_KEY);
                            effects[slot] = new ArrayList<>();
                            for (int i = 0; i < list.size(); i++) {
                                effects[slot].add(list.getCompound(i).orElseThrow());
                            }
                        }
                    }
                }

                if (hasBrewed) {
                    savedBrewCounts.put(posKey, counts);
                }
        }
    }

    /**
     * After vanilla brewing: restore saved brew data onto the new potions.
     */
    @SubscribeEvent
    public void onPotionBrewed(net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Find the brewing stand the player just used
        // Check nearby brewing stands for saved data
        if (!(player.level() instanceof ServerLevel sl)) return;

        var hitResult = player.pick(5.0, 0, false);
        if (!(hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit)) return;

        var be = sl.getBlockEntity(blockHit.getBlockPos());
        if (!(be instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity brewingStand)) return;

        long posKey = blockHit.getBlockPos().asLong();
        int[] counts = savedBrewCounts.remove(posKey);
        if (counts == null) return;

        // Restore brew counts to the new potions
        for (int slot = 0; slot < 3; slot++) {
            if (counts[slot] > 0) {
                ItemStack potion = brewingStand.getItem(slot);
                if (!potion.isEmpty() && isPotionItem(potion)) {
                    setBrewCount(potion, counts[slot]);
                    updatePotionLore(potion);
                }
            }
        }
        brewingStand.setChanged();
    }

    private boolean isPotionItem(ItemStack stack) {
        return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
    }

    private int getBrewCount(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        return cd.copyTag().getIntOr(BREW_COUNT_KEY, 0);
    }

    private void setBrewCount(ItemStack stack, int count) {
        CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = cd.copyTag();
        tag.putInt(BREW_COUNT_KEY, count);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private void addIngredientEffects(ItemStack target, ItemStack ingredient) {
        // Get ingredient's potion effects
        PotionContents ingredientContents = ingredient.get(DataComponents.POTION_CONTENTS);
        if (ingredientContents == null) return;

        // Get the actual MobEffectInstances from the ingredient potion
        var ingredientEffects = new ArrayList<MobEffectInstance>();
        ingredientContents.forEachEffect(e -> ingredientEffects.add(new MobEffectInstance(e)), 1.0f);

        if (ingredientEffects.isEmpty()) return;

        // Merge into target's POTION_CONTENTS custom effects
        PotionContents targetContents = target.getOrDefault(DataComponents.POTION_CONTENTS,
                PotionContents.EMPTY);

        // Collect ONLY the existing custom effects (not base potion effects)
        // This prevents duplicating effects that come from the base potion type
        var existingCustom = new ArrayList<>(targetContents.customEffects());
        var mergedEffects = new ArrayList<>(existingCustom);

        // Collect base potion effect types to avoid duplicating them
        var baseEffectTypes = new java.util.HashSet<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>>();
        if (targetContents.potion().isPresent()) {
            for (var effect : targetContents.potion().get().value().getEffects()) {
                baseEffectTypes.add(effect.getEffect());
            }
        }

        // Add new effects (skip if already in base potion or custom effects)
        for (var newEffect : ingredientEffects) {
            if (baseEffectTypes.contains(newEffect.getEffect())) continue;
            boolean duplicate = false;
            for (var existing : mergedEffects) {
                if (existing.getEffect().equals(newEffect.getEffect())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                mergedEffects.add(newEffect);
            }
        }

        // Rebuild POTION_CONTENTS with merged custom effects
        target.set(DataComponents.POTION_CONTENTS,
                new PotionContents(targetContents.potion(), targetContents.customColor(),
                        java.util.List.copyOf(mergedEffects), targetContents.customName()));

        // Also track in NBT for reference
        CustomData cd = target.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = cd.copyTag();
        ListTag effectList = tag.getListOrEmpty(BREW_EFFECTS_KEY);
        if (ingredientContents.potion().isPresent()) {
            CompoundTag effectTag = new CompoundTag();
            effectTag.putString("potion", ingredientContents.potion().get().getRegisteredName());
            effectList.add(effectTag);
        }
        tag.put(BREW_EFFECTS_KEY, effectList);
        target.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private void updatePotionLore(ItemStack stack) {
        int count = getBrewCount(stack);
        int remaining = (MAX_BREWS + 1) - count;
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Cleric Brew +" + count).withColor(0xAA55FF));
        if (remaining > 0) {
            lore.add(Component.literal(remaining + " more until Concoction").withColor(0x8855CC));
        } else {
            lore.add(Component.literal("Ready to become a Concoction!").withColor(0xFF55FF));
        }
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
    }

    /**
     * Tick lingering concoction clouds: apply effects to players inside them.
     */
    public void tickLingeringClouds(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel sl)) return;
        if (sl.getGameTime() % 20 != 0) return; // Once per second

        // Check each online player for nearby concoction clouds
        for (ServerPlayer player : sl.players()) {
            var nearby = sl.getEntitiesOfClass(net.minecraft.world.entity.AreaEffectCloud.class,
                    player.getBoundingBox().inflate(10));
            for (var cloud : nearby) {
                if (!cloud.getTags().contains("mlkymc_concoction_cloud")) continue;

                // Check if player is within the cloud's radius
                double dx = player.getX() - cloud.getX();
                double dz = player.getZ() - cloud.getZ();
                double distSq = dx * dx + dz * dz;
                double cloudRadius = cloud.getRadius();
                if (distSq > cloudRadius * cloudRadius) continue;
                if (Math.abs(player.getY() - cloud.getY()) > 2) continue;

                int concoctionId = -1;
                for (String tag : cloud.getTags()) {
                    if (tag.startsWith("mlkymc_concoction_cloud_id_")) {
                        try {
                            concoctionId = Integer.parseInt(tag.substring(27));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                if (concoctionId < 0) continue;

                ItemStack fakeStack = new ItemStack(ModItems.CONCOCTION.get());
                var nbt = new net.minecraft.nbt.CompoundTag();
                nbt.putInt("mlkymc_concoction_id", concoctionId);
                fakeStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.of(nbt));
                applyConcoction(player, fakeStack);
                break; // Only apply once per tick per player
            }
        }
    }

    // =========================================================================
    // CONCOCTION CREATION
    // =========================================================================

    private static ItemStack createConcoction(ItemStack inputPotion) {
        // Match the concoction type to the input potion type
        net.minecraft.world.item.Item concoctionItem;
        if (inputPotion.is(Items.SPLASH_POTION)) {
            concoctionItem = ModItems.CONCOCTION_SPLASH.get();
        } else if (inputPotion.is(Items.LINGERING_POTION)) {
            concoctionItem = ModItems.CONCOCTION_LINGERING.get();
        } else {
            concoctionItem = ModItems.CONCOCTION.get();
        }
        ItemStack concoction = new ItemStack(concoctionItem);

        // Randomize: effect pool, duration, potency, type (drink/splash/linger)
        var random = ThreadLocalRandom.current();
        int effectIndex = random.nextInt(CONCOCTION_EFFECTS.length);
        ConcocEffect effect = CONCOCTION_EFFECTS[effectIndex];

        CompoundTag nbt = new CompoundTag();
        nbt.putInt("mlkymc_concoction_id", effectIndex);
        nbt.putString("mlkymc_concoction_name", effect.name);
        concoction.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));

        // Enchantment glint for mystery
        concoction.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        // Mystery lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Concoction").withColor(0xFF55FF));
        lore.add(Component.literal("A volatile mixture...").withColor(0xAAAAAA));
        lore.add(Component.literal("Drink to find out!").withColor(0x8855CC));
        concoction.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));

        return concoction;
    }

    /**
     * Apply concoction effects when consumed. Called from ItemFunctionHandler.
     */
    // Track last concoction application time per player to prevent spam
    private static final Map<UUID, Long> lastConcoctionApply = new HashMap<>();

    public static void applyConcoction(ServerPlayer player, ItemStack concoction) {
        CustomData cd = concoction.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return;
        int effectId = cd.copyTag().getIntOr("mlkymc_concoction_id", -1);
        if (effectId < 0 || effectId >= CONCOCTION_EFFECTS.length) return;

        // Prevent re-applying within 3 seconds (lingering cloud spam protection)
        long now = System.currentTimeMillis();
        Long lastApply = lastConcoctionApply.get(player.getUUID());
        if (lastApply != null && now - lastApply < 3000) return;
        lastConcoctionApply.put(player.getUUID(), now);

        ConcocEffect effect = CONCOCTION_EFFECTS[effectId];
        effect.applier.accept(player);

        // Show on action bar instead of chat
        player.displayClientMessage(Component.literal("Concoction: " + effect.name + "!")
                .withColor(effect.color), true);

        if (player.level() instanceof ServerLevel sl) {
            sl.playSound(null, player.blockPosition(),
                    SoundEvents.WITCH_DRINK, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
    }

    // =========================================================================
    // EFFECT DEFINITIONS
    // =========================================================================

    private record ConcocEffect(String name, int color, java.util.function.Consumer<ServerPlayer> applier) {}

    private static final ConcocEffect[] CONCOCTION_EFFECTS = {
        // === BENEFICIAL ===
        new ConcocEffect("Power Surge", 0x55FF55, p -> {
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 1));
            p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 1));
            p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 200, 1));
        }),
        new ConcocEffect("Guardian Angel", 0xFFD700, p -> {
            p.heal(p.getMaxHealth());
            p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 4));
        }),
        new ConcocEffect("Shadow Walk", 0x8855FF, p -> {
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 2400, 0));
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, 2400, 1));
        }),
        new ConcocEffect("Iron Skin", 0xAAAAAA, p -> {
            p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 600, 2));
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0));
        }),
        new ConcocEffect("Berserker", 0xFF5555, p -> {
            p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 400, 2));
            p.addEffect(new MobEffectInstance(MobEffects.HASTE, 400, 2));
            p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 400, 0));
        }),

        // === CHAOTIC ===
        new ConcocEffect("Warp", 0x55FFFF, p -> {
            var random = ThreadLocalRandom.current();
            double dx = random.nextInt(200) - 100;
            double dz = random.nextInt(200) - 100;
            if (p.level() instanceof ServerLevel sl) {
                double newX = p.getX() + dx;
                double newZ = p.getZ() + dz;
                int newY = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int)newX, (int)newZ);
                p.teleportTo(sl, newX, newY + 1, newZ, java.util.Set.of(), p.getYRot(), p.getXRot(), false);
            }
        }),
        new ConcocEffect("Moon Gravity", 0xAADDFF, p -> {
            p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 300, 0));
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0));
        }),
        new ConcocEffect("Mega Knockback", 0xFFAA00, p -> {
            // Pure knockback — no extra damage
            var attr = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_KNOCKBACK);
            if (attr != null) {
                var modId = net.minecraft.resources.Identifier.parse("mlkymc:concoction_knockback");
                attr.removeModifier(modId);
                attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        modId, 8.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
                // Schedule removal after 30s
                p.level().getServer().execute(() -> {
                    // Remove after 600 ticks via delayed check
                    p.addTag("mlkymc_mega_kb_" + p.level().getGameTime());
                });
            }
        }),
        new ConcocEffect("Bouncy", 0x55FF55, p -> {
            p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 600, 4));
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0));
        }),
        new ConcocEffect("Lightning Rod", 0xFFFF55, p -> {
            if (p.level() instanceof ServerLevel sl) {
                var lightning = new net.minecraft.world.entity.LightningBolt(
                        net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, sl);
                lightning.setPos(p.getX(), p.getY(), p.getZ());
                sl.addFreshEntity(lightning);
            }
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
        }),

        // === CURSED ===
        new ConcocEffect("Blinding Sickness", 0x553355, p -> {
            p.addEffect(new MobEffectInstance(MobEffects.POISON, 300, 0));
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 300, 0));
            p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 300, 0));
        }),
        new ConcocEffect("Earthquake", 0x885533, p -> {
            if (p.level() instanceof ServerLevel sl) {
                sl.explode(null, p.getX(), p.getY(), p.getZ(), 3.0f,
                        net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            }
            p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 4)); // survive own explosion
        }),
        new ConcocEffect("Mob Magnet", 0xFF3333, p -> {
            p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1200, 0));
            p.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, 1200, 0));
            p.sendSystemMessage(Component.literal("Every mob can see you now...").withColor(0xFF3333));
        }),
        new ConcocEffect("Nausea Trip", 0x55AA55, p -> {
            p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 400, 0, false, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0));
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, 400, 2));
        }),
        new ConcocEffect("Glass Cannon", 0xFF8800, p -> {
            p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 600, 3));
            p.addEffect(new MobEffectInstance(MobEffects.HASTE, 600, 2));
            p.setHealth(1.0f); // Set to half a heart
        }),

        // === UTILITY ===
        new ConcocEffect("Deep Diver", 0x0055AA, p -> {
            p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 6000, 0));
            p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 6000, 0));
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 6000, 0));
        }),
        new ConcocEffect("Featherweight", 0xFFFFFF, p -> {
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 2400, 0));
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, 2400, 1));
            p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 2400, 2));
        }),
        new ConcocEffect("Midas Touch", 0xFFD700, p -> {
            p.addEffect(new MobEffectInstance(MobEffects.LUCK, 2400, 2));
            p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 2400, 0));
        }),
        new ConcocEffect("Ice Age", 0xAADDFF, p -> {
            if (p.level() instanceof ServerLevel sl) {
                BlockPos center = p.blockPosition();
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        BlockPos surface = sl.getHeightmapPos(
                                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, center.offset(dx, 0, dz));
                        if (sl.getBlockState(surface.below()).isSolid() && sl.getBlockState(surface).isAir()) {
                            sl.setBlock(surface, net.minecraft.world.level.block.Blocks.SNOW.defaultBlockState(), 3);
                        }
                    }
                }
            }
            p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 600, 1));
            p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 600, 0));
        }),
    };
}
