package net.tagtart.rechanted.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import net.tagtart.rechanted.enchantment.ModEnchantments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class EnchantmentResourcesTest {
    @Test
    void everyDeclaredEnchantmentAndItsItemTagsArePackaged() throws Exception {
        try (ZipFile jar = new ZipFile(System.getProperty("rechanted.testJar"))) {
            var keys = enchantmentKeys();
            assertFalse(keys.isEmpty());
            for (var key : keys) {
                String path = "data/rechanted/enchantment/" + key.location().getPath() + ".json";
                JsonObject definition = readJson(jar, path);
                for (String field : List.of("supported_items", "primary_items")) {
                    if (definition.has(field) && definition.get(field).isJsonPrimitive()) {
                        String value = definition.get(field).getAsString();
                        if (value.startsWith("#rechanted:")) {
                            readJson(jar, "data/rechanted/tags/item/" + value.substring("#rechanted:".length()) + ".json");
                        }
                    }
                }
            }
        }
    }

    @Test
    @ExtendWith(EphemeralTestServerProvider.class)
    void enchantmentsRegisterAndTheDamageExclusiveTagResolves(MinecraftServer server) throws Exception {
        var registry = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (var key : enchantmentKeys()) {
            assertNotNull(registry.getHolderOrThrow(key));
        }
        for (var key : List.of(ModEnchantments.HELLS_FURY, ModEnchantments.VOIDS_BANE,
                Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS)) {
            assertTrue(registry.getHolderOrThrow(key).is(EnchantmentTags.DAMAGE_EXCLUSIVE),
                    () -> "Missing damage-exclusion member: " + key.location());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ResourceKey<Enchantment>> enchantmentKeys() throws IllegalAccessException {
        List<ResourceKey<Enchantment>> keys = new ArrayList<>();
        for (var field : ModEnchantments.class.getFields()) {
            if (field.getType() == ResourceKey.class) {
                keys.add((ResourceKey<Enchantment>) field.get(null));
            }
        }
        return keys;
    }

    private static JsonObject readJson(ZipFile jar, String path) throws IOException {
        var entry = jar.getEntry(path);
        assertNotNull(entry, "Missing packaged enchantment resource: " + path);
        try (var reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
