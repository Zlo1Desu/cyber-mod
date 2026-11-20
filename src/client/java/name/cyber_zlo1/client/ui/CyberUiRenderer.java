package name.cyber_zlo1.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Authentic Cyberpunk 2077 REDengine 2D UI drawing primitives.
 * Provides 45-degree chamfered panels, angled segmented gauges, Kiroshi corner brackets,
 * RGB-split glitch text, and auto-clamped scaled typography to guarantee zero overlapping text.
 */
public final class CyberUiRenderer {
    // Authentic Cyberpunk 2077 REDengine palette
    public static final int CP_CRIMSON = 0xFFFF4D5A;
    public static final int CP_CRIMSON_BRIGHT = 0xFFFF2A45;
    public static final int CP_CRIMSON_DARK = 0xFF7A1924;
    public static final int CP_CRIMSON_BG = 0xE213070B;
    public static final int CP_CRIMSON_PANEL = 0xD81D0A10;
    public static final int CP_CRIMSON_DIM = 0x66FF4D5A;

    public static final int CP_CYAN = 0xFF5CEBF5;
    public static final int CP_CYAN_DIM = 0x665CEBF5;
    public static final int CP_CYAN_DARK = 0xFF143C47;

    public static final int CP_YELLOW = 0xFFFCEE09;
    public static final int CP_YELLOW_DIM = 0x66FCEE09;

    public static final int CP_EMERALD = 0xFF39FFB4;
    public static final int CP_DARK = 0xE60A0B10;
    public static final int CP_CHAMBER = 0xEE090408;

    public static final int CP_TEXT_LIGHT = 0xFFF2E6E8;
    public static final int CP_TEXT_MUTED = 0xFF9E7B82;
    public static final int CP_SCANLINE = 0x0E000000;

    private CyberUiRenderer() {
    }

    /**
     * Draws a Cyberpunk 2077 panel with a 45-degree diagonal chamfer on the bottom-right corner,
     * subtle dark scanlines, a crisp 1px contour border, and a left/top accent bar.
     */
    public static void drawChamferedPanel(
            DrawContext ctx,
            int x,
            int y,
            int w,
            int h,
            int cutSize,
            int fillColor,
            int borderColor,
            int accentColor
    ) {
        drawChamferedPanelInternal(ctx, x, y, w, h, cutSize, fillColor, borderColor, accentColor, false, true);
    }

    /**
     * Draws a Cyberpunk 2077 window/card with 45-degree diagonal chamfers on BOTH top-left and
     * bottom-right corners, crisp 1px contour border, subtle dark scanlines, and stepped corner accents.
     */
    public static void drawDoubleChamferedPanel(
            DrawContext ctx,
            int x,
            int y,
            int w,
            int h,
            int cutSize,
            int fillColor,
            int borderColor,
            int accentColor
    ) {
        drawChamferedPanelInternal(ctx, x, y, w, h, cutSize, fillColor, borderColor, accentColor, true, true);
    }

    private static void drawChamferedPanelInternal(
            DrawContext ctx,
            int x,
            int y,
            int w,
            int h,
            int cutSize,
            int fillColor,
            int borderColor,
            int accentColor,
            boolean doubleChamfer,
            boolean scanlines
    ) {
        if (w <= 1 || h <= 1) {
            return;
        }
        int maxCut = doubleChamfer ? Math.min(w, h) / 3 : Math.min(w, h) / 2 - 1;
        int cut = Math.max(0, Math.min(cutSize, maxCut));

        // 1. Interior fill
        if ((fillColor >>> 24) != 0) {
            if (!doubleChamfer) {
                if (h - cut > 0) {
                    ctx.fill(x, y, x + w, y + h - cut, fillColor);
                }
                for (int i = 0; i < cut; i++) {
                    int ry = y + h - cut + i;
                    int rx = x + w - 1 - i;
                    if (rx > x) {
                        ctx.fill(x, ry, rx, ry + 1, fillColor);
                    }
                }
            } else {
                for (int i = 0; i < cut; i++) {
                    int ry = y + i;
                    int lx = x + cut - i;
                    if (x + w > lx) {
                        ctx.fill(lx, ry, x + w, ry + 1, fillColor);
                    }
                }
                if (y + h - cut > y + cut) {
                    ctx.fill(x, y + cut, x + w, y + h - cut, fillColor);
                }
                for (int i = 0; i < cut; i++) {
                    int ry = y + h - cut + i;
                    int rx = x + w - 1 - i;
                    if (rx > x) {
                        ctx.fill(x, ry, rx, ry + 1, fillColor);
                    }
                }
            }
        }

        // 2. Subtle REDengine dark horizontal scanlines strictly inside contour
        if (scanlines && h >= 6) {
            for (int sy = y + 2; sy < y + h - 1; sy += 3) {
                int relY = sy - y;
                int lx = (doubleChamfer && relY < cut) ? (x + cut - relY + 1) : (x + 1);
                int rx = (relY >= h - cut) ? (x + w - 2 - (relY - (h - cut))) : (x + w - 1);
                if (rx > lx) {
                    ctx.fill(lx, sy, rx, sy + 1, CP_SCANLINE);
                }
            }
        }

        // 3. Crisp 1px contour border
        if ((borderColor >>> 24) != 0) {
            if (!doubleChamfer) {
                // Top & left edges
                ctx.fill(x, y, x + w, y + 1, borderColor);
                ctx.fill(x, y, x + 1, y + h, borderColor);
                // Right edge (above cut) & bottom edge (left of cut)
                ctx.fill(x + w - 1, y, x + w, y + h - cut, borderColor);
                ctx.fill(x, y + h - 1, x + w - cut, y + h, borderColor);
                // Bottom-right 45-degree diagonal contour
                for (int i = 0; i < cut; i++) {
                    int px = x + w - 2 - i;
                    int py = y + h - cut + i;
                    ctx.fill(px, py, px + 1, py + 1, borderColor);
                }
            } else {
                // Top edge (right of top-left cut) & right edge (above bottom-right cut)
                ctx.fill(x + cut, y, x + w, y + 1, borderColor);
                ctx.fill(x + w - 1, y, x + w, y + h - cut, borderColor);
                // Left edge (below top-left cut) & bottom edge (left of bottom-right cut)
                ctx.fill(x, y + cut, x + 1, y + h, borderColor);
                ctx.fill(x, y + h - 1, x + w - cut, y + h, borderColor);
                // Top-left 45-degree diagonal contour
                for (int i = 0; i <= cut; i++) {
                    int px = x + cut - i;
                    int py = y + i;
                    ctx.fill(px, py, px + 1, py + 1, borderColor);
                }
                // Bottom-right 45-degree diagonal contour
                for (int i = 0; i < cut; i++) {
                    int px = x + w - 2 - i;
                    int py = y + h - cut + i;
                    ctx.fill(px, py, px + 1, py + 1, borderColor);
                }
            }
        }

        // 4. Signature Cyberpunk 2077 accent bar & corner ticks
        if ((accentColor >>> 24) != 0) {
            if (!doubleChamfer) {
                int barW = w >= 18 ? 2 : 1;
                ctx.fill(x, y, x + barW, y + h, accentColor);
                int topTick = Math.min(Math.max(4, w / 5), Math.max(4, w - cut - 2));
                ctx.fill(x, y, x + topTick, y + 1, accentColor);
                if (cut >= 3) {
                    ctx.fill(x + w - cut - 2, y + h - 1, x + w - cut, y + h, accentColor);
                }
            } else {
                // Top-right corner accent L-bar
                int trLen = Math.min(18, Math.max(5, (w - cut) / 4));
                int trH = Math.min(10, Math.max(4, (h - cut) / 4));
                ctx.fill(x + w - trLen, y, x + w, y + 2, accentColor);
                ctx.fill(x + w - 2, y, x + w, y + trH, accentColor);
                // Bottom-left corner accent L-bar
                int blLen = Math.min(16, Math.max(5, (w - cut) / 4));
                int blH = Math.min(10, Math.max(4, (h - cut) / 4));
                ctx.fill(x, y + h - 2, x + blLen, y + h, accentColor);
                ctx.fill(x, y + h - blH, x + 2, y + h, accentColor);
            }
        }
    }

    /**
     * Draws a chamfered Cyberpunk 2077 implant socket with an optional left tier indicator bar.
     */
    public static void drawChamferedSlot(
            DrawContext ctx,
            int x,
            int y,
            int size,
            int cut,
            int fillColor,
            int borderColor,
            int tierBarColor
    ) {
        drawChamferedPanelInternal(ctx, x, y, size, size, cut, fillColor, borderColor, 0, false, false);
        if ((tierBarColor >>> 24) != 0 && size >= 8) {
            ctx.fill(x + 1, y + 2, x + 3, y + size - 2, tierBarColor);
        }
    }

    /**
     * Draws a crisp Cyberpunk 2077 angled/segmented energy, health, or capacity bar.
     * Supports smooth partial progress in the frontier segment while preserving crisp segment dividers.
     */
    public static void drawSegmentedGauge(
            DrawContext ctx,
            int x,
            int y,
            int w,
            int h,
            float ratio,
            int segments,
            int fillColor,
            int emptyColor,
            int bgColor
    ) {
        if (w <= 0 || h <= 0 || segments <= 0) {
            return;
        }
        float clamped = MathHelper.clamp(ratio, 0.0f, 1.0f);

        if ((bgColor >>> 24) != 0) {
            ctx.fill(x, y, x + w, y + h, bgColor);
        }

        int gap = (w >= segments * 4) ? 1 : 0;
        boolean slanted = (h >= 4 && w >= segments * 5);
        int availW = w - (slanted ? 1 : 0);
        int topHalfH = h / 2;

        float exactFilled = clamped * segments;
        int highlightColor = ((Math.min(0x55, (fillColor >>> 24) / 3)) << 24) | 0xFFFFFF;

        for (int i = 0; i < segments; i++) {
            int sx0 = x + (i * availW) / segments;
            int sx1 = x + ((i + 1) * availW) / segments - gap;
            if (sx1 <= sx0) {
                sx1 = sx0 + 1;
            }
            int segW = sx1 - sx0;
            float segFrac = MathHelper.clamp(exactFilled - i, 0.0f, 1.0f);

            // Empty segment backdrop
            if ((emptyColor >>> 24) != 0) {
                drawGaugeBlock(ctx, sx0, y, segW, h, topHalfH, slanted, emptyColor);
            }

            // Filled portion of segment
            if (segFrac > 0.0f) {
                int fillW = Math.max(1, Math.round(segW * segFrac));
                drawGaugeBlock(ctx, sx0, y, fillW, h, topHalfH, slanted, fillColor);
                // Crisp 1px specular sheen along top edge of filled segment
                if (h >= 3) {
                    int shift = slanted ? 1 : 0;
                    ctx.fill(sx0 + shift, y, sx0 + shift + fillW, y + 1, highlightColor);
                }
            }
        }
    }

    private static void drawGaugeBlock(
            DrawContext ctx,
            int sx,
            int y,
            int segW,
            int h,
            int topHalfH,
            boolean slanted,
            int color
    ) {
        if (!slanted) {
            ctx.fill(sx, y, sx + segW, y + h, color);
        } else {
            ctx.fill(sx + 1, y, sx + 1 + segW, y + topHalfH, color);
            ctx.fill(sx, y + topHalfH, sx + segW, y + h, color);
        }
    }

    /**
     * Draws clean 1px Kiroshi HUD corner brackets framing (x, y, w, h).
     */
    public static void drawCornerBrackets(DrawContext ctx, int x, int y, int w, int h, int len, int color) {
        if (w <= 2 || h <= 2 || len <= 0) {
            return;
        }
        int b = Math.min(len, Math.min(w, h) / 2);
        // Top-left
        ctx.fill(x, y, x + b, y + 1, color);
        ctx.fill(x, y, x + 1, y + b, color);
        // Top-right
        ctx.fill(x + w - b, y, x + w, y + 1, color);
        ctx.fill(x + w - 1, y, x + w, y + b, color);
        // Bottom-left
        ctx.fill(x, y + h - 1, x + b, y + h, color);
        ctx.fill(x, y + h - b, x + 1, y + h, color);
        // Bottom-right
        ctx.fill(x + w - b, y + h - 1, x + w, y + h, color);
        ctx.fill(x + w - 1, y + h - b, x + w, y + h, color);
    }

    /**
     * Draws Kiroshi HUD corner brackets with a subtle 1px translucent outer glow halo.
     */
    public static void drawCornerBracketsWithGlow(
            DrawContext ctx,
            int x,
            int y,
            int w,
            int h,
            int len,
            int color,
            int glowColor
    ) {
        if ((glowColor >>> 24) != 0) {
            drawCornerBrackets(ctx, x - 1, y - 1, w + 2, h + 2, len + 1, glowColor);
        }
        drawCornerBrackets(ctx, x, y, w, h, len, color);
    }

    /**
     * Draws scaled {@link Text} cleanly with matrix stack isolation.
     */
    public static void drawScaledText(
            DrawContext ctx,
            TextRenderer tr,
            Text text,
            float x,
            float y,
            float scale,
            int color,
            boolean shadow
    ) {
        if (scale <= 0.01f) {
            return;
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0.0f);
        ctx.getMatrices().scale(scale, scale, 1.0f);
        ctx.drawText(tr, text, 0, 0, color, shadow);
        ctx.getMatrices().pop();
    }

    /**
     * Draws scaled {@link String} text cleanly with matrix stack isolation.
     */
    public static void drawScaledText(
            DrawContext ctx,
            TextRenderer tr,
            String text,
            float x,
            float y,
            float scale,
            int color,
            boolean shadow
    ) {
        if (scale <= 0.01f) {
            return;
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0.0f);
        ctx.getMatrices().scale(scale, scale, 1.0f);
        ctx.drawText(tr, text, 0, 0, color, shadow);
        ctx.getMatrices().pop();
    }

    /**
     * Draws {@link Text} auto-scaled so its rendered width NEVER exceeds {@code maxWidth}.
     */
    public static void drawClampedText(
            DrawContext ctx,
            TextRenderer tr,
            Text text,
            float x,
            float y,
            float maxScale,
            float maxWidth,
            int color,
            boolean shadow
    ) {
        int rawW = tr.getWidth(text);
        float scale = rawW <= 0 ? maxScale : Math.min(maxScale, Math.max(1.0f, maxWidth) / rawW);
        drawScaledText(ctx, tr, text, x, y, scale, color, shadow);
    }

    /**
     * Draws {@link String} auto-scaled so its rendered width NEVER exceeds {@code maxWidth}.
     */
    public static void drawClampedText(
            DrawContext ctx,
            TextRenderer tr,
            String text,
            float x,
            float y,
            float maxScale,
            float maxWidth,
            int color,
            boolean shadow
    ) {
        int rawW = tr.getWidth(text);
        float scale = rawW <= 0 ? maxScale : Math.min(maxScale, Math.max(1.0f, maxWidth) / rawW);
        drawScaledText(ctx, tr, text, x, y, scale, color, shadow);
    }

    /**
     * Draws right-aligned {@link String} auto-scaled to fit strictly within {@code maxWidth} ending at {@code rightX}.
     */
    public static void drawClampedTextRight(
            DrawContext ctx,
            TextRenderer tr,
            String text,
            float rightX,
            float y,
            float maxScale,
            float maxWidth,
            int color,
            boolean shadow
    ) {
        int rawW = tr.getWidth(text);
        float scale = rawW <= 0 ? maxScale : Math.min(maxScale, Math.max(1.0f, maxWidth) / rawW);
        float renderedW = rawW * scale;
        drawScaledText(ctx, tr, text, rightX - renderedW, y, scale, color, shadow);
    }

    /**
     * Draws horizontally centered {@link Text} auto-scaled to fit strictly within {@code maxWidth}.
     */
    public static void drawClampedTextCentered(
            DrawContext ctx,
            TextRenderer tr,
            Text text,
            float centerX,
            float y,
            float maxScale,
            float maxWidth,
            int color,
            boolean shadow
    ) {
        int rawW = tr.getWidth(text);
        float scale = rawW <= 0 ? maxScale : Math.min(maxScale, Math.max(1.0f, maxWidth) / rawW);
        float renderedW = rawW * scale;
        drawScaledText(ctx, tr, text, centerX - renderedW * 0.5f, y, scale, color, shadow);
    }

    /**
     * Draws horizontally centered {@link String} auto-scaled to fit strictly within {@code maxWidth}.
     */
    public static void drawClampedTextCentered(
            DrawContext ctx,
            TextRenderer tr,
            String text,
            float centerX,
            float y,
            float maxScale,
            float maxWidth,
            int color,
            boolean shadow
    ) {
        int rawW = tr.getWidth(text);
        float scale = rawW <= 0 ? maxScale : Math.min(maxScale, Math.max(1.0f, maxWidth) / rawW);
        float renderedW = rawW * scale;
        drawScaledText(ctx, tr, text, centerX - renderedW * 0.5f, y, scale, color, shadow);
    }

    /**
     * Draws RGB-split Relic glitch text (cyan left offset, crimson right offset, main pass center).
     */
    public static void drawRgbGlitchText(
            DrawContext ctx,
            TextRenderer tr,
            String text,
            float x,
            float y,
            float maxScale,
            float maxWidth,
            int mainColor,
            boolean split
    ) {
        int rawW = tr.getWidth(text);
        float scale = rawW <= 0 ? maxScale : Math.min(maxScale, Math.max(1.0f, maxWidth) / rawW);
        if (split) {
            drawScaledText(ctx, tr, text, x - 1.0f, y, scale, 0x885CEBF5, false);
            drawScaledText(ctx, tr, text, x + 1.0f, y, scale, 0x88FF4D5A, false);
        }
        drawScaledText(ctx, tr, text, x, y, scale, mainColor, false);
    }

    public static void drawBorder(DrawContext ctx, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        ctx.fill(x, y, x + width, y + 1, color);
        ctx.fill(x, y + height - 1, x + width, y + height, color);
        ctx.fill(x, y, x + 1, y + height, color);
        ctx.fill(x + width - 1, y, x + width, y + height, color);
    }

    public static void drawHLine(DrawContext ctx, int x1, int x2, int y, int color) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        ctx.fill(minX, y, maxX + 1, y + 1, color);
    }

    public static void drawVLine(DrawContext ctx, int x, int y1, int y2, int color) {
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        ctx.fill(x, minY, x + 1, maxY + 1, color);
    }

    public static void drawDiamondNode(DrawContext ctx, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int span = radius - Math.abs(dy);
            ctx.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
        }
    }
}
