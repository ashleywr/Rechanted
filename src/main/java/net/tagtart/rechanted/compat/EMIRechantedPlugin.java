package net.tagtart.rechanted.compat;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tagtart.rechanted.Rechanted;
import net.tagtart.rechanted.item.ModItems;
import net.tagtart.rechanted.util.RechantedBookOperations;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

@EmiEntrypoint
public final class EMIRechantedPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registerBookComparisons(registry);
        var categories = new EnumMap<BookOperation.Kind, EmiRecipeCategory>(BookOperation.Kind.class);
        for (var kind : BookOperation.Kind.values()) {
            var category = new EmiRecipeCategory(ResourceLocation.fromNamespaceAndPath(Rechanted.MOD_ID, "book_" + kind.key),
                    EmiStack.of(BookOperationCatalog.icon(kind)));
            categories.put(kind, category);
            registry.addCategory(category);
            for (var workstation : BookOperationCatalog.workstations(kind)) {
                registry.addWorkstation(category, EmiStack.of(workstation));
            }
        }
        // EMI can import JEI displays; replace our bridged categories to avoid registering every operation twice.
        registry.removeRecipes(recipe -> !(recipe instanceof OperationRecipe)
                && recipe.getCategory().getId().getNamespace().equals(Rechanted.MOD_ID)
                && recipe.getCategory().getId().getPath().startsWith("book_"));
        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (var operation : BookOperationCatalog.create(level.registryAccess(), BookOperationCatalog.defaultEquipment())) {
                registry.addRecipe(new OperationRecipe(operation, categories.get(operation.kind())));
            }
        }
    }

    static void registerBookComparisons(EmiRegistry registry) {
        // Vanilla books are disabled and all convert through the same item-only crafting ingredient.
        // EMI's vanilla component comparison otherwise hides this use for books carrying enchantments.
        registry.setDefaultComparison(Items.ENCHANTED_BOOK, Comparison.DEFAULT_COMPARISON);
        registry.setDefaultComparison(ModItems.RECHANTED_BOOK.get(),
                Comparison.compareData(stack -> RechantedBookOperations.recipeIdentity(stack.getItemStack())));
    }

    private record OperationRecipe(BookOperation operation, EmiRecipeCategory category) implements EmiRecipe {
        @Override public EmiRecipeCategory getCategory() { return category; }
        @Override public ResourceLocation getId() { return operation.id(); }
        @Override public int getDisplayWidth() { return BookOperationText.WIDTH; }
        @Override public int getDisplayHeight() { return BookOperationText.height(operation); }

        @Override
        public List<EmiIngredient> getInputs() {
            List<EmiIngredient> inputs = new ArrayList<>();
            inputs.add(ingredient(operation.firstInputs()));
            if (!operation.secondInputs().isEmpty()) {
                inputs.add(ingredient(operation.secondInputs()));
            }
            return List.copyOf(inputs);
        }

        @Override
        public List<EmiStack> getOutputs() {
            List<EmiStack> outputs = new ArrayList<>(operation.outputAlternatives().stream().map(EmiStack::of).toList());
            if (!operation.extraOutput().isEmpty()) {
                EmiStack extra = EmiStack.of(operation.extraOutput());
                extra.setChance((float) operation.extraOutputChance());
                outputs.add(extra);
            }
            return List.copyOf(outputs);
        }

        @Override
        public boolean supportsRecipeTree() {
            // These are conditional actions, with XP costs, random outcomes or alternative equipment.
            // Do not present them as deterministic, automatically craftable material conversions.
            return false;
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addSlot(ingredient(operation.firstInputs()), 1, 4);
            if (!operation.secondInputs().isEmpty()) {
                widgets.addSlot(ingredient(operation.secondInputs()), 37, 4);
                widgets.addText(net.minecraft.network.chat.Component.literal("+"), 25, 9, 0xFF404040, false);
            }
            widgets.addText(net.minecraft.network.chat.Component.literal("→"), 72, 9, 0xFF404040, false);
            widgets.addSlot(ingredient(operation.outputAlternatives()), 99, 4).recipeContext(this);
            if (!operation.extraOutput().isEmpty()) {
                EmiStack extra = EmiStack.of(operation.extraOutput());
                extra.setChance((float) operation.extraOutputChance());
                widgets.addSlot(extra, 129, 4).recipeContext(this);
            }
            widgets.addDrawable(0, 0, BookOperationText.WIDTH, getDisplayHeight(),
                    (graphics, mouseX, mouseY, delta) -> BookOperationText.draw(graphics, operation));
        }

        private static EmiIngredient ingredient(List<ItemStack> alternatives) {
            return EmiIngredient.of(alternatives.stream().map(EmiStack::of).toList());
        }
    }
}
