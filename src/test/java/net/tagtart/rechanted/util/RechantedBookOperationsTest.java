package net.tagtart.rechanted.util;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import net.tagtart.rechanted.component.ModDataComponents;
import net.tagtart.rechanted.config.RechantedCommonConfigs;
import net.tagtart.rechanted.event.ModGenericEvents;
import net.tagtart.rechanted.item.ModItems;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class RechantedBookOperationsTest {
    @Test
    void mergingPreservesRatesAndRestrictionsWithoutMutatingInputs(MinecraftServer server) {
        try (Settings settings = new Settings()) {
            settings.enableMerging();
            var smite = enchantment(server, "minecraft:smite");
            ItemStack left = RechantedBookOperations.createBook(smite, 1, 85);
            ItemStack right = RechantedBookOperations.createBook(smite, 1, 60);
            right.set(ModDataComponents.REROLLED_SUCCESS_RATE, true);
            right.set(ModDataComponents.IS_CLONE, true);
            right.set(ModDataComponents.HAS_BEEN_CLONED, true);
            ItemStack beforeLeft = left.copy();
            ItemStack beforeRight = right.copy();

            var merge = RechantedBookOperations.merge(left, right).orElseThrow();
            assertEquals(2, RechantedBookOperations.readBook(merge.output()).orElseThrow().level());
            assertEquals(60, RechantedBookOperations.successRate(merge.output()));
            assertTrue(merge.output().getOrDefault(ModDataComponents.REROLLED_SUCCESS_RATE, false));
            assertTrue(merge.output().getOrDefault(ModDataComponents.IS_CLONE, false));
            assertTrue(merge.output().getOrDefault(ModDataComponents.HAS_BEEN_CLONED, false));
            assertEquals(2, merge.levelCost());
            assertTrue(ItemStack.matches(left, beforeLeft));
            assertTrue(ItemStack.matches(right, beforeRight));
            assertTrue(ItemStack.matches(merge.output(), RechantedBookOperations.merge(right, left).orElseThrow().output()));
        }
    }

    @Test
    void mergingRejectsDifferentBooksAndBothLevelCaps(MinecraftServer server) {
        try (Settings settings = new Settings()) {
            settings.enableMerging();
            var smite = enchantment(server, "minecraft:smite");
            var sharpness = enchantment(server, "minecraft:sharpness");
            var one = RechantedBookOperations.createBook(smite, 1, 80);
            assertTrue(RechantedBookOperations.merge(one, RechantedBookOperations.createBook(sharpness, 1, 80)).isEmpty());
            assertTrue(RechantedBookOperations.merge(one, RechantedBookOperations.createBook(smite, 2, 80)).isEmpty());
            var max = RechantedBookOperations.createBook(smite,
                    RechantedBookOperations.maximumMergeLevel(RechantedBookOperations.readBook(one).orElseThrow()), 80);
            assertTrue(RechantedBookOperations.merge(max, max).isEmpty());
            var registryMax = RechantedBookOperations.createBook(smite, smite.value().getMaxLevel(), 80);
            assertTrue(RechantedBookOperations.merge(registryMax, registryMax).isEmpty());
            var orphan = orphan(server);
            assertTrue(RechantedBookOperations.merge(orphan, orphan).isEmpty());
            assertTrue(RechantedBookOperations.merge(one.copyWithCount(2), one).isEmpty());
            RechantedCommonConfigs.BOOK_MERGING_ENABLED.set(false);
            assertTrue(RechantedBookOperations.merge(one, one).isEmpty());
        }
    }

    @Test
    void missingAndMalformedRatesCannotBecomeGuaranteedBooks(MinecraftServer server) {
        try (Settings settings = new Settings()) {
            settings.enableMerging();
            var smite = enchantment(server, "minecraft:smite");
            ItemStack left = RechantedBookOperations.createBook(smite, 1, 80);
            ItemStack right = left.copy();
            right.remove(ModDataComponents.SUCCESS_RATE);
            assertEquals(0, RechantedBookOperations.successRate(RechantedBookOperations.merge(left, right).orElseThrow().output()));
            right.set(ModDataComponents.SUCCESS_RATE, -10);
            assertEquals(0, RechantedBookOperations.successRate(RechantedBookOperations.merge(left, right).orElseThrow().output()));
            right.set(ModDataComponents.SUCCESS_RATE, 1000);
            assertEquals(80, RechantedBookOperations.successRate(RechantedBookOperations.merge(left, right).orElseThrow().output()));
        }
    }

    @Test
    void anvilPreviewUsesConfiguredCostAndConsumesOneRightInput(MinecraftServer server) {
        try (Settings settings = new Settings()) {
            settings.enableMerging();
            RechantedCommonConfigs.BOOK_MERGE_COST_PER_LEVEL.set(3);
            var book = RechantedBookOperations.createBook(enchantment(server, "minecraft:smite"), 1, 75);
            var event = new AnvilUpdateEvent(book, book.copy(), "Merged book", 0, null);
            ModGenericEvents.onAnvilUpdate(event);
            assertFalse(event.isCanceled());
            assertEquals(6, event.getCost());
            assertEquals(1, event.getMaterialCost());
            assertEquals("Merged book", event.getOutput().get(DataComponents.CUSTOM_NAME).getString());
            assertEquals(1, book.getCount());
            var invalid = new AnvilUpdateEvent(new ItemStack(Items.IRON_SWORD), book, null, 0, null);
            ModGenericEvents.onAnvilUpdate(invalid);
            assertTrue(invalid.isCanceled(), "Book application must not bypass success rates through an anvil");
        }
    }

    @Test
    void orphanedBooksRecycleFromEitherSlotAndInvalidItemsFallBackToPaper(MinecraftServer server) {
        try (Settings settings = new Settings()) {
            RechantedCommonConfigs.ORPHANED_BOOK_GRINDSTONE_XP.set(2);
            RechantedCommonConfigs.GRINDSTONE_RESULT_ITEM.set("missing_mod:missing_item");
            var book = orphan(server);
            for (boolean top : new boolean[] {true, false}) {
                var event = new GrindstoneEvent.OnPlaceItem(top ? book : ItemStack.EMPTY,
                        top ? ItemStack.EMPTY : book, -1);
                ModGenericEvents.onGrindstoneChange(event);
                assertFalse(event.isCanceled());
                assertTrue(event.getOutput().is(Items.PAPER));
                assertEquals(2, event.getXp());
                assertEquals(1, book.getCount());
            }
            RechantedCommonConfigs.GRINDSTONE_RESULT_ITEM.set("not a valid id");
            assertTrue(RechantedBookOperations.salvageItem().is(Items.PAPER));
            RechantedCommonConfigs.GRINDSTONE_RESULT_ITEM.set("minecraft:air");
            assertTrue(RechantedBookOperations.salvageItem().is(Items.PAPER));
        }
    }

    @Test
    void malformedAndTwoInputBooksAreRejectedWithoutChangingVanillaGrinding(MinecraftServer server) {
        var emptyBook = new ItemStack(ModItems.RECHANTED_BOOK.get());
        var emptyEvent = new GrindstoneEvent.OnPlaceItem(emptyBook, ItemStack.EMPTY, -1);
        ModGenericEvents.onGrindstoneChange(emptyEvent);
        assertTrue(emptyEvent.isCanceled());
        var valid = RechantedBookOperations.createBook(enchantment(server, "minecraft:smite"), 1, 80);
        var enchants = new ItemEnchantments.Mutable(valid.get(DataComponents.STORED_ENCHANTMENTS));
        enchants.set(enchantment(server, "minecraft:sharpness"), 1);
        emptyBook.set(DataComponents.STORED_ENCHANTMENTS, enchants.toImmutable());
        assertTrue(RechantedBookOperations.readBook(emptyBook).isEmpty());
        var combined = new GrindstoneEvent.OnPlaceItem(valid, valid.copy(), -1);
        ModGenericEvents.onGrindstoneChange(combined);
        assertTrue(combined.isCanceled());
        var vanilla = new GrindstoneEvent.OnPlaceItem(new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY, -1);
        ModGenericEvents.onGrindstoneChange(vanilla);
        assertFalse(vanilla.isCanceled());
        assertTrue(vanilla.getOutput().isEmpty());
        assertEquals(-1, vanilla.getXp());
    }

    @Test
    void lapisIsOnlyReturnedDuringConsumptionIncludingPlayerlessAutomation(MinecraftServer server) {
        try (Settings settings = new Settings()) {
            RechantedCommonConfigs.GRINDSTONE_LAPIS_REFUND_CHANCE.set(1.0);
            var book = RechantedBookOperations.createBook(enchantment(server, "minecraft:smite"), 1, 80);
            for (int i = 0; i < 10; i++) {
                var preview = new GrindstoneEvent.OnPlaceItem(book, ItemStack.EMPTY, -1);
                ModGenericEvents.onGrindstoneChange(preview);
                assertFalse(preview.getOutput().is(Items.LAPIS_LAZULI));
                assertFalse(book.has(DataComponents.CUSTOM_DATA));
                assertEquals(1, book.getCount());
            }
            for (boolean top : new boolean[] {true, false}) {
                var take = new GrindstoneEvent.OnTakeItem(ContainerLevelAccess.NULL, null,
                        top ? book : ItemStack.EMPTY, top ? ItemStack.EMPTY : book, 10);
                ModGenericEvents.onGrindstoneTake(take);
                assertFalse(take.isCanceled());
                assertEquals(10, take.getXp());
                assertTrue((top ? take.getNewTopItem() : take.getNewBottomItem()).is(Items.LAPIS_LAZULI));
                assertTrue((top ? take.getNewBottomItem() : take.getNewTopItem()).isEmpty());
            }
            RechantedCommonConfigs.GRINDSTONE_LAPIS_REFUND_CHANCE.set(0.0);
            var disabled = new GrindstoneEvent.OnTakeItem(ContainerLevelAccess.NULL, null, book, ItemStack.EMPTY, 10);
            ModGenericEvents.onGrindstoneTake(disabled);
            assertTrue(disabled.getNewTopItem().isEmpty());
            assertTrue(disabled.getNewBottomItem().isEmpty());
        }
    }

    @Test
    void zeroWeightedLevelsDoNotRaiseTheMergeCap(MinecraftServer server) {
        try (Settings settings = new Settings()) {
            settings.enableMerging();
            var one = RechantedBookOperations.createBook(enchantment(server, "minecraft:smite"), 1, 80);
            var book = RechantedBookOperations.readBook(one).orElseThrow();
            var rarity = RechantedBookOperations.rarity(book).orElseThrow();
            var originalPool = rarity.enchantmentPool;
            try {
                rarity.enchantmentPool = java.util.List.of(new EnchantmentPoolEntry("minecraft:smite", 1, 1, 3,
                        new java.util.ArrayList<>(java.util.List.of(1, 1, 0))));
                assertEquals(2, RechantedBookOperations.maximumMergeLevel(book));
                assertTrue(RechantedBookOperations.merge(one, one).isPresent());
                var two = RechantedBookOperations.createBook(book.enchantment(), 2, 80);
                assertTrue(RechantedBookOperations.merge(two, two).isEmpty());
            } finally {
                rarity.enchantmentPool = originalPool;
            }
        }
    }

    @Test
    void maximumXpRangeDoesNotOverflow(MinecraftServer server) {
        var stack = RechantedBookOperations.createBook(enchantment(server, "minecraft:smite"), 1, 80);
        var rarity = RechantedBookOperations.rarity(RechantedBookOperations.readBook(stack).orElseThrow()).orElseThrow();
        int originalMin = rarity.minGrindstoneXP;
        int originalMax = rarity.maxGrindstoneXP;
        try {
            rarity.minGrindstoneXP = Integer.MAX_VALUE;
            rarity.maxGrindstoneXP = Integer.MAX_VALUE;
            var preview = new GrindstoneEvent.OnPlaceItem(stack, ItemStack.EMPTY, -1);
            assertDoesNotThrow(() -> ModGenericEvents.onGrindstoneChange(preview));
            assertEquals(Integer.MAX_VALUE, preview.getXp());
        } finally {
            rarity.minGrindstoneXP = originalMin;
            rarity.maxGrindstoneXP = originalMax;
        }
    }

    @Test
    void recipeIdentityIgnoresChanceAndGemHistoryButNotEnchantmentOrLevel(MinecraftServer server) {
        var smite = enchantment(server, "minecraft:smite");
        var first = RechantedBookOperations.createBook(smite, 1, 40);
        var second = RechantedBookOperations.createBook(smite, 1, 90);
        second.set(ModDataComponents.REROLLED_SUCCESS_RATE, true);
        second.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("My book"));
        assertEquals("minecraft:smite@1", RechantedBookOperations.recipeIdentity(first));
        assertEquals(RechantedBookOperations.recipeIdentity(first), RechantedBookOperations.recipeIdentity(second));
        assertNotEquals(RechantedBookOperations.recipeIdentity(first),
                RechantedBookOperations.recipeIdentity(RechantedBookOperations.createBook(smite, 2, 40)));
        assertNotEquals(RechantedBookOperations.recipeIdentity(first), RechantedBookOperations.recipeIdentity(
                RechantedBookOperations.createBook(enchantment(server, "minecraft:sharpness"), 1, 40)));
    }

    private static Holder<Enchantment> enchantment(MinecraftServer server, String id) {
        return server.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(
                net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(id))).orElseThrow();
    }

    private static ItemStack orphan(MinecraftServer server) {
        var holder = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                .filter(enchantment -> UtilFunctions.getPropertiesFromEnchantment(enchantment.key().location().toString()) == null)
                .findFirst().orElseThrow();
        return RechantedBookOperations.createBook(holder, 1, 50);
    }

    private static class Settings implements AutoCloseable {
        private final boolean merging = RechantedCommonConfigs.BOOK_MERGING_ENABLED.get();
        private final int cost = RechantedCommonConfigs.BOOK_MERGE_COST_PER_LEVEL.get();
        private final int xp = RechantedCommonConfigs.ORPHANED_BOOK_GRINDSTONE_XP.get();
        private final double lapis = RechantedCommonConfigs.GRINDSTONE_LAPIS_REFUND_CHANCE.get();
        private final String item = RechantedCommonConfigs.GRINDSTONE_RESULT_ITEM.get();

        void enableMerging() { RechantedCommonConfigs.BOOK_MERGING_ENABLED.set(true); }

        @Override public void close() {
            RechantedCommonConfigs.BOOK_MERGING_ENABLED.set(merging);
            RechantedCommonConfigs.BOOK_MERGE_COST_PER_LEVEL.set(cost);
            RechantedCommonConfigs.ORPHANED_BOOK_GRINDSTONE_XP.set(xp);
            RechantedCommonConfigs.GRINDSTONE_LAPIS_REFUND_CHANCE.set(lapis);
            RechantedCommonConfigs.GRINDSTONE_RESULT_ITEM.set(item);
        }
    }
}
