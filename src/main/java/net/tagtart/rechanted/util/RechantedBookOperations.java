package net.tagtart.rechanted.util;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.tagtart.rechanted.component.ModDataComponents;
import net.tagtart.rechanted.config.RechantedCommonConfigs;
import net.tagtart.rechanted.item.ModItems;

import java.util.Optional;

/** Rules shared by gameplay and recipe-viewer displays. Does not mutate input stacks. */
public final class RechantedBookOperations {
    private RechantedBookOperations() {}

    public record Book(Holder<Enchantment> enchantment, int level) {}
    public record XpRange(int min, int max) {}
    public record Merge(ItemStack output, int levelCost) {}

    public static Optional<Book> readBook(ItemStack stack) {
        if (!stack.is(ModItems.RECHANTED_BOOK.get()) || stack.getCount() != 1) {
            return Optional.empty();
        }
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchantments.size() != 1) {
            return Optional.empty();
        }
        var entry = enchantments.entrySet().iterator().next();
        if (entry.getIntValue() < 1 || entry.getIntValue() > 255 || entry.getKey().unwrapKey().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Book(entry.getKey(), entry.getIntValue()));
    }

    public static Optional<BookRarityProperties> rarity(Book book) {
        return Optional.ofNullable(UtilFunctions.getPropertiesFromEnchantment(
                book.enchantment().unwrapKey().orElseThrow().location().toString()));
    }

    public static XpRange salvageXp(Book book) {
        return rarity(book).map(properties -> new XpRange(
                Math.max(0, Math.min(properties.minGrindstoneXP, properties.maxGrindstoneXP)),
                Math.max(0, Math.max(properties.minGrindstoneXP, properties.maxGrindstoneXP))))
                .orElseGet(() -> new XpRange(RechantedCommonConfigs.ORPHANED_BOOK_GRINDSTONE_XP.get(),
                        RechantedCommonConfigs.ORPHANED_BOOK_GRINDSTONE_XP.get()));
    }

    public static ItemStack salvageItem() {
        ResourceLocation id = ResourceLocation.tryParse(RechantedCommonConfigs.GRINDSTONE_RESULT_ITEM.get());
        // A malformed or removed item must not strand existing books in a grindstone.
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.get(id) == Items.AIR) {
            return new ItemStack(Items.PAPER);
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    public static int maximumMergeLevel(Book book) {
        String id = book.enchantment().unwrapKey().orElseThrow().location().toString();
        // Use the same first matching rarity as existing book naming and recycling.
        // Removing a pool entry permits salvage, not promotion into a new enchantment route.
        var properties = rarity(book);
        if (properties.isEmpty()) {
            return 0;
        }
        int configuredCap = 0;
        for (var entry : properties.get().enchantmentPool) {
            if (!entry.enchantment.equals(id) || entry.weight <= 0) {
                continue;
            }
            for (int index = 0; index < entry.levelWeights.size(); index++) {
                int level = entry.minLevel + index;
                if (entry.levelWeights.get(index) > 0 && level >= 1 && level <= 255) {
                    configuredCap = Math.max(configuredCap, level);
                }
            }
        }
        return Math.min(book.enchantment().value().getMaxLevel(), configuredCap);
    }

    public static int mergeCost(int outputLevel) {
        return Math.multiplyExact(outputLevel, RechantedCommonConfigs.BOOK_MERGE_COST_PER_LEVEL.get());
    }

    public static Optional<Merge> merge(ItemStack left, ItemStack right) {
        if (!RechantedCommonConfigs.BOOK_MERGING_ENABLED.get()) {
            return Optional.empty();
        }
        Optional<Book> first = readBook(left);
        Optional<Book> second = readBook(right);
        if (first.isEmpty() || second.isEmpty()) {
            return Optional.empty();
        }
        Book book = first.get();
        if (!book.enchantment().equals(second.get().enchantment()) || book.level() != second.get().level()
                || book.level() >= maximumMergeLevel(book)) {
            return Optional.empty();
        }
        ItemStack output = left.copyWithCount(1);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(book.enchantment(), book.level() + 1);
        output.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable().withTooltip(false));
        output.set(ModDataComponents.SUCCESS_RATE, Math.min(successRate(left), successRate(right)));
        output.set(ModDataComponents.REROLLED_SUCCESS_RATE,
                left.getOrDefault(ModDataComponents.REROLLED_SUCCESS_RATE, false)
                        || right.getOrDefault(ModDataComponents.REROLLED_SUCCESS_RATE, false));
        // Combining must not turn a copy or an already-copied book into a fresh cloning source.
        output.set(ModDataComponents.IS_CLONE, left.getOrDefault(ModDataComponents.IS_CLONE, false)
                || right.getOrDefault(ModDataComponents.IS_CLONE, false));
        output.set(ModDataComponents.HAS_BEEN_CLONED, left.getOrDefault(ModDataComponents.HAS_BEEN_CLONED, false)
                || right.getOrDefault(ModDataComponents.HAS_BEEN_CLONED, false));
        output.set(ModDataComponents.ANNOUNCE_ON_FOUND, false);
        return Optional.of(new Merge(output, mergeCost(book.level() + 1)));
    }

    public static int successRate(ItemStack book) {
        return Math.clamp(book.getOrDefault(ModDataComponents.SUCCESS_RATE, 0), 0, 100);
    }

    public static ItemStack createBook(Holder<Enchantment> enchantment, int level, int successRate) {
        ItemStack stack = new ItemStack(ModItems.RECHANTED_BOOK.get());
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment, level);
        stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable().withTooltip(false));
        stack.set(ModDataComponents.SUCCESS_RATE, Math.clamp(successRate, 0, 100));
        return stack;
    }

    /** Recipe lookup ignores success rates, names and gem history, but never enchantment identity or level. */
    public static String recipeIdentity(ItemStack stack) {
        return stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet().stream()
                .map(entry -> entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("unregistered")
                        + "@" + entry.getIntValue()).sorted().collect(java.util.stream.Collectors.joining(";"));
    }
}
