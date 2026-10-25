package net.tagtart.rechanted.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

/** Shared wrapping keeps both viewers readable without truncating operation rules. */
public final class BookOperationText {
    public static final int WIDTH = 174;
    public static final int HEIGHT = 220;

    private BookOperationText() {}

    public static int height(BookOperation operation) {
        var font = Minecraft.getInstance().font;
        return 38 + operation.details().stream()
                .mapToInt(detail -> font.split(detail, WIDTH - 4).size() * font.lineHeight + 3).sum();
    }

    public static void draw(GuiGraphics graphics, BookOperation operation) {
        var font = Minecraft.getInstance().font;
        int y = 36;
        for (var detail : operation.details()) {
            for (FormattedCharSequence line : font.split(detail, WIDTH - 4)) {
                graphics.drawString(font, line, 2, y, 0xFF404040, false);
                y += font.lineHeight;
            }
            y += 3;
        }
    }
}
