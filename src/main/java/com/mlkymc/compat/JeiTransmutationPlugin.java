package com.mlkymc.compat;

import com.mlkymc.registry.ModItems;
import com.mlkymc.transmutation.TransmutationRecipe;
import com.mlkymc.transmutation.TransmutationRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JEI plugin that registers the Cauldron Transmutation recipe category.
 * Players can search for any transmutation output in JEI and see the recipe
 * with inputs, output, and Cleric level requirement.
 *
 * <p>This is a client-side-only class. JEI discovers it via the @JeiPlugin annotation.
 * If JEI isn't installed, this class is never loaded (compileOnly dependency).
 */
@JeiPlugin
public class JeiTransmutationPlugin implements IModPlugin {

    public static final Identifier PLUGIN_ID = Identifier.parse("mlkymc:transmutation");
    public static final RecipeType<TransmutationRecipe> TRANSMUTATION_TYPE =
            RecipeType.create("mlkymc", "transmutation", TransmutationRecipe.class);

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration reg) {
        reg.addRecipeCategories(new TransmutationCategory(reg.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        // Load all transmutation recipes from the registry
        reg.addRecipes(TRANSMUTATION_TYPE, TransmutationRegistry.getRecipes());
    }

    // =========================================================================
    // Category: renders the cauldron recipe layout
    // =========================================================================

    public static class TransmutationCategory implements IRecipeCategory<TransmutationRecipe> {

        private final IDrawable icon;

        public TransmutationCategory(IGuiHelper guiHelper) {
            this.icon = guiHelper.createDrawableItemLike(Items.CAULDRON);
        }

        @Override
        public RecipeType<TransmutationRecipe> getRecipeType() {
            return TRANSMUTATION_TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.literal("Cauldron Transmutation");
        }

        @Override
        public int getWidth() {
            return 160;
        }

        @Override
        public int getHeight() {
            return 60;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, TransmutationRecipe recipe, IFocusGroup focuses) {
            // Input slots — lay out horizontally
            int x = 0;
            for (Map.Entry<net.minecraft.world.item.Item, Integer> entry : recipe.inputs().entrySet()) {
                builder.addInputSlot(x, 4)
                        .addItemStack(new ItemStack(entry.getKey(), entry.getValue()));
                x += 20;
            }

            // Arrow gap
            x = Math.max(x, 80);

            // Output slot
            if (recipe.output() != null && !recipe.output().isEmpty()) {
                builder.addOutputSlot(x + 10, 4)
                        .addItemStack(recipe.output());
            } else {
                // Effect-only recipe (Soul Fracture cleanse) — show a visual indicator
                builder.addOutputSlot(x + 10, 4)
                        .addItemStack(new ItemStack(Items.NETHER_STAR));
            }
        }

        @Override
        public void draw(TransmutationRecipe recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView slotsView,
                         net.minecraft.client.gui.GuiGraphics guiGraphics, double mouseX, double mouseY) {
            // Draw the Cleric level requirement text
            var font = net.minecraft.client.Minecraft.getInstance().font;
            String levelText = "Cleric Lv" + recipe.clericLevelRequired() + "+";
            guiGraphics.drawString(font, levelText, 2, 42, 0xFF55FFFF, true);

            // Draw recipe name
            String name = recipe.id().replace("_", " ");
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
            guiGraphics.drawString(font, name, 2, 30, 0xFFAAAAAA, true);

            // Arrow between inputs and output
            guiGraphics.drawString(font, "→", 75, 8, 0xFFFFFFFF, false);
        }
    }
}
