# Rolled book operations

Rechanted books retain their enchantment, level and application success rate.
They are not converted into vanilla enchanted books by any of these operations.

## Recycling

Put one rolled book in either grindstone input and leave the other input empty.
Taking the output consumes the book and returns `grindstone_result_item`, plus
the existing rarity-specific XP refund. A registered enchantment removed from
the rarity pools instead returns `orphaned_book_grindstone_xp` (default: 2).
Empty or multi-enchantment Rechanted books are rejected rather than guessed at.
An invalid configured output item falls back to paper.

`grindstone_lapis_refund_chance` optionally returns one lapis in the consumed
book's vacated input slot. Its default is 0, preserving existing balance. The
chance is evaluated only in the consumption event, never in the output preview.
The output item and XP are still awarded normally.

This uses NeoForge's grindstone events without referencing Create classes. Create
Enchantment Industry 2.5.4 uses those events and collects their input remainders,
item output and XP. There is no separate crushing recipe with different refunds.

## Merging

Enable `book_merging_enabled` (default: false) to combine two books of the same
enchantment and level in an anvil. Each input must contain exactly one book and
one stored enchantment. The output level increases by one, capped by both the
enchantment's maximum and the highest positively weighted level in its current
rarity pool. An enchantment removed from the pools cannot be promoted.

The cost is `book_merge_cost_per_level` XP **levels** multiplied by the output
enchantment level. The default multiplier is 1. Normal anvil affordability and
the survival “Too Expensive” limit still apply. Both inputs are consumed only
when taking the result; previewing has no side effects.

The output keeps the lower input success rate. A missing rate is conservatively
treated as 0, and rates are clamped to 0–100. Rerolled, cloned and already-copied
flags from either input are preserved. Merging cannot reset gem restrictions.

## JEI and EMI

EMI treats disabled vanilla enchanted books as one ingredient so their uses lookup
finds the Mysterious Book crafting conversion regardless of stored enchantments.
Rolled Rechanted books still use enchantment and level to identify their recipes.

Both integrations are optional. Their categories use the same operation catalog:
application (including matching-level equipment upgrades), recycling, merging,
gem interactions and book rolls. Recipe lookup
distinguishes enchantment and level, but ignores success rate, names and gem
history, so an actual rolled book can find the relevant examples.

Equipment examples use real enchantment-support checks on unenchanted default
stacks. Modular equipment may need a different configuration; the displays state
this limitation rather than advertising every base tool as compatible. Random
rolls, example success percentages, XP points versus XP levels, and conditional
gem restrictions are labeled. These are informational operation displays, not
automatic crafting/transfer handlers. EMI recipe-tree resolution is disabled
because treating the examples as guaranteed conversions would give incorrect
resource calculations.

Viewer data is constructed when world registries become available. As with
Rechanted's existing common configuration, client and server should use matching
pack settings. Reload the viewers/rejoin after changing those settings.

## Verification

Run `gradlew.bat build` for headless registry-backed operation tests and packaged
resource checks. This does not launch a game client. In-game anvil, grindstone,
machine ejection and viewer rendering still require a separately authorized
runtime check; compilation and metadata tests do not verify their visual layout.
