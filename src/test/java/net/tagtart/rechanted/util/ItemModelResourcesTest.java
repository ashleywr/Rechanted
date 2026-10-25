package net.tagtart.rechanted.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import net.tagtart.rechanted.block.ModBlocks;
import net.tagtart.rechanted.item.ModItems;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class ItemModelResourcesTest {
    @Test
    void everyRegisteredItemHasAPackagedModelAndReadableTextures() throws IOException {
        try (ZipFile jar = openJar()) {
            // Deferred item IDs are available without constructing items or a world.
            assertFalse(ModItems.ITEMS.getEntries().isEmpty());
            Set<String> checkedModels = new HashSet<>();
            for (var item : ModItems.ITEMS.getEntries()) {
                assertModelResources(jar, item.getId().getNamespace() + ":item/" + item.getId().getPath(), checkedModels);
            }
        }
    }

    @Test
    void allBookRaritiesHaveTheExpectedModelOverrides() throws IOException {
        try (ZipFile jar = openJar()) {
            JsonObject model = readModel(jar, "rechanted:item/rechanted_book");
            String[] rarities = {"dusty", "simple", "unique", "elite", "ultimate", "legendary"};
            var overrides = model.getAsJsonArray("overrides");
            assertEquals(rarities.length, overrides.size());
            for (int i = 0; i < rarities.length; ++i) {
                JsonObject override = overrides.get(i).getAsJsonObject();
                assertEquals(i, override.getAsJsonObject("predicate").get("rechanted:book_rarity").getAsInt());
                assertEquals("rechanted:item/" + rarities[i], override.get("model").getAsString());
            }
        }
    }

    @Test
    void testClassesAndTheTestProviderTagAreNotPackaged() throws IOException {
        try (ZipFile jar = openJar()) {
            assertNull(jar.getEntry("net/tagtart/rechanted/util/ItemModelResourcesTest.class"));
            assertNull(jar.getEntry("net/tagtart/rechanted/util/EnchantingPowerTest.class"));
            assertNull(jar.getEntry("data/minecraft/tags/block/enchantment_power_provider.json"));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith(".cache/")),
                    "Local datagen caches must not ship in the runtime JAR");
        }
    }

    @Test
    @ExtendWith(EphemeralTestServerProvider.class)
    void everyRegisteredBlockStateHasAModel(MinecraftServer server) throws IOException {
        assertNotNull(server.registryAccess());
        try (ZipFile jar = openJar()) {
            for (var block : ModBlocks.BLOCKS.getEntries()) {
                String path = "assets/rechanted/blockstates/" + block.getId().getPath() + ".json";
                JsonObject definition = readJson(jar, path);
                var variants = definition.getAsJsonObject("variants");
                for (var state : block.get().getStateDefinition().getPossibleStates()) {
                    String variant = state.getValues().entrySet().stream()
                            .map(entry -> entry.getKey().getName() + "=" + entry.getValue())
                            .sorted().collect(java.util.stream.Collectors.joining(","));
                    assertTrue(variants.has(variant), "Missing block model variant: " + block.getId() + "#" + variant);
                    assertModelResources(jar, variants.getAsJsonObject(variant).get("model").getAsString(), new HashSet<>());
                }
            }
        }
    }

    @Test
    void soundFilesAndShaderProgramsArePackaged() throws IOException {
        try (ZipFile jar = openJar()) {
            JsonObject sounds = readJson(jar, "assets/rechanted/sounds.json");
            for (var event : sounds.entrySet()) {
                for (var sound : event.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                    if (sound.isJsonObject() && sound.getAsJsonObject().has("type")
                            && sound.getAsJsonObject().get("type").getAsString().equals("event")) {
                        continue;
                    }
                    String id = sound.isJsonPrimitive() ? sound.getAsString() : sound.getAsJsonObject().get("name").getAsString();
                    if (id.startsWith("rechanted:")) {
                        String path = "assets/rechanted/sounds/" + id.substring("rechanted:".length()) + ".ogg";
                        var file = jar.getEntry(path);
                        assertNotNull(file, "Missing sound: " + path);
                        try (var stream = jar.getInputStream(file)) {
                            assertEquals("OggS", new String(stream.readNBytes(4), StandardCharsets.US_ASCII), "Invalid sound: " + path);
                        }
                    }
                }
            }
            for (String shader : new String[]{"cloned_item_shader", "ench_table_line_shader", "ench_table_grid_shader"}) {
                JsonObject definition = readJson(jar, "assets/rechanted/shaders/core/shaders/program/" + shader + ".json");
                for (String stage : new String[]{"vertex", "fragment"}) {
                    String[] id = definition.get(stage).getAsString().split(":", 2);
                    String path = "assets/" + id[0] + "/shaders/core/" + id[1] + (stage.equals("vertex") ? ".vsh" : ".fsh");
                    assertNotNull(jar.getEntry(path), "Missing shader stage: " + path);
                }
            }
        }
    }

    private static ZipFile openJar() throws IOException {
        return new ZipFile(System.getProperty("rechanted.testJar"));
    }

    private static JsonObject readModel(ZipFile jar, String modelId) throws IOException {
        String[] id = modelId.split(":", 2);
        String path = "assets/" + id[0] + "/models/" + id[1] + ".json";
        return readJson(jar, path);
    }

    private static JsonObject readJson(ZipFile jar, String path) throws IOException {
        var entry = jar.getEntry(path);
        assertNotNull(entry, "Missing packaged model: " + path);
        try (var reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static void assertModelResources(ZipFile jar, String modelId, Set<String> checkedModels) throws IOException {
        if (!checkedModels.add(modelId)) {
            return;
        }
        JsonObject model = readModel(jar, modelId);
        if (model.has("parent") && model.get("parent").getAsString().startsWith("rechanted:")) {
            assertModelResources(jar, model.get("parent").getAsString(), checkedModels);
        }
        if (model.has("textures")) {
            for (var texture : model.getAsJsonObject("textures").entrySet()) {
                String reference = texture.getValue().getAsString();
                if (!reference.startsWith("rechanted:")) {
                    continue; // Vanilla textures and #references are resolved by Minecraft.
                }
                String path = "assets/rechanted/textures/" + reference.substring("rechanted:".length()) + ".png";
                var entry = jar.getEntry(path);
                assertNotNull(entry, "Missing packaged texture: " + path);
                try (var stream = jar.getInputStream(entry)) {
                    assertNotNull(ImageIO.read(stream), "Unreadable texture: " + path);
                }
            }
        }
        if (model.has("elements")) {
            for (var element : model.getAsJsonArray("elements")) {
                for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                    String reference = face.getValue().getAsJsonObject().get("texture").getAsString();
                    Set<String> visited = new HashSet<>();
                    while (reference.startsWith("#")) {
                        assertTrue(visited.add(reference), "Texture reference cycle in " + modelId);
                        String name = reference.substring(1);
                        assertTrue(model.has("textures") && model.getAsJsonObject("textures").has(name),
                                "Unresolved texture " + reference + " in " + modelId);
                        reference = model.getAsJsonObject("textures").get(name).getAsString();
                    }
                }
            }
        }
        if (model.has("overrides")) {
            for (var override : model.getAsJsonArray("overrides")) {
                assertModelResources(jar, override.getAsJsonObject().get("model").getAsString(), checkedModels);
            }
        }
    }
}
