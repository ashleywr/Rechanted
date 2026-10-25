package net.tagtart.rechanted.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import net.tagtart.rechanted.compat.BookOperation;
import net.tagtart.rechanted.compat.BookOperationCatalog;
import net.tagtart.rechanted.component.ModDataComponents;
import net.tagtart.rechanted.config.RechantedCommonConfigs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class BookOperationCatalogTest {
    @Test
    void sharedCatalogHasStableUniqueIdsAndAllOperationKinds(MinecraftServer server) {
        boolean oldMerge = RechantedCommonConfigs.BOOK_MERGING_ENABLED.get();
        double oldLapis = RechantedCommonConfigs.GRINDSTONE_LAPIS_REFUND_CHANCE.get();
        try {
            RechantedCommonConfigs.BOOK_MERGING_ENABLED.set(true);
            RechantedCommonConfigs.GRINDSTONE_LAPIS_REFUND_CHANCE.set(0.25);
            var operations = BookOperationCatalog.create(server.registryAccess(),
                    List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_HELMET)));
            assertDoesNotThrow(() -> BookOperationCatalog.create(server.registryAccess(), BookOperationCatalog.defaultEquipment()));
            var ids = new HashSet<ResourceLocation>();
            for (var operation : operations) {
                assertTrue(ids.add(operation.id()), "Duplicate operation ID: " + operation.id());
                assertFalse(operation.firstInputs().isEmpty());
                assertFalse(operation.details().isEmpty());
                assertFalse(operation.details().stream().anyMatch(detail -> detail.getString().contains("recipe.rechanted.detail.")),
                        "An operation description is missing its translation: " + operation.id());
            }
            for (var kind : BookOperation.Kind.values()) {
                assertTrue(operations.stream().anyMatch(operation -> operation.kind() == kind), "Missing " + kind);
            }
            assertTrue(BookOperationCatalog.workstations(BookOperation.Kind.REVEAL).isEmpty(),
                    "In-hand revealing must not advertise the enchanting table as its workstation");
            var recycling = operations.stream().filter(operation -> operation.kind() == BookOperation.Kind.RECYCLE).findFirst().orElseThrow();
            assertTrue(recycling.extraOutput().is(Items.LAPIS_LAZULI));
            assertEquals(0.25, recycling.extraOutputChance());

            RechantedCommonConfigs.BOOK_MERGING_ENABLED.set(false);
            assertTrue(BookOperationCatalog.create(server.registryAccess(), List.of()).stream()
                    .noneMatch(operation -> operation.kind() == BookOperation.Kind.MERGE));
        } finally {
            RechantedCommonConfigs.BOOK_MERGING_ENABLED.set(oldMerge);
            RechantedCommonConfigs.GRINDSTONE_LAPIS_REFUND_CHANCE.set(oldLapis);
        }
    }

    @Test
    void applicationOnlyListsSupportedEquipmentAndUsesOneAlternativeResult(MinecraftServer server) {
        var smite = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse("minecraft:smite")))
                .orElseThrow();
        ItemStack book = RechantedBookOperations.createBook(smite, 1, 75);
        var operations = new ArrayList<BookOperation>();
        BookOperationCatalog.addBookUses(operations, book,
                List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_HELMET), new ItemStack(Items.STONE)));
        var apply = operations.stream().filter(operation -> operation.kind() == BookOperation.Kind.APPLY).findFirst().orElseThrow();
        assertEquals(1, apply.secondInputs().size());
        assertTrue(apply.secondInputs().getFirst().is(Items.IRON_SWORD));
        assertEquals(1, apply.outputAlternatives().size());
        assertEquals(1, apply.outputAlternatives().getFirst().getEnchantments().getLevel(smite));
        var upgrade = operations.stream().filter(operation -> operation.id().getPath().contains("/apply_upgrade/"))
                .findFirst().orElseThrow();
        assertEquals(1, upgrade.secondInputs().getFirst().getEnchantments().getLevel(smite));
        assertEquals(2, upgrade.outputAlternatives().getFirst().getEnchantments().getLevel(smite));
        assertFalse(book.isEnchanted());
        assertEquals(75, book.get(ModDataComponents.SUCCESS_RATE));
    }
}
