package net.tagtart.rechanted.compat;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.tagtart.rechanted.Rechanted;
import net.tagtart.rechanted.item.ModItems;
import net.tagtart.rechanted.util.RechantedBookOperations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@JeiPlugin
public class JEIRechantedPlugin implements IModPlugin {

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Rechanted.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // Enchantment and level identify a recipe; success rates and gem history do not.
        ISubtypeInterpreter<ItemStack> interpreter = new ISubtypeInterpreter<>() {
            @Override
            public @Nullable Object getSubtypeData(ItemStack itemStack, @NotNull UidContext context) {

                return RechantedBookOperations.recipeIdentity(itemStack);
            }

            @Override
            public @NotNull String getLegacyStringSubtypeInfo(@NotNull ItemStack ingredient, @NotNull UidContext context) {
                return RechantedBookOperations.recipeIdentity(ingredient);
            }
        };
        registration.registerSubtypeInterpreter(ModItems.RECHANTED_BOOK.get(), interpreter);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        for (var kind : BookOperation.Kind.values()) {
            registration.addRecipeCategories(new JEIBookOperationCategory(kind, registration.getJeiHelpers().getGuiHelper()));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        var operations = BookOperationCatalog.create(level.registryAccess(), BookOperationCatalog.defaultEquipment());
        for (var kind : BookOperation.Kind.values()) {
            registration.addRecipes(JEIBookOperationCategory.type(kind),
                    operations.stream().filter(operation -> operation.kind() == kind).toList());
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (var kind : BookOperation.Kind.values()) {
            for (var workstation : BookOperationCatalog.workstations(kind)) {
                registration.addRecipeCatalyst(workstation, JEIBookOperationCategory.type(kind));
            }
        }
    }
}
