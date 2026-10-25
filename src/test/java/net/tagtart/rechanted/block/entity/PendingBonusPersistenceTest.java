package net.tagtart.rechanted.block.entity;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import net.tagtart.rechanted.block.ModBlocks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class PendingBonusPersistenceTest {
    @Test
    void absentAndEmptyOptionalItemsDoNotProduceDecodeErrors(MinecraftServer server) {
        AtomicInteger errors = new AtomicInteger();
        Logger logger = (Logger) LogManager.getLogger(ItemStack.class);
        AbstractAppender appender = new AbstractAppender("pendingBonusDecodeTest", null, null, false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getMessage().getFormattedMessage().contains("Tried to load invalid item")) {
                    errors.incrementAndGet();
                }
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            forEachReader(server, (table, reader) -> {
                reader.accept(table, new CompoundTag());
                CompoundTag explicitEmpty = new CompoundTag();
                explicitEmpty.put("PendingBonus", new CompoundTag());
                explicitEmpty.put("PendingGem", new CompoundTag());
                reader.accept(table, explicitEmpty);
                assertFalse(table.getUpdateTag(server.registryAccess()).contains("PendingBonus"));
            });
            assertEquals(0, errors.get(), "An absent optional bonus must not enter the strict item decoder");
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    void absentBonusClearsPreviouslyLoadedBonus(MinecraftServer server) {
        forEachReader(server, (table, reader) -> {
            reader.accept(table, bonusTag("PendingBonus", new ItemStack(Items.DIAMOND), server));
            assertEquals("minecraft:diamond", table.getUpdateTag(server.registryAccess()).getCompound("PendingBonus").getString("id"));
            reader.accept(table, new CompoundTag());
            assertFalse(table.getUpdateTag(server.registryAccess()).contains("PendingBonus"), "The old bonus must not survive a cleared state");
        });
    }

    @Test
    void legacyGemLoadsAndModernBonusTakesPrecedence(MinecraftServer server) {
        forEachReader(server, (table, reader) -> {
            CompoundTag tag = bonusTag("PendingGem", new ItemStack(Items.EMERALD, 3), server);
            reader.accept(table, tag);
            CompoundTag saved = table.getUpdateTag(server.registryAccess()).getCompound("PendingBonus");
            assertEquals("minecraft:emerald", saved.getString("id"));
            assertEquals(3, saved.getInt("count"));
            tag.put("PendingBonus", new ItemStack(Items.DIAMOND, 2).save(server.registryAccess()));
            reader.accept(table, tag);
            saved = table.getUpdateTag(server.registryAccess()).getCompound("PendingBonus");
            assertEquals("minecraft:diamond", saved.getString("id"));
            assertEquals(2, saved.getInt("count"));
        });
    }

    @Test
    void explicitEmptyModernBonusDoesNotRestoreLegacyGem(MinecraftServer server) {
        forEachReader(server, (table, reader) -> {
            CompoundTag tag = bonusTag("PendingGem", new ItemStack(Items.EMERALD), server);
            tag.put("PendingBonus", new CompoundTag());
            reader.accept(table, tag);
            assertFalse(table.getUpdateTag(server.registryAccess()).contains("PendingBonus"));
        });
    }

    private static CompoundTag bonusTag(String key, ItemStack item, MinecraftServer server) {
        CompoundTag tag = new CompoundTag();
        tag.put(key, item.save(server.registryAccess()));
        return tag;
    }

    private static void forEachReader(MinecraftServer server, BiConsumer<RechantedTableBlockEntity,
            BiConsumer<RechantedTableBlockEntity, CompoundTag>> test) {
        test.accept(newTable(), (table, tag) -> table.loadAdditional(tag, server.registryAccess()));
        test.accept(newTable(), (table, tag) -> table.handleUpdateTag(tag, server.registryAccess()));
    }

    private static RechantedTableBlockEntity newTable() {
        return new RechantedTableBlockEntity(BlockPos.ZERO, ModBlocks.RECHANTED_TABLE_BLOCK.get().defaultBlockState());
    }
}
