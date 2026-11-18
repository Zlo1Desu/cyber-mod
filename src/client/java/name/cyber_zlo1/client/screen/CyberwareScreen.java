package name.cyber_zlo1.client.screen;

import name.cyber_zlo1.cyberware.CyberwareSlotType;
import name.cyber_zlo1.screen.CyberwareScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class CyberwareScreen extends HandledScreen<CyberwareScreenHandler> {
    private static final int BG_DARK = 0xEE080B12;
    private static final int BG_PANEL = 0xDD0D1320;
    private static final int BG_CHAMBER = 0xEE05080F;
    private static final int NEON_YELLOW = 0xFFFCEE09;
    private static final int NEON_CYAN = 0xFF00F0FF;
    private static final int NEON_MAGENTA = 0xFFFF003C;
    private static final int NEON_CYAN_DIM = 0x5500F0FF;
    private static final int NEON_RED_DIM = 0x66FF003C;
    private static final int GRID_LINE = 0x1800F0FF;

    private float mouseX;
    private float mouseY;

    public CyberwareScreen(CyberwareScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = CyberwareScreenHandler.GUI_WIDTH;
        this.backgroundHeight = CyberwareScreenHandler.GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        int btnX = this.x + 6;
        int btnY = this.y + 5;
        this.addDrawableChild(new CyberButton(btnX, btnY, 40, 12, Text.literal("< INV"), button -> {
            if (this.client != null && this.client.player != null) {
                this.client.setScreen(new InventoryScreen(this.client.player));
            }
        }));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Suppress default vanilla grey titles; header and labels are rendered in drawBackground.
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = this.x;
        int y = this.y;
        int w = this.backgroundWidth;
        int h = this.backgroundHeight;

        // Main dark cyberpunk frame
        context.fill(x, y, x + w, y + h, BG_DARK);

        // Subtle horizontal tech scanlines
        for (int sy = y + 2; sy < y + h - 2; sy += 4) {
            context.fill(x + 2, sy, x + w - 2, sy + 1, GRID_LINE);
        }

        // Outer neon borders (yellow top/left accents, cyan main frame)
        drawBorder(context, x, y, w, h, NEON_CYAN);
        drawBorder(context, x + 2, y + 2, w - 4, h - 4, 0x4400F0FF);

        // Clipped/stepped Cyberpunk corner accents in neon yellow & magenta
        context.fill(x - 1, y - 1, x + 16, y + 2, NEON_YELLOW);
        context.fill(x - 1, y - 1, x + 2, y + 14, NEON_YELLOW);
        context.fill(x + w - 16, y - 1, x + w + 1, y + 2, NEON_YELLOW);
        context.fill(x + w - 2, y - 1, x + w + 1, y + 14, NEON_YELLOW);
        context.fill(x - 1, y + h - 2, x + 16, y + h + 1, NEON_MAGENTA);
        context.fill(x - 1, y + h - 14, x + 2, y + h + 1, NEON_MAGENTA);
        context.fill(x + w - 16, y + h - 2, x + w + 1, y + h + 1, NEON_MAGENTA);
        context.fill(x + w - 2, y + h - 14, x + w + 1, y + h + 1, NEON_MAGENTA);

        // Top header bar
        context.fill(x + 3, y + 3, x + w - 3, y + 19, BG_PANEL);
        context.fill(x + 3, y + 19, x + w - 3, y + 20, NEON_YELLOW);

        // Scaled title so localized strings fit cleanly between button and capacity meter (x+48..x+146)
        Text titleText = Text.translatable("cyber.ui.title");
        float titleScale = Math.min(0.62f, 98.0f / Math.max(1, this.textRenderer.getWidth(titleText)));
        context.getMatrices().push();
        context.getMatrices().translate(x + 48, y + 7, 0);
        context.getMatrices().scale(titleScale, titleScale, 1.0f);
        context.drawText(this.textRenderer, titleText, 0, 0, NEON_YELLOW, true);
        context.getMatrices().pop();

        // Capacity meter (top-right) in its own bordered sub-box
        drawCapacityMeter(context, x + w - 66, y + 4);

        // Central Ripperdoc scanner chamber (x+64..x+158, y+22..y+142)
        int chX1 = x + 64;
        int chY1 = y + 22;
        int chX2 = x + 158;
        int chY2 = y + 142;
        context.fill(chX1, chY1, chX2, chY2, BG_CHAMBER);
        drawBorder(context, chX1, chY1, chX2 - chX1, chY2 - chY1, NEON_CYAN_DIM);
        drawTargetingReticle(context, chX1, chY1, chX2, chY2);

        // Circuit traces connecting each of the 10 CyberwareSlotType slots to body zones
        drawCircuitTraces(context, x, y);

        // Render live 3D player model inside central chamber
        if (this.client != null && this.client.player != null) {
            InventoryScreen.drawEntity(context, chX1, chY1, chX2, chY2, 48, 0.0625F, this.mouseX, this.mouseY, this.client.player);
        }

        // Render all 10 cyberware slot frames + player inventory slot frames
        drawSlotFrames(context, x, y);

        // Bottom inventory divider & frame
        context.fill(x + 6, y + 146, x + w - 6, y + 147, NEON_MAGENTA);
        context.fill(x + 6, y + 147, x + 70, y + 148, NEON_CYAN);
        context.getMatrices().push();
        context.getMatrices().translate(x + 8, y + 141, 0);
        context.getMatrices().scale(0.65f, 0.65f, 1.0f);
        context.drawText(this.textRenderer, Text.literal("// INVENTORY"), 0, 0, NEON_CYAN, false);
        context.getMatrices().pop();
    }

    private void drawCapacityMeter(DrawContext context, int mx, int my) {
        int cap = this.handler.getTotalEquippedCapacity();
        int maxCap = 100;
        int color = cap > 90 ? NEON_MAGENTA : (cap > 70 ? NEON_YELLOW : NEON_CYAN);

        // Clean bordered sub-box inside header bar
        context.fill(mx - 2, my - 1, mx + 61, my + 14, 0xCC060A11);
        drawBorder(context, mx - 2, my - 1, 63, 15, NEON_CYAN_DIM);

        String capText = cap + " / " + maxCap;
        context.getMatrices().push();
        context.getMatrices().translate(mx + 1, my + 1, 0);
        context.getMatrices().scale(0.62f, 0.62f, 1.0f);
        context.drawText(this.textRenderer, Text.literal(capText), 0, 0, color, false);
        context.getMatrices().pop();

        // 10 segmented bars
        int segments = 10;
        int filled = Math.min(segments, (int) Math.ceil((cap / (double) maxCap) * segments));
        for (int i = 0; i < segments; i++) {
            int sx = mx + i * 6;
            int sy = my + 8;
            int segColor = i < filled ? (i >= 9 ? NEON_MAGENTA : (i >= 7 ? NEON_YELLOW : NEON_CYAN)) : 0x331E2D3D;
            context.fill(sx, sy, sx + 4, sy + 4, segColor);
        }
    }

    private void drawTargetingReticle(DrawContext context, int x1, int y1, int x2, int y2) {
        int cx = (x1 + x2) / 2;
        int cy = (y1 + y2) / 2;

        // Vertical spine guide and subtle crosshairs
        context.fill(cx, y1 + 8, cx + 1, y2 - 8, 0x2200F0FF);
        context.fill(x1 + 8, cy, x2 - 8, cy + 1, 0x2200F0FF);

        // Corner scanner brackets
        int b = 8;
        context.fill(x1 + 2, y1 + 2, x1 + 2 + b, y1 + 3, NEON_CYAN);
        context.fill(x1 + 2, y1 + 2, x1 + 3, y1 + 2 + b, NEON_CYAN);

        context.fill(x2 - 2 - b, y1 + 2, x2 - 2, y1 + 3, NEON_CYAN);
        context.fill(x2 - 3, y1 + 2, x2 - 2, y1 + 2 + b, NEON_CYAN);

        context.fill(x1 + 2, y2 - 3, x1 + 2 + b, y2 - 2, NEON_CYAN);
        context.fill(x1 + 2, y2 - 2 - b, x1 + 3, y2 - 2, NEON_CYAN);

        context.fill(x2 - 2 - b, y2 - 3, x2 - 2, y2 - 2, NEON_CYAN);
        context.fill(x2 - 3, y2 - 2 - b, x2 - 2, y2 - 2, NEON_CYAN);

        // Animated horizontal laser scanner beam sweeping across chamber
        int scanRange = Math.max(1, y2 - y1 - 12);
        int scanY = y1 + 6 + (int) ((System.currentTimeMillis() / 18L) % scanRange);
        context.fill(x1 + 2, scanY - 2, x2 - 2, scanY + 3, 0x2800F0FF);
        context.fill(x1 + 2, scanY, x2 - 2, scanY + 1, 0xAA00FFA3);
    }

    private void drawCircuitTraces(DrawContext context, int x, int y) {
        float pulse = (System.currentTimeMillis() % 1200L) / 1200.0f;

        for (CyberwareSlotType type : CyberwareSlotType.values()) {
            ItemStack stack = this.handler.getCyberwareInventory().getStack(type.getIndex());
            boolean equipped = !stack.isEmpty();
            int traceColor = equipped ? NEON_YELLOW : 0x6600F0FF;
            int nodeColor = equipped ? NEON_MAGENTA : NEON_CYAN;

            int sx = x + type.getSlotX();
            int sy = y + type.getSlotY() + 8;
            boolean isLeft = type.getSlotX() < 100;

            int startX = isLeft ? sx + 18 : sx - 2;
            int targetX = getTargetBodyX(x, type);
            int targetY = getTargetBodyY(y, type);
            int elbowX = isLeft ? x + 56 : x + 166;

            // Horizontal from slot to elbow
            drawHLine(context, startX, elbowX, sy, traceColor);
            // Vertical from slot Y to body zone Y
            drawVLine(context, elbowX, sy, targetY, traceColor);
            // Horizontal from elbow into body zone
            drawHLine(context, elbowX, targetX, targetY, traceColor);
            // Node dot at body zone
            context.fill(targetX - 1, targetY - 1, targetX + 2, targetY + 2, nodeColor);

            // Traveling neon data pulse along the 3-segment trace
            drawTracePulse(context, startX, sy, elbowX, targetX, targetY, pulse, equipped);
        }
    }

    private static void drawTracePulse(DrawContext context, int startX, int sy, int elbowX,
                                       int targetX, int targetY, float pulse, boolean equipped) {
        int d1 = Math.abs(elbowX - startX);
        int d2 = Math.abs(targetY - sy);
        int d3 = Math.abs(targetX - elbowX);
        int total = d1 + d2 + d3;
        if (total <= 0) {
            return;
        }

        float dist = pulse * total;
        int px;
        int py;
        if (dist <= d1) {
            float t = d1 == 0 ? 0.0f : dist / d1;
            px = Math.round(startX + (elbowX - startX) * t);
            py = sy;
        } else if (dist <= d1 + d2) {
            float t = d2 == 0 ? 0.0f : (dist - d1) / d2;
            px = elbowX;
            py = Math.round(sy + (targetY - sy) * t);
        } else {
            float t = d3 == 0 ? 0.0f : (dist - d1 - d2) / d3;
            px = Math.round(elbowX + (targetX - elbowX) * t);
            py = targetY;
        }

        int glowColor = equipped ? 0x66FCEE09 : 0x5500F0FF;
        int coreColor = equipped ? 0xFF00FFA3 : 0xEE00FFA3;
        context.fill(px - 1, py - 1, px + 2, py + 2, glowColor);
        context.fill(px, py, px + 1, py + 1, coreColor);
    }

    private int getTargetBodyX(int x, CyberwareSlotType type) {
        return switch (type) {
            case FRONTAL_CORTEX, OCULAR -> x + 106;
            case CIRCULATORY -> x + 105;
            case IMMUNE -> x + 107;
            case NERVOUS -> x + 108;
            case OPERATING_SYSTEM, SKELETON -> x + 116;
            case HANDS -> x + 128;
            case ARMS -> x + 125;
            case LEGS -> x + 116;
        };
    }

    private int getTargetBodyY(int y, CyberwareSlotType type) {
        return switch (type) {
            case FRONTAL_CORTEX -> y + 34;
            case OCULAR -> y + 44;
            case CIRCULATORY -> y + 66;
            case IMMUNE -> y + 80;
            case NERVOUS -> y + 96;
            case OPERATING_SYSTEM -> y + 42;
            case SKELETON -> y + 64;
            case HANDS -> y + 86;
            case ARMS -> y + 72;
            case LEGS -> y + 116;
        };
    }

    private void drawSlotFrames(DrawContext context, int x, int y) {
        for (Slot slot : this.handler.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            if (slot.inventory == this.handler.getCyberwareInventory()) {
                CyberwareSlotType type = CyberwareSlotType.values()[slot.getIndex()];
                boolean equipped = slot.hasStack();
                int outerColor = equipped ? NEON_YELLOW : NEON_CYAN;
                int accentColor = equipped ? NEON_MAGENTA : 0x5500F0FF;

                // 20x20 outer frame around 16x16 slot
                context.fill(sx - 2, sy - 2, sx + 18, sy + 18, 0xCC070B14);
                drawBorder(context, sx - 2, sy - 2, 20, 20, outerColor);
                context.fill(sx - 2, sy - 2, sx + 2, sy, accentColor);
                context.fill(sx + 14, sy + 16, sx + 18, sy + 18, accentColor);

                if (!equipped) {
                    drawHologramIcon(context, sx + 2, sy + 2, type);

                    String code = getSlotCode(type);
                    int codeX = sx + 15 - Math.round(this.textRenderer.getWidth(code) * 0.5f);
                    int codeColor = type == CyberwareSlotType.OPERATING_SYSTEM ? 0xCCFCEE09 : 0xAA00F0FF;
                    context.getMatrices().push();
                    context.getMatrices().translate(codeX, sy + 11, 0);
                    context.getMatrices().scale(0.5f, 0.5f, 1.0f);
                    context.drawText(this.textRenderer, code, 0, 0, codeColor, false);
                    context.getMatrices().pop();
                }
            } else {
                // Player inventory / hotbar slot frame
                context.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xAA0A0F1A);
                drawBorder(context, sx - 1, sy - 1, 18, 18, NEON_RED_DIM);
            }
        }
    }

    private static void drawHologramIcon(DrawContext context, int ix, int iy, CyberwareSlotType type) {
        String[] pattern = getIconPattern(type);
        boolean isOs = type == CyberwareSlotType.OPERATING_SYSTEM;
        int outerColor = isOs ? 0x99FCEE09 : 0x7700F0FF;
        int coreColor = isOs ? 0xCC00F0FF : 0xBB00FFA3;

        for (int row = 0; row < pattern.length; row++) {
            String line = pattern[row];
            for (int col = 0; col < line.length(); col++) {
                char c = line.charAt(col);
                if (c == '.') {
                    continue;
                }
                int color = (c == '#') ? coreColor : outerColor;
                context.fill(ix + col, iy + row, ix + col + 1, iy + row + 1, color);
            }
        }
    }

    private static String[] getIconPattern(CyberwareSlotType type) {
        return switch (type) {
            case FRONTAL_CORTEX -> new String[]{
                    "..O..O..O...",
                    ".OOOOOOOOO..",
                    ".O#######O..",
                    "OO#O###O#OO.",
                    ".O#######O..",
                    "OO#O###O#OO.",
                    ".O#######O..",
                    "OO#O###O#OO.",
                    ".O#######O..",
                    ".OOOOOOOOO..",
                    "..O..O..O...",
                    "............"
            };
            case OCULAR -> new String[]{
                    "....OOOO....",
                    "..OO....OO..",
                    ".O...##...O.",
                    "O....##....O",
                    "O..######..O",
                    "O..##OO##..O",
                    "O..######..O",
                    "O....##....O",
                    ".O...##...O.",
                    "..OO....OO..",
                    "....OOOO....",
                    "............"
            };
            case CIRCULATORY -> new String[]{
                    "..OO....OO..",
                    ".O##O..O##O.",
                    "O####OO####O",
                    "O##########O",
                    "O##O####O##O",
                    ".O########O.",
                    "..O######O..",
                    "...O####O...",
                    "....O##O....",
                    ".....OO.....",
                    "............",
                    "............"
            };
            case IMMUNE -> new String[]{
                    "....OOOO....",
                    "..OO####OO..",
                    ".O###OO###O.",
                    ".O###OO###O.",
                    ".O#OOOOOO#O.",
                    ".O#OOOOOO#O.",
                    ".O###OO###O.",
                    "..O##OO##O..",
                    "...O####O...",
                    "....O##O....",
                    ".....OO.....",
                    "............"
            };
            case NERVOUS -> new String[]{
                    "......OOO...",
                    ".....O##O...",
                    "....O##O....",
                    "...O##O.....",
                    "..O#####OO..",
                    "....OOO##O..",
                    "......O##O..",
                    ".....O##O...",
                    "....O##O....",
                    "...O##O.....",
                    "...OO.......",
                    "............"
            };
            case OPERATING_SYSTEM -> new String[]{
                    "....OOOO....",
                    "...O####O...",
                    "....OOOO....",
                    "...O####O...",
                    "....OOOO....",
                    "...O####O...",
                    "....OOOO....",
                    "...O####O...",
                    "....OOOO....",
                    "...O####O...",
                    "....OOOO....",
                    "............"
            };
            case SKELETON -> new String[]{
                    "...OOOOOO...",
                    "..O######O..",
                    ".O##O##O##O.",
                    ".O##O##O##O.",
                    ".O########O.",
                    "..O##OO##O..",
                    "...OOOOOO...",
                    "...O#OO#O...",
                    "....OOOO....",
                    "..OO....OO..",
                    ".O........O.",
                    "............"
            };
            case HANDS -> new String[]{
                    "..O.O.O.O...",
                    "..#.#.#.#...",
                    "..#.#.#.#...",
                    "..O#O#O#O.O.",
                    "..O######O#.",
                    "..O#######O.",
                    "..O######O..",
                    "...O####O...",
                    "...O####O...",
                    "....OOOO....",
                    "............",
                    "............"
            };
            case ARMS -> new String[]{
                    ".OO......OO.",
                    ".O#O....O#O.",
                    "..O#O..O#O..",
                    "...O#OO#O...",
                    "....O##O....",
                    "...O####O...",
                    "..O#O..O#O..",
                    ".O#O....O#O.",
                    ".OO......OO.",
                    "............",
                    "............",
                    "............"
            };
            case LEGS -> new String[]{
                    "...OOOOO....",
                    "...O###O....",
                    "...O###O....",
                    "....O#O.....",
                    "...O###O....",
                    "...O###O....",
                    "....O#O.....",
                    "...O####OO..",
                    "..O#######O.",
                    "..OOOOOOOOO.",
                    "............",
                    "............"
            };
        };
    }

    @Override
    protected void drawMouseoverTooltip(DrawContext context, int x, int y) {
        super.drawMouseoverTooltip(context, x, y);
        if (this.handler.getCursorStack().isEmpty()
                && this.focusedSlot != null
                && !this.focusedSlot.hasStack()
                && this.focusedSlot.inventory == this.handler.getCyberwareInventory()) {
            CyberwareSlotType type = CyberwareSlotType.values()[this.focusedSlot.getIndex()];
            List<Text> lines = new ArrayList<>();
            lines.add(Text.translatable(type.getTranslationKey()).formatted(Formatting.AQUA, Formatting.BOLD));
            if (type == CyberwareSlotType.OPERATING_SYSTEM) {
                lines.add(Text.literal("Установите Сандевистан или ОС").formatted(Formatting.YELLOW));
            }
            lines.add(Text.literal("SLOT // " + getSlotCode(type)).formatted(Formatting.DARK_GRAY));
            context.drawTooltip(this.textRenderer, lines, x, y);
        }
    }

    private static String getSlotCode(CyberwareSlotType type) {
        return switch (type) {
            case FRONTAL_CORTEX -> "FC";
            case OCULAR -> "OC";
            case CIRCULATORY -> "CR";
            case IMMUNE -> "IM";
            case NERVOUS -> "NV";
            case OPERATING_SYSTEM -> "OS";
            case SKELETON -> "SK";
            case HANDS -> "HD";
            case ARMS -> "AR";
            case LEGS -> "LG";
        };
    }

    private static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static void drawHLine(DrawContext context, int x1, int x2, int y, int color) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        context.fill(minX, y, maxX + 1, y + 1, color);
    }

    private static void drawVLine(DrawContext context, int x, int y1, int y2, int color) {
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        context.fill(x, minY, x + 1, maxY + 1, color);
    }

    private class CyberButton extends ButtonWidget {
        protected CyberButton(int x, int y, int width, int height, Text message, PressAction onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean hovered = this.isHovered();
            int bg = hovered ? 0xEE18263A : 0xEE0C1320;
            int border = hovered ? NEON_YELLOW : NEON_CYAN;
            int textColor = hovered ? NEON_YELLOW : NEON_CYAN;

            context.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), bg);
            drawBorder(context, this.getX(), this.getY(), this.getWidth(), this.getHeight(), border);
            int tw = textRenderer.getWidth(this.getMessage());
            context.drawText(textRenderer, this.getMessage(),
                    this.getX() + (this.getWidth() - tw) / 2,
                    this.getY() + (this.getHeight() - 8) / 2,
                    textColor, false);
        }
    }
}
