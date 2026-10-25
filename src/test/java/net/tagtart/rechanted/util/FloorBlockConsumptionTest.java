package net.tagtart.rechanted.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FloorBlockConsumptionTest {
    private static final BlockPos TABLE = new BlockPos(10, 100, 10);

    @ParameterizedTest
    @ValueSource(doubles = {0.03, 0.045, 0.05, 0.075, 0.09})
    void configuredChanceMatchesTheProbabilityOfAnyFloorDamage(double purchaseChance) {
        double blockChance = FloorBlockConsumption.perBlockChance(purchaseChance, 9);
        assertEquals(1 - purchaseChance, Math.pow(1 - blockChance, 9), 1e-12,
                "The displayed chance must apply to the whole floor");
    }

    @Test
    void rollsBetweenTheNewPerBlockRateAndFivePercentLeaveTheFloorIntact() {
        Level level = mock(Level.class);
        Random random = mock(Random.class);
        when(random.nextDouble()).thenReturn(0.006);

        FloorBlockConsumption.consume(level, TABLE, floor(), 0.05, random);

        verify(random, times(9)).nextDouble();
        verifyNoInteractions(level);
    }

    @Test
    void severalSuccessfulRollsCanStillBreakSeveralBlocks() {
        Level level = mock(Level.class);
        Random random = mock(Random.class);
        when(random.nextDouble()).thenReturn(0.001, 0.001, 0.001, 0.9);
        BlockPos[] floor = floor();

        FloorBlockConsumption.consume(level, TABLE, floor, 0.05, random);

        for (int i = 0; i < 3; ++i) {
            verify(level).destroyBlock(floor[i], false);
        }
        verifyNoMoreInteractions(level);
    }

    @Test
    void successfulCenterRollMovesToASurvivingBlockWithoutAddingALoss() {
        Level level = mock(Level.class);
        Random random = mock(Random.class);
        when(random.nextDouble()).thenReturn(0.001, 0.9, 0.9, 0.9, 0.001, 0.9);
        BlockPos[] floor = floor();

        FloorBlockConsumption.consume(level, TABLE, floor, 0.05, random);

        verify(level, times(2)).destroyBlock(any(BlockPos.class), eq(false));
        verify(level).destroyBlock(floor[0], false);
        verify(level, never()).destroyBlock(TABLE.below(), false);
        verify(random).nextInt(7);
    }

    @Test
    void zeroChanceAndEmptyFloorDoNotRollOrDestroyAnything() {
        Level level = mock(Level.class);
        Random random = mock(Random.class);
        FloorBlockConsumption.consume(level, TABLE, floor(), 0, random);
        FloorBlockConsumption.consume(level, TABLE, new BlockPos[0], 0.05, random);
        verifyNoInteractions(level, random);
    }

    @Test
    void certainDamageStillHandlesTheCenterWhenEveryBlockBreaks() {
        Level level = mock(Level.class);
        Random random = mock(Random.class);
        when(random.nextDouble()).thenReturn(0.999);
        BlockPos[] floor = floor();

        FloorBlockConsumption.consume(level, TABLE, floor, 1, random);

        for (BlockPos position : floor) {
            verify(level).destroyBlock(position, false);
        }
        verifyNoMoreInteractions(level);
        verify(random, never()).nextInt(anyInt());
    }

    @Test
    void repeatedPurchasesDamageAboutFivePercentOfFloorsAndRetainMultipleLosses() {
        Level level = mock(Level.class);
        AtomicInteger losses = new AtomicInteger();
        when(level.destroyBlock(any(BlockPos.class), eq(false))).thenAnswer(call -> {
            losses.incrementAndGet();
            return true;
        });
        Random random = new Random(20261011L);
        BlockPos[] floor = floor();
        int purchases = 20_000;
        int damagedFloors = 0;
        int multipleLosses = 0;
        for (int i = 0; i < purchases; ++i) {
            losses.set(0);
            FloorBlockConsumption.consume(level, TABLE, floor, 0.05, random);
            if (losses.get() > 0) {
                ++damagedFloors;
            }
            if (losses.get() > 1) {
                ++multipleLosses;
            }
        }
        assertEquals(0.05, damagedFloors / (double) purchases, 0.005);
        assertTrue(multipleLosses > 0, "Independent rolls must still allow multiple losses");
        assertTrue(multipleLosses / (double) purchases < 0.003,
                "Several blocks breaking must be rare at a 5% overall chance");
        System.out.printf("Floor damage probe: %d/%d damaged floors, %d with multiple losses%n",
                damagedFloors, purchases, multipleLosses);
    }

    private static BlockPos[] floor() {
        BlockPos[] positions = new BlockPos[9];
        int index = 0;
        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                positions[index++] = TABLE.offset(x, -1, z);
            }
        }
        return positions;
    }
}
