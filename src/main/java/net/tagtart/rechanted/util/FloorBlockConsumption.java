package net.tagtart.rechanted.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

public final class FloorBlockConsumption {
    private FloorBlockConsumption() {}

    /** Independent rolls whose combined chance of any floor damage matches the config. */
    static double perBlockChance(double purchaseChance, int blockCount) {
        if (blockCount <= 0 || purchaseChance <= 0) {
            return 0;
        }
        if (purchaseChance >= 1) {
            return 1;
        }
        // (1 - perBlockChance)^blockCount = 1 - purchaseChance.
        return -Math.expm1(Math.log1p(-purchaseChance) / blockCount);
    }

    public static void consume(Level level, BlockPos tablePos, BlockPos[] floorPositions,
                               double purchaseChance, Random random) {
        double breakChance = perBlockChance(purchaseChance, floorPositions.length);
        if (breakChance <= 0) {
            return;
        }

        HashSet<Integer> remainingBlocks = new HashSet<>();
        for (int i = 0; i < floorPositions.length; ++i) {
            remainingBlocks.add(i);
        }

        int underTableIndex = -1;
        for (int i = 0; i < floorPositions.length; ++i) {
            BlockPos position = floorPositions[i];
            if (random.nextDouble() < breakChance) {
                if (position.getX() == tablePos.getX() && position.getZ() == tablePos.getZ()) {
                    underTableIndex = i;
                } else {
                    level.destroyBlock(position, false);
                }
                remainingBlocks.remove(i);
            }
        }

        // Move a successful center roll to a surviving block when possible,
        // preserving both the number of losses and the table's support.
        if (underTableIndex != -1) {
            ArrayList<Integer> remainingBlocksIterable = new ArrayList<>(remainingBlocks);
            int randomBlock = underTableIndex;
            if (!remainingBlocksIterable.isEmpty()) {
                randomBlock = remainingBlocksIterable.get(random.nextInt(remainingBlocksIterable.size()));
            }
            level.destroyBlock(floorPositions[randomBlock], false);
        }
    }
}
