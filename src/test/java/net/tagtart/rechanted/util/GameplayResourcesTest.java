package net.tagtart.rechanted.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForgeEventHandler;
import net.neoforged.neoforge.common.loot.LootModifierManager;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import net.tagtart.rechanted.config.RechantedCommonConfigs;
import net.tagtart.rechanted.item.ModItems;
import net.tagtart.rechanted.loot.ModLootModifiers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameplayResourcesTest {
    private static final List<String> EQUIPMENT = List.of(
            "sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots");

    @Test
    void recipesUnlocksAndEveryRegisteredLootModifierArePackaged() throws Exception {
        try (ZipFile jar = new ZipFile(System.getProperty("rechanted.testJar"))) {
            List<String> recipes = new ArrayList<>(List.of("mysterious_book"));
            EQUIPMENT.forEach(item -> recipes.add("netherite_" + item + "_smithing_gem"));
            for (String recipe : recipes) {
                JsonObject definition = readJson(jar, "data/rechanted/recipe/" + recipe + ".json");
                String category = recipe.equals("mysterious_book") ? "misc"
                        : List.of("pickaxe", "axe", "shovel", "hoe").stream().anyMatch(item -> recipe.equals("netherite_" + item + "_smithing_gem"))
                        ? "tools" : "combat";
                JsonObject unlock = readJson(jar, "data/rechanted/advancement/recipes/" + category + "/" + recipe + ".json");
                assertEquals("rechanted:" + recipe, unlock.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
                assertTrue(definition.has("result"));
            }

            JsonObject manifest = readJson(jar, "data/neoforge/loot_modifiers/global_loot_modifiers.json");
            assertFalse(manifest.get("replace").getAsBoolean(), "Must retain other mods' loot modifiers");
            Set<String> types = new HashSet<>();
            Set<String> entries = new HashSet<>();
            for (var entry : manifest.getAsJsonArray("entries")) {
                ResourceLocation id = ResourceLocation.parse(entry.getAsString());
                assertTrue(entries.add(id.toString()), "Duplicate loot modifier: " + id);
                JsonObject modifier = readJson(jar, "data/" + id.getNamespace() + "/loot_modifiers/" + id.getPath() + ".json");
                assertTrue(modifier.has("conditions"));
                assertTrue(types.add(modifier.get("type").getAsString()));
            }
            Set<String> registeredTypes = new HashSet<>();
            ModLootModifiers.LOOT_MODIFIERS.getEntries().forEach(codec -> registeredTypes.add(codec.getId().toString()));
            assertFalse(registeredTypes.isEmpty());
            assertEquals(registeredTypes, types, "Every registered handler must have active loot data");
        }
    }

    @Test
    @ExtendWith(EphemeralTestServerProvider.class)
    void enchantedBooksConvertInBothCraftingGridsAndAllGemUpgradesLoad(MinecraftServer server) {
        ServerLevel level = mock(ServerLevel.class);
        when(level.registryAccess()).thenReturn(server.registryAccess());
        ItemStack book = enchantedStack(server, new ItemStack(Items.ENCHANTED_BOOK), true);
        for (int size : List.of(2, 3)) {
            List<ItemStack> slots = new ArrayList<>();
            for (int i = 0; i < size * size; i++) {
                slots.add(i == size * size - 1 ? book.copy() : ItemStack.EMPTY);
            }
            CraftingInput input = CraftingInput.of(size, size, slots);
            var recipe = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
            assertEquals(ResourceLocation.parse("rechanted:mysterious_book"), recipe.id());
            ItemStack result = recipe.value().assemble(input, server.registryAccess());
            assertTrue(result.is(ModItems.MYSTERIOUS_BOOK.get()));
            assertEquals(1, result.getCount());
            assertFalse(result.has(DataComponents.STORED_ENCHANTMENTS));
        }
        for (String equipment : EQUIPMENT) {
            ItemStack base = enchantedStack(server, new ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("diamond_" + equipment))), false);
            SmithingRecipeInput input = new SmithingRecipeInput(new ItemStack(ModItems.SMITHING_GEM.get()), base,
                    new ItemStack(Items.NETHERITE_INGOT));
            var recipe = server.getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, level).orElseThrow();
            assertEquals(ResourceLocation.parse("rechanted:netherite_" + equipment + "_smithing_gem"), recipe.id());
            ItemStack result = recipe.value().assemble(input, server.registryAccess());
            assertEquals(ResourceLocation.withDefaultNamespace("netherite_" + equipment),
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem()));
            assertEquals(base.get(DataComponents.ENCHANTMENTS), result.get(DataComponents.ENCHANTMENTS));
        }
    }

    @Test
    @ExtendWith(EphemeralTestServerProvider.class)
    void loadedLootPipelineReplacesVanillaBooksAndKeepsRechantedMending(MinecraftServer server) throws Exception {
        var accessor = NeoForgeEventHandler.class.getDeclaredMethod("getLootModifierManager");
        accessor.setAccessible(true);
        var modifiers = ((LootModifierManager) accessor.invoke(null)).getAllLootMods();
        assertEquals(5, modifiers.stream().filter(modifier -> modifier.getClass().getPackageName().equals("net.tagtart.rechanted.loot")).count());
        ServerLevel level = mock(ServerLevel.class);
        when(level.registryAccess()).thenReturn(server.registryAccess());
        when(level.getServer()).thenReturn(server);
        LootContext context = new LootContext.Builder(new LootParams.Builder(level).create(LootContextParamSets.EMPTY))
                .withOptionalRandomSource(RandomSource.create(42)).create(Optional.empty());
        double gemChance = RechantedCommonConfigs.WORLD_LOOT_GEM_DROP_CHANCE.get();
        assertTrue(RechantedCommonConfigs.REMOVE_MENDING_ENABLED.get());
        try {
            RechantedCommonConfigs.WORLD_LOOT_GEM_DROP_CHANCE.set(0.0);
            ItemStack vanillaBook = enchantedStack(server, new ItemStack(Items.ENCHANTED_BOOK), true);
            var replacement = CommonHooks.modifyLoot(ResourceLocation.withDefaultNamespace("chests/simple_dungeon"),
                    new ObjectArrayList<>(List.of(vanillaBook)), context);
            assertEquals(1, replacement.size());
            assertTrue(replacement.getFirst().is(ModItems.RECHANTED_BOOK.get()));
            assertFalse(replacement.getFirst().getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty());

            ItemStack rechantedBook = enchantedStack(server, new ItemStack(ModItems.RECHANTED_BOOK.get()), true);
            ItemEnchantments originalEnchantments = rechantedBook.get(DataComponents.STORED_ENCHANTMENTS);
            ItemStack equipment = enchantedStack(server, new ItemStack(Items.GOLDEN_PICKAXE), false);
            var loot = CommonHooks.modifyLoot(ResourceLocation.withDefaultNamespace("chests/ruined_portal"),
                    new ObjectArrayList<>(List.of(rechantedBook, equipment)), context);
            assertEquals(originalEnchantments, loot.getFirst().get(DataComponents.STORED_ENCHANTMENTS));
            assertFalse(loot.getFirst().getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty(),
                    "Mending from the configured Rechanted book pools must survive the full loot pipeline");
            assertTrue(loot.get(1).getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty(),
                    "Mending removal still applies to vanilla equipment excluded from replacement");
        } finally {
            RechantedCommonConfigs.WORLD_LOOT_GEM_DROP_CHANCE.set(gemChance);
        }
    }

    private static ItemStack enchantedStack(MinecraftServer server, ItemStack stack, boolean stored) {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(server.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.MENDING), 1);
        stack.set(stored ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        return stack;
    }

    private static JsonObject readJson(ZipFile jar, String path) throws Exception {
        var entry = jar.getEntry(path);
        assertNotNull(entry, "Missing packaged gameplay resource: " + path);
        try (var reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
