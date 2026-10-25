package net.tagtart.rechanted.compat;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public final class JEIBookOperationCategory implements IRecipeCategory<BookOperation> {
    private final BookOperation.Kind kind;
    private final IDrawable icon;

    public JEIBookOperationCategory(BookOperation.Kind kind, IGuiHelper gui) {
        this.kind = kind;
        icon = gui.createDrawableItemStack(BookOperationCatalog.icon(kind));
    }

    public static RecipeType<BookOperation> type(BookOperation.Kind kind) {
        return RecipeType.create("rechanted", "book_" + kind.key, BookOperation.class);
    }

    @Override public @NotNull RecipeType<BookOperation> getRecipeType() { return type(kind); }
    @Override public @NotNull Component getTitle() { return kind.title(); }
    @Override public int getWidth() { return BookOperationText.WIDTH; }
    @Override public int getHeight() { return BookOperationText.HEIGHT; }
    @Override public @NotNull IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BookOperation recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 2, 5).addItemStacks(recipe.firstInputs()).setStandardSlotBackground();
        var second = builder.addSlot(RecipeIngredientRole.INPUT, 38, 5)
                .addItemStacks(recipe.secondInputs()).setStandardSlotBackground();
        var output = builder.addSlot(RecipeIngredientRole.OUTPUT, 100, 5)
                .addItemStacks(recipe.outputAlternatives()).setStandardSlotBackground();
        if (kind == BookOperation.Kind.APPLY && recipe.secondInputs().size() == recipe.outputAlternatives().size()) {
            builder.createFocusLink(second, output);
        }
        if (!recipe.extraOutput().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 130, 5).addItemStack(recipe.extraOutput())
                    .setStandardSlotBackground().addRichTooltipCallback((slot, tooltip) -> tooltip.add(
                            Component.translatable("recipe.rechanted.detail.extra_chance", Math.round(recipe.extraOutputChance() * 100))));
        }
    }

    @Override
    public net.minecraft.resources.ResourceLocation getRegistryName(BookOperation recipe) {
        return recipe.id();
    }

    @Override
    public void draw(BookOperation recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        if (!recipe.secondInputs().isEmpty()) {
            graphics.drawString(font, "+", 25, 9, 0xFF404040, false);
        }
        graphics.drawString(font, "→", 72, 9, 0xFF404040, false);
        BookOperationText.draw(graphics, recipe);
    }
}
