package net.tagtart.rechanted.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.tagtart.rechanted.Rechanted;
import net.tagtart.rechanted.block.ModBlocks;
import net.tagtart.rechanted.component.ModDataComponents;
import net.tagtart.rechanted.config.RechantedCommonConfigs;
import net.tagtart.rechanted.item.ModItems;
import net.tagtart.rechanted.util.BookRarityProperties;
import net.tagtart.rechanted.util.RechantedBookOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One source of operation metadata for JEI and EMI; built after world registries are available. */
public final class BookOperationCatalog {
    private BookOperationCatalog() {}

    public static List<BookOperation> create(HolderLookup.Provider registries, List<ItemStack> equipment) {
        List<BookOperation> operations = new ArrayList<>();
        var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        enchantments.listElements().forEach(holder -> {
            // Include non-pool enchantments too: existing books still have application and salvage uses.
            String id = holder.key().location().toString();
            int poolCap = java.util.Arrays.stream(BookRarityProperties.getAllProperties())
                    .flatMap(rarity -> rarity.enchantmentPool.stream()).filter(entry -> entry.enchantment.equals(id))
                    .mapToInt(entry -> entry.maxLevel).max().orElse(0);
            int exampleRate = RechantedBookOperations.rarity(new RechantedBookOperations.Book(holder, 1))
                    .map(rarity -> rarity.minSuccess).orElse(50);
            for (int level = 1; level <= Math.min(255, Math.max(poolCap, holder.value().getMaxLevel())); level++) {
                addBookUses(operations, RechantedBookOperations.createBook(holder, level, exampleRate), equipment);
            }
        });
        addAcquisition(operations, registries);
        return List.copyOf(operations);
    }

    public static void addBookUses(List<BookOperation> operations, ItemStack stack, List<ItemStack> equipment) {
        var parsed = RechantedBookOperations.readBook(stack);
        if (parsed.isEmpty()) {
            return;
        }
        var book = parsed.get();
        var xp = RechantedBookOperations.salvageXp(book);
        double refundChance = RechantedCommonConfigs.GRINDSTONE_LAPIS_REFUND_CHANCE.get();
        operations.add(operation(book, "recycle", BookOperation.Kind.RECYCLE, List.of(stack), List.of(),
                List.of(RechantedBookOperations.salvageItem()),
                refundChance > 0 ? new ItemStack(Items.LAPIS_LAZULI) : ItemStack.EMPTY, refundChance,
                text("salvage_xp", xp.min(), xp.max()), text("salvage_consumed"),
                text("lapis_remainder", percent(refundChance)), text("automation")));

        List<ItemStack> targets = equipment.stream().filter(target -> !target.isEmpty()
                && !target.isEnchanted() && target.isEnchantable() && target.supportsEnchantment(book.enchantment()))
                .map(ItemStack::copy).toList();
        if (!targets.isEmpty()) {
            List<ItemStack> results = targets.stream().map(target -> {
                ItemStack result = target.copy();
                ItemEnchantments.Mutable enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                enchants.set(book.enchantment(), book.level());
                result.set(DataComponents.ENCHANTMENTS, enchants.toImmutable());
                return result;
            }).toList();
            operations.add(operation(book, "apply", BookOperation.Kind.APPLY, List.of(stack), targets, results,
                    ItemStack.EMPTY, 0, text("drag_book"), text("application_chance"),
                    text("book_consumed"), text("module_support"), text("example_rate")));
            if (book.level() < book.enchantment().value().getMaxLevel()) {
                List<ItemStack> upgrades = results.stream().map(target -> {
                    ItemStack upgraded = target.copy();
                    ItemEnchantments.Mutable enchants = new ItemEnchantments.Mutable(upgraded.getEnchantments());
                    enchants.set(book.enchantment(), book.level() + 1);
                    upgraded.set(DataComponents.ENCHANTMENTS, enchants.toImmutable());
                    return upgraded;
                }).toList();
                operations.add(operation(book, "apply_upgrade", BookOperation.Kind.APPLY, List.of(stack), results, upgrades,
                        ItemStack.EMPTY, 0, text("drag_book"), text("gear_upgrade"), text("application_chance"),
                        text("book_consumed"), text("example_rate")));
            }
        } else {
            operations.add(operation(book, "apply", BookOperation.Kind.APPLY, List.of(stack), List.of(), List.of(),
                    ItemStack.EMPTY, 0, text("drag_book"), text("no_default_target"),
                    text("module_support"), text("application_chance"), text("book_consumed")));
        }

        RechantedBookOperations.merge(stack, stack).ifPresent(merge ->
                operations.add(operation(book, "merge", BookOperation.Kind.MERGE, List.of(stack), List.of(stack.copy()),
                        List.of(merge.output()), ItemStack.EMPTY, 0, text("merge_cost", merge.levelCost()),
                        text("merge_matching"), text("merge_rate"), text("merge_history"))));

        // Rerolling requires a currently configured rarity, just as the gem implementation does.
        RechantedBookOperations.rarity(book).ifPresent(rarity -> {
            ItemStack rerolled = stack.copy();
            rerolled.set(ModDataComponents.REROLLED_SUCCESS_RATE, true);
            rerolled.set(ModDataComponents.SUCCESS_RATE, Math.clamp(rarity.minSuccess, 0, 100));
            operations.add(operation(book, "chance_gem", BookOperation.Kind.GEMS,
                    List.of(new ItemStack(ModItems.CHANCE_GEM.get())), List.of(stack), List.of(rerolled), ItemStack.EMPTY, 0,
                    text("drag_gem"), text("reroll_range", rarity.minSuccess, rarity.maxSuccess),
                    text("reroll_once"), text("chance_gem_consumed")));
            operations.add(operation(book, "shiny_chance_gem", BookOperation.Kind.GEMS,
                    List.of(new ItemStack(ModItems.SHINY_CHANCE_GEM.get())), List.of(stack), List.of(rerolled.copy()),
                    ItemStack.EMPTY, 0, text("drag_gem"), text("reroll_range", rarity.minSuccess, rarity.maxSuccess),
                    text("reroll_once"), text("shiny_gem_reuse")));
        });
        ItemStack clone = RechantedBookOperations.createBook(book.enchantment(), book.level(),
                RechantedBookOperations.successRate(stack));
        clone.set(ModDataComponents.IS_CLONE, true);
        ItemStack original = stack.copy();
        original.set(ModDataComponents.HAS_BEEN_CLONED, true);
        operations.add(operation(book, "clone_gem", BookOperation.Kind.GEMS,
                List.of(new ItemStack(ModItems.CLONE_GEM.get())), List.of(stack), List.of(clone), original, 1,
                text("drag_gem"), text("clone_rule"), text("clone_consumed"), text("clone_rate")));
    }

    private static void addAcquisition(List<BookOperation> operations, HolderLookup.Provider registries) {
        BookRarityProperties[] rarities = BookRarityProperties.getAllProperties();
        double totalRarityWeight = java.util.Arrays.stream(rarities).mapToDouble(r -> Math.max(0, r.worldSpawnWeight)).sum();
        for (int tier = 0; tier < rarities.length; tier++) {
            BookRarityProperties rarity = rarities[tier];
            for (var entry : rarity.enchantmentPool) {
                if (entry.weight <= 0 || entry.levelWeightsSum <= 0 || rarity.enchantmentPoolTotalWeights <= 0) {
                    continue;
                }
                ResourceLocation enchantmentId = ResourceLocation.tryParse(entry.enchantment);
                if (enchantmentId == null) {
                    continue;
                }
                var holder = registries.lookupOrThrow(Registries.ENCHANTMENT)
                        .get(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, enchantmentId));
                if (holder.isEmpty()) {
                    continue;
                }
                for (int index = 0; index < entry.levelWeights.size(); index++) {
                    if (entry.levelWeights.get(index) <= 0) {
                        continue;
                    }
                    int level = entry.minLevel + index;
                    if (level < 1 || level > 255) {
                        continue;
                    }
                    double chance = entry.weight / (double) rarity.enchantmentPoolTotalWeights
                            * entry.levelWeights.get(index) / entry.levelWeightsSum;
                    ItemStack output = RechantedBookOperations.createBook(holder.get(), level, rarity.minSuccess);
                    var book = new RechantedBookOperations.Book(holder.get(), level);
                    if (tier > 0) {
                        operations.add(operation(book, "table/tier_" + tier, BookOperation.Kind.ACQUIRE,
                                List.of(new ItemStack(Items.LAPIS_LAZULI, rarity.requiredLapis)), List.of(), List.of(output),
                                ItemStack.EMPTY, 0, text("table_roll", rarity.key, percent(chance)),
                                text("table_cost", rarity.requiredExp, rarity.requiredLapis),
                                text("table_structure", rarity.requiredBookShelves, rarity.floorBlock.getName()),
                                text("roll_rate", rarity.minSuccess, rarity.maxSuccess), text("table_destruction")));
                    } else {
                        operations.add(operation(book, "dusty_reveal", BookOperation.Kind.REVEAL,
                                List.of(new ItemStack(ModItems.DUSTY_MYSTERIOUS_BOOK.get())), List.of(), List.of(output),
                                ItemStack.EMPTY, 0, text("reveal", percent(chance)),
                                text("roll_rate", rarity.minSuccess, rarity.maxSuccess)));
                    }
                    if (rarity.worldSpawnWeight > 0 && totalRarityWeight > 0) {
                        operations.add(operation(book, "mysterious_reveal/tier_" + tier, BookOperation.Kind.REVEAL,
                                List.of(new ItemStack(ModItems.MYSTERIOUS_BOOK.get())), List.of(), List.of(output),
                                ItemStack.EMPTY, 0, text("reveal", percent(chance * rarity.worldSpawnWeight / totalRarityWeight)),
                                text("roll_rate", rarity.minSuccess, rarity.maxSuccess)));
                    }
                }
            }
        }
    }

    public static List<ItemStack> defaultEquipment() {
        return BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR)
                .map(item -> item.getDefaultInstance())
                .filter(stack -> stack.isEnchantable() && !stack.is(ModItems.RECHANTED_BOOK.get())).toList();
    }

    public static List<ItemStack> workstations(BookOperation.Kind kind) {
        return switch (kind) {
            case RECYCLE -> {
                List<ItemStack> stations = new ArrayList<>(List.of(new ItemStack(Items.GRINDSTONE)));
                // Discover optional machinery by registry ID, never by linking optional mod classes.
                for (String path : List.of("mechanical_grindstone", "grindstone_drain")) {
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath("create_enchantment_industry", path);
                    if (BuiltInRegistries.ITEM.containsKey(id)) {
                        stations.add(new ItemStack(BuiltInRegistries.ITEM.get(id)));
                    }
                }
                yield List.copyOf(stations);
            }
            case MERGE -> List.of(new ItemStack(Items.ANVIL));
            case ACQUIRE -> List.of(new ItemStack(ModBlocks.RECHANTED_TABLE_BLOCK.get()));
            default -> List.of();
        };
    }

    public static ItemStack icon(BookOperation.Kind kind) {
        return switch (kind) {
            case APPLY -> new ItemStack(Items.IRON_SWORD);
            case RECYCLE -> new ItemStack(Items.GRINDSTONE);
            case MERGE -> new ItemStack(Items.ANVIL);
            case GEMS -> new ItemStack(ModItems.CHANCE_GEM.get());
            case ACQUIRE -> new ItemStack(ModBlocks.RECHANTED_TABLE_BLOCK.get());
            case REVEAL -> new ItemStack(ModItems.MYSTERIOUS_BOOK.get());
        };
    }

    private static BookOperation operation(RechantedBookOperations.Book book, String action, BookOperation.Kind kind,
            List<ItemStack> first, List<ItemStack> second, List<ItemStack> output, ItemStack extra, double extraChance,
            Component... details) {
        ResourceLocation enchantment = book.enchantment().unwrapKey().orElseThrow().location();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Rechanted.MOD_ID,
                "/book_operations/" + action + "/" + enchantment.getNamespace() + "/" + enchantment.getPath() + "/" + book.level());
        return new BookOperation(id, kind, first, second, output, extra, extraChance, List.of(details));
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("recipe.rechanted.detail." + key, arguments);
    }

    private static String percent(double chance) {
        return String.format(Locale.ROOT, "%.2f%%", chance * 100);
    }
}
