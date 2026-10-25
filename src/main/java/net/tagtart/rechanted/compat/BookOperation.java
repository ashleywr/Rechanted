package net.tagtart.rechanted.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** A display of an actual operation, not a crafting recipe or an automatic transfer handler. */
public record BookOperation(ResourceLocation id, Kind kind, List<ItemStack> firstInputs,
        List<ItemStack> secondInputs, List<ItemStack> outputAlternatives, ItemStack extraOutput,
        double extraOutputChance, List<Component> details) {
    public BookOperation {
        firstInputs = List.copyOf(firstInputs);
        secondInputs = List.copyOf(secondInputs);
        outputAlternatives = List.copyOf(outputAlternatives);
        details = List.copyOf(details);
    }

    public enum Kind {
        APPLY("apply"), RECYCLE("recycle"), MERGE("merge"), GEMS("gems"), ACQUIRE("acquire"), REVEAL("reveal");

        public final String key;

        Kind(String key) {
            this.key = key;
        }

        public Component title() {
            return Component.translatable("recipe.rechanted." + key);
        }
    }
}
