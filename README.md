# Rechanted

## Enchanting power providers

The Rechanted table uses NeoForge's `BlockState.getEnchantPowerBonus(level, pos)`
to total enchanting power in its existing 7×3×7 area: up to three blocks away
horizontally, from the table's height through two blocks above it. Rechanted's
existing placement rules apply; no vanilla air-gap requirement is added.

The `required_bookshelves` config keys remain compatible and now specify the
required enchanting power. A normal bookshelf supplies one power; fractional
and higher values supplied by other mods count at their actual value, with a
small float-precision tolerance at the requirement. The table
screen, activation effects, and server purchase checks use the same calculation.

By default, NeoForge gives blocks in `minecraft:enchantment_power_provider` one
power. A datapack can extend that tag, for example in
`data/minecraft/tags/block/enchantment_power_provider.json`:

```json
{
  "replace": false,
  "values": ["minecraft:chiseled_bookshelf"]
}
```

This example gives every chiseled bookshelf one power regardless of its contents.
Mods can override the hook to provide power based on block state, inventory,
or world position instead. Chiseled bookshelves are not added by Rechanted itself.

Only blocks supplying finite, positive power are eligible for break-chance rolls.
Custom providers can therefore be destroyed without block drops when purchasing
books. Inventory-bearing providers are not protected; do not use valuable storage
blocks as power sources unless you accept that risk.

Each purchase randomly selects sources covering the required power, whether or
not the rolls actually destroy them. Each selected source gets one roll at the
configured `book_break_chance`. If only part of the final source's power is needed,
its chance is reduced by that fraction. For example, a requirement of four uses
four ordinary shelves or eight half-power sources. A single eight-power source
gets half the configured break chance. This preserves normal bookshelf behavior
and the expected power lost without making fractional sources cheaper to consume.
Extra sources beyond the requirement do not add destruction rolls. Requirements,
XP, lapis, floor costs, and placement bounds otherwise remain unchanged.

Run `./gradlew build` to compile the mod and run the enchanting-power tests.

## Floor damage when purchasing books

Each rarity's `floor_break_chance` in `config/rechanted-common.toml` now means
the chance that a purchase destroys **one or more** floor blocks. For example,
`0.05` means 95% of purchases leave the entire floor intact. This value previously
applied separately to each of the nine blocks, giving about a 37% chance of damage
at the same setting. Existing config values now use the per-purchase meaning.

Floor blocks still roll independently, so multiple losses remain possible but
are much rarer. The per-block probability is derived from the configured overall
chance and the number of floor blocks. A successful roll beneath the table moves
to a surviving floor block when possible, preserving the table's support.
Both the tooltip and config descriptions explain the per-purchase chance.

## Generated item models

Generated client resources are tracked in `src/generated/resources` so a clean
checkout builds a complete mod without running data generation first. After
changing an item model generator, run `./gradlew runClientData` to refresh client
resources, then commit the changed JSON files. This headless data-generation task
does not open a game client or change server-side recipes and loot data.

The build tests inspect the runtime JAR for registered item models, book-rarity
overrides, and their local texture references. Missing models fail the build
instead of being discovered only after deployment.

## Generated enchantment definitions

The 18 generated enchantment definitions and their four supporting item tags are
also tracked in `src/generated/resources`. These files are required for Minecraft
to register the enchantments; the Java resource keys alone do not register them.
Refresh them with `./gradlew runData` after changing the enchantment bootstrap or
item-tag providers. Review the generated output before committing, because this
task also generates recipes and loot modifiers.

Build tests check that every declared enchantment and its custom item tags are
packaged, then load a test server to verify registration and the damage-exclusion
tag, including Hell's Fury, Void's Bane, and the vanilla damage enchantments.

## Generated recipes and loot modifiers

The generated server resources also include the enchanted-book conversion,
nine Smithing Gem netherite upgrades and their recipe advancements, and all five
global loot modifiers. Keep these JSON files in `src/generated/resources`;
registering a Java loot codec alone does not activate its modifier. Run
`./gradlew runData` to refresh all generated resources before building after
changing a provider. Normal builds use the checked-in files without launching
data generation.

Build tests inspect the runtime JAR's recipes, unlock advancements, and loot
modifier manifest, then load an ephemeral server to verify book conversion in
both crafting grids, all nine Smithing Gem upgrades, and the active loot
pipeline. Mending removal preserves Rechanted books rolled from configured
enchantment pools while still removing Mending from other enchanted loot.
Client resource checks cover block variants, model texture references, sound
files, shader stages, and all six book rarity models.

## Optional pending bonus data

Table saves and update tags decode optional bonus items with `ItemStack.parseOptional`.
Absent or empty data clears the previous bonus without emitting an invalid-item
error. Old saves containing `PendingGem` still load when the modern
`PendingBonus` field is absent; an explicit modern field takes precedence,
including an empty field. Nonempty malformed items retain the normal decoder
diagnostics. Empty bonuses are omitted when saving.

<!-- ashley-release-information -->

## Ashley's builds

This is an unofficial maintenance fork. Original authors retain ownership and credit. See the [upstream project](https://github.com/Rechanted-Team/Rechanted) for the original mod. Local changes do not imply upstream endorsement.

Licensing: **MIT (as declared in the original source snapshot)**. Existing license files, copyright notices, attribution and any separate asset terms remain in force. This fork does not relicense upstream work.

Provided **AS IS**, without warranty of any kind. Maintenance, updates and support are best effort; compatibility with every modpack is not guaranteed.

### Downloads and installation

Download a runtime `.jar` from [GitHub Releases](https://github.com/ashleywr/Rechanted/releases). Use the Minecraft version and loader named in that release and install its required dependencies. Do not install `-sources`, `-dev`, `-slim`, `-common` or test JARs as the mod.

Close Minecraft, back up your world, and place the matching runtime JAR in the instance's `mods/` directory. Replace older versions of this same mod rather than loading duplicates. For content mods, install matching versions on the server and its clients unless the release explicitly says client-only or server-only.

### Building and reporting problems

Use Java 21 and the checked-in Gradle wrapper:

```powershell
.\gradlew.bat build
```

Build outputs are under `build/libs/` or each loader subproject's `build/libs/`. Some projects require the local development dependencies described above; those dependencies are not included in the download.

For a problem report, include the exact release filename, Minecraft and loader versions, relevant mod versions, `logs/latest.log`, and a crash report if one was written. Remove private information before sharing logs. Remove the JAR to uninstall; worlds containing this mod's blocks, entities or world generation may require restoring a backup.
