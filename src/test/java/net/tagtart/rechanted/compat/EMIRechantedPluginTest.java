package net.tagtart.rechanted.compat;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiComparisonDefaults;
import dev.emi.emi.registry.EmiStackList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import net.tagtart.rechanted.item.ModItems;
import net.tagtart.rechanted.util.RechantedBookOperations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(EphemeralTestServerProvider.class)
class EMIRechantedPluginTest {
    @Test
    void enchantedBookUsesFindTheLoadedConversionRegardlessOfEnchantments(MinecraftServer server) {
        var previous = EmiComparisonDefaults.comparisons;
        try {
            var holder = server.getRecipeManager().byKey(ResourceLocation.parse("rechanted:mysterious_book")).orElseThrow();
            var recipe = holder.value();
            EmiStack input = EmiIngredient.of(recipe.getIngredients().getFirst()).getEmiStacks().getFirst();
            assertTrue(recipe.getResultItem(server.registryAccess()).is(ModItems.MYSTERIOUS_BOOK.get()));
            var sharpness = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
            ItemStack enchanted = new ItemStack(Items.ENCHANTED_BOOK);
            enchanted.enchant(sharpness, 3);

            // Reproduce EMI's vanilla default and its actual recipe lookup hashing.
            EmiComparisonDefaults.comparisons = Map.of(Items.ENCHANTED_BOOK, Comparison.compareComponents());
            var before = new Object2ObjectOpenCustomHashMap<EmiStack, ResourceLocation>(new EmiStackList.ComparisonHashStrategy());
            before.put(input.copy(), holder.id());
            assertNull(before.get(EmiStack.of(enchanted)));

            EmiComparisonDefaults.comparisons = registeredComparisons();
            var after = new Object2ObjectOpenCustomHashMap<EmiStack, ResourceLocation>(new EmiStackList.ComparisonHashStrategy());
            after.put(input.copy(), holder.id());
            for (var enchantment : List.of(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS,
                    net.minecraft.world.item.enchantment.Enchantments.MENDING,
                    net.minecraft.world.item.enchantment.Enchantments.BINDING_CURSE)) {
                for (int level : List.of(1, 3)) {
                    ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                    book.enchant(server.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(enchantment), level);
                    assertEquals(holder.id(), after.get(EmiStack.of(book)));
                }
            }
            assertEquals(holder.id(), after.get(EmiStack.of(Items.ENCHANTED_BOOK)));
            assertNull(after.get(EmiStack.of(Items.BOOK)));
        } finally {
            EmiComparisonDefaults.comparisons = previous;
        }
    }

    @Test
    void rolledRechantedBooksKeepEnchantmentAndLevelIdentity(MinecraftServer server) {
        var previous = EmiComparisonDefaults.comparisons;
        try {
            EmiComparisonDefaults.comparisons = registeredComparisons();
            var enchantments = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            var sharpness = enchantments.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
            var smite = enchantments.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SMITE);
            EmiStack book = EmiStack.of(RechantedBookOperations.createBook(sharpness, 1, 25));
            assertTrue(book.isEqual(EmiStack.of(RechantedBookOperations.createBook(sharpness, 1, 90))));
            assertFalse(book.isEqual(EmiStack.of(RechantedBookOperations.createBook(sharpness, 2, 25))));
            assertFalse(book.isEqual(EmiStack.of(RechantedBookOperations.createBook(smite, 1, 25))));
        } finally {
            EmiComparisonDefaults.comparisons = previous;
        }
    }

    private static Map<Object, Comparison> registeredComparisons() {
        Map<Object, Comparison> comparisons = new HashMap<>();
        EmiRegistry registry = mock(EmiRegistry.class);
        doAnswer(call -> {
            comparisons.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(registry).setDefaultComparison(any(Item.class), any(Comparison.class));
        EMIRechantedPlugin.registerBookComparisons(registry);
        return comparisons;
    }
}
