package name.cyber_zlo1.client.hud;

import name.cyber_zlo1.cyberware.CyberwareState;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class SandevistanHudOverlay implements HudRenderCallback {
    private static final int CHROMA_CYAN = 0x2800F0FF;
    private static final int CHROMA_EMERALD = 0x3000FFA3;
    private static final int CHROMA_MAGENTA = 0x22FF0055;
    private static final int NEON_CYAN = 0xFF00F0FF;
    private static final int NEON_EMERALD = 0xFF00FFA3;
    private static final int NEON_YELLOW = 0xFFFCEE09;
    private static final int NEON_RED = 0xFFFF003C;
    private static final int BG_DARK = 0xCC080B12;

    private boolean wasActiveLastFrame = false;
    private int deactivationAnimTicks = 0;

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.options.hudHidden) {
            return;
        }

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        TextRenderer textRenderer = client.textRenderer;

        boolean activeNow = CyberwareState.isActive();
        if (wasActiveLastFrame && !activeNow) {
            deactivationAnimTicks = 10;
        }
        wasActiveLastFrame = activeNow;

        boolean topLeftOccupied = false;
        if (activeNow) {
            drawActiveOverlay(context, textRenderer, screenW, screenH, client.player.age);
        } else {
            if (deactivationAnimTicks > 0) {
                drawDeactivationFade(context, screenW, screenH);
                deactivationAnimTicks--;
            }
            if (CyberwareState.getCooldownTicks() > 0) {
                drawCooldownBar(context, textRenderer, screenW);
            } else if (CyberwareState.isSandevistanEquipped()) {
                drawReadyIndicator(context, textRenderer);
                topLeftOccupied = true;
            }
        }

        if (CyberwareState.isKiroshiEquipped()) {
            drawKiroshiScanner(context, textRenderer, client, screenW, screenH);
        }

        if (CyberwareState.getSecondHeartCooldown() > 0) {
            drawSecondHeartCooldown(context, textRenderer, topLeftOccupied ? 28 : 10);
        }
    }

    private void drawDeactivationFade(DrawContext context, int screenW, int screenH) {
        float t = deactivationAnimTicks / 10.0f;
        int alpha = (int) (t * 48);
        context.fill(0, 0, screenW, screenH, (alpha << 24) | 0x00F0FF);
    }

    private void drawActiveOverlay(DrawContext context, TextRenderer textRenderer, int screenW, int screenH, int playerAge) {
        int activeTicks = CyberwareState.getActiveTicks();

        // 1. Full-Screen Cyberpunk Optical Color Grading
        context.fill(0, 0, screenW, screenH, 0x1A00E5FF);

        // Top & bottom 28% emerald/cyan radial vignette bands
        int topH = Math.max(1, (int) (screenH * 0.28f));
        int botH = Math.max(1, (int) (screenH * 0.28f));
        context.fillGradient(0, 0, screenW, topH, 0x4400FFA3, 0x0000FFA3);
        context.fillGradient(0, screenH - botH, screenW, screenH, 0x0000FFA3, 0x4400FFA3);

        // Left & right 18% horizontal vignette bands
        int sideW = Math.max(12, (int) (screenW * 0.18f));
        int strips = 12;
        int stripW = Math.max(1, sideW / strips);
        for (int i = 0; i < strips; i++) {
            float ratio = 1.0f - (i / (float) strips);
            int alpha = (int) (0x38 * ratio * ratio);
            int color = (alpha << 24) | 0x00D0FF;
            int lx = i * stripW;
            context.fill(lx, 0, lx + stripW, screenH, color);
            int rx = screenW - (i + 1) * stripW;
            context.fill(rx, 0, rx + stripW, screenH, color);
        }

        // Edgerunners Chromatic Aberration Edge Frame
        int edge = 6;
        context.fill(0, 0, screenW, edge, CHROMA_EMERALD);
        context.fill(0, screenH - edge, screenW, screenH, CHROMA_EMERALD);
        context.fill(0, edge, edge, screenH - edge, CHROMA_EMERALD);
        context.fill(screenW - edge, edge, screenW, screenH - edge, CHROMA_EMERALD);

        context.fill(0, 0, screenW, 3, CHROMA_CYAN);
        context.fill(0, 0, 4, screenH, CHROMA_CYAN);
        context.fill(edge, edge, screenW - edge, edge + 2, CHROMA_CYAN);

        context.fill(screenW - 4, 0, screenW, screenH, CHROMA_MAGENTA);
        context.fill(0, screenH - 3, screenW, screenH, CHROMA_MAGENTA);
        context.fill(edge, screenH - edge - 2, screenW - edge, screenH - edge, CHROMA_MAGENTA);

        // 2. Perspective Speed-Lines & Neural Grid
        int animOffset = (playerAge * 9) % 40;
        for (int y = 18 + (animOffset % 16); y < screenH - 18; y += 20) {
            int barLen = 14 + ((y + animOffset) % 18);
            int color = ((y / 20) % 3 == 0) ? 0x7700F0FF : (((y / 20) % 3 == 1) ? 0x6600FFA3 : 0x55FF0055);
            context.fill(2, y, 2 + barLen, y + 2, color);
            context.fill(screenW - 2 - barLen, y + 6, screenW - 2, y + 8, color);
        }
        for (int x = 24 + (animOffset % 24); x < screenW - 24; x += 36) {
            int vLen = 8 + ((x + animOffset) % 10);
            int color = ((x / 36) % 2 == 0) ? 0x5500FFA3 : 0x4400F0FF;
            context.fill(x, 2, x + 2, 2 + vLen, color);
            context.fill(x + 12, screenH - 2 - vLen, x + 14, screenH - 2, color);
        }

        // Subtle scanlines at top & bottom
        for (int y = 0; y < 24; y += 4) {
            context.fill(0, y, screenW, y + 1, 0x1600FFA3);
            context.fill(0, screenH - 1 - y, screenW, screenH - y, 0x1600FFA3);
        }

        // Corner brackets
        int b = 18;
        int m = 8;
        context.fill(m, m, m + b, m + 2, NEON_EMERALD);
        context.fill(m, m, m + 2, m + b, NEON_EMERALD);
        context.fill(screenW - m - b, m, screenW - m, m + 2, NEON_EMERALD);
        context.fill(screenW - m - 2, m, screenW - m, m + b, NEON_EMERALD);
        context.fill(m, screenH - m - 2, m + b, screenH - m, NEON_EMERALD);
        context.fill(m, screenH - m - b, m + 2, screenH - m, NEON_EMERALD);
        context.fill(screenW - m - b, screenH - m - 2, screenW - m, screenH - m, NEON_EMERALD);
        context.fill(screenW - m - 2, screenH - m - b, screenW - m, screenH - m, NEON_EMERALD);

        // 3. Activation Shockwave & Neural Boot Animation (first 15 ticks: activeTicks > 145)
        if (activeTicks > 145) {
            float bootProgress = Math.max(0.0f, Math.min(1.0f, (160 - activeTicks) / 15.0f));

            // Initial cyber-flash
            int flashAlpha = (int) ((1.0f - bootProgress) * 95);
            context.fill(0, 0, screenW, screenH, (flashAlpha << 24) | 0x00FFA3);

            // Expanding rectangular shockwave ring from screen center
            int cx = screenW / 2;
            int cy = screenH / 2;
            int halfW = Math.max(8, (int) (cx * bootProgress * 1.08f));
            int halfH = Math.max(8, (int) (cy * bootProgress * 1.08f));
            int ringAlpha = (int) ((1.0f - bootProgress) * 220);
            int ringCyan = (ringAlpha << 24) | 0x00F0FF;
            int ringYellow = (ringAlpha << 24) | 0xFCEE09;
            int ringMagenta = (ringAlpha << 24) | 0xFF0055;

            drawBorder(context, cx - halfW, cy - halfH, halfW * 2, halfH * 2, ringCyan);
            if (halfW > 12 && halfH > 12) {
                drawBorder(context, cx - halfW + 4, cy - halfH + 4, (halfW - 4) * 2, (halfH - 4) * 2, ringYellow);
            }
            if (halfW > 20 && halfH > 20) {
                drawBorder(context, cx - halfW + 8, cy - halfH + 8, (halfW - 8) * 2, (halfH - 8) * 2, ringMagenta);
            }

            // Animated neural HUD circuit lines locking in from 4 corners toward crosshair
            float lock = Math.min(1.0f, bootProgress * 1.3f);
            int lineLenX = (int) ((cx - 28) * lock);
            int lineLenY = (int) ((cy - 20) * lock);
            int circuitColor = (ringAlpha << 24) | 0x00FFA3;
            context.fill(10, 10, 10 + lineLenX, 11, circuitColor);
            context.fill(10, 10, 11, 10 + lineLenY, circuitColor);
            context.fill(screenW - 10 - lineLenX, 10, screenW - 10, 11, circuitColor);
            context.fill(screenW - 11, 10, screenW - 10, 10 + lineLenY, circuitColor);
            context.fill(10, screenH - 11, 10 + lineLenX, screenH - 10, circuitColor);
            context.fill(10, screenH - 10 - lineLenY, 11, screenH - 10, circuitColor);
            context.fill(screenW - 10 - lineLenX, screenH - 11, screenW - 10, screenH - 10, circuitColor);
            context.fill(screenW - 11, screenH - 10 - lineLenY, screenW - 10, screenH - 10, circuitColor);
        }

        // Top-center Sandevistan active status bar (zero overlap layout)
        float ratio = Math.max(0.0f, Math.min(1.0f, activeTicks / (float) CyberwareState.MAX_ACTIVE_TICKS));
        String seconds = String.format("%.1fs", activeTicks / 20.0f);

        int barW = 204;
        int barH = 36;
        int bx = (screenW - barW) / 2;
        int by = 10;

        drawGlowPanel(context, bx, by, barW, barH, BG_DARK, NEON_EMERALD, 0x5500FFA3, NEON_YELLOW);

        // Row 1 (by + 4): Left-aligned label (max 145px), right-aligned seconds (0.80f)
        Text label = Text.translatable("cyber.hud.sandevistan_active");
        float labelScale = Math.min(0.75f, 145.0f / Math.max(1, textRenderer.getWidth(label)));
        context.getMatrices().push();
        context.getMatrices().translate(bx + 6, by + 4, 0);
        context.getMatrices().scale(labelScale, labelScale, 1.0f);
        context.drawText(textRenderer, label, 0, 0, NEON_EMERALD, true);
        context.getMatrices().pop();

        float secScale = 0.80f;
        int secW = (int) (textRenderer.getWidth(seconds) * secScale);
        context.getMatrices().push();
        context.getMatrices().translate(bx + barW - secW - 6, by + 4, 0);
        context.getMatrices().scale(secScale, secScale, 1.0f);
        context.drawText(textRenderer, seconds, 0, 0, NEON_YELLOW, true);
        context.getMatrices().pop();

        // Row 2 (by + 15): Time-dilation readout
        context.getMatrices().push();
        context.getMatrices().translate(bx + 6, by + 15, 0);
        context.getMatrices().scale(0.68f, 0.68f, 1.0f);
        context.drawText(textRenderer, "TIME DILATION // 0.04x (-96%)", 0, 0, NEON_CYAN, true);
        context.getMatrices().pop();

        // Row 3 (by + 26..31): Segmented neon emerald/cyan progress bar with animated diagonal energy stripes
        int innerX = bx + 6;
        int innerW = barW - 12;
        int fillW = (int) (innerW * ratio);
        context.fill(innerX, by + 26, innerX + innerW, by + 31, 0x4400FFA3);
        context.fillGradient(innerX, by + 26, innerX + fillW, by + 31, NEON_EMERALD, NEON_CYAN);

        int stripeShift = (int) ((System.currentTimeMillis() / 45L) % 10);
        for (int dy = 0; dy < 5; dy++) {
            int sy = by + 26 + dy;
            for (int sx = innerX - 10 + stripeShift + dy; sx < innerX + fillW; sx += 10) {
                int xStart = Math.max(innerX, sx);
                int xEnd = Math.min(innerX + fillW, sx + 2);
                if (xEnd > xStart) {
                    context.fill(xStart, sy, xEnd, sy + 1, 0x55FFFFFF);
                }
            }
        }

        for (int sx = innerX + 12; sx < innerX + innerW; sx += 12) {
            context.fill(sx, by + 26, sx + 1, by + 31, BG_DARK);
        }
    }

    private void drawCooldownBar(DrawContext context, TextRenderer textRenderer, int screenW) {
        int cooldownTicks = CyberwareState.getCooldownTicks();
        float remainingRatio = Math.max(0.0f, Math.min(1.0f, cooldownTicks / (float) CyberwareState.MAX_COOLDOWN_TICKS));
        float progress = 1.0f - remainingRatio;
        String seconds = String.format("%.1fs", cooldownTicks / 20.0f);

        int barW = 204;
        int barH = 26;
        int bx = (screenW - barW) / 2;
        int by = 10;

        drawGlowPanel(context, bx, by, barW, barH, BG_DARK, NEON_RED, 0x44FF003C, NEON_YELLOW);

        // Row 1 (by + 4): Left-aligned label (max 145px), right-aligned seconds (0.80f)
        Text label = Text.translatable("cyber.hud.sandevistan_cooldown");
        float labelScale = Math.min(0.72f, 145.0f / Math.max(1, textRenderer.getWidth(label)));
        context.getMatrices().push();
        context.getMatrices().translate(bx + 6, by + 4, 0);
        context.getMatrices().scale(labelScale, labelScale, 1.0f);
        context.drawText(textRenderer, label, 0, 0, NEON_YELLOW, true);
        context.getMatrices().pop();

        float secScale = 0.80f;
        int secW = (int) (textRenderer.getWidth(seconds) * secScale);
        context.getMatrices().push();
        context.getMatrices().translate(bx + barW - secW - 6, by + 4, 0);
        context.getMatrices().scale(secScale, secScale, 1.0f);
        context.drawText(textRenderer, seconds, 0, 0, NEON_RED, true);
        context.getMatrices().pop();

        // Row 2 (by + 16..21): Progress bar
        int innerX = bx + 6;
        int innerW = barW - 12;
        int fillW = (int) (innerW * progress);
        context.fill(innerX, by + 16, innerX + innerW, by + 21, 0x44FF003C);
        context.fill(innerX, by + 16, innerX + fillW, by + 21, NEON_YELLOW);
    }

    private void drawReadyIndicator(DrawContext context, TextRenderer textRenderer) {
        Text readyText = Text.translatable("cyber.hud.sandevistan_ready");
        int textW = (int) (textRenderer.getWidth(readyText) * 0.8f);
        int boxW = textW + 14;
        int boxH = 14;
        int bx = 10;
        int by = 10;

        drawGlowPanel(context, bx, by, boxW, boxH, BG_DARK, NEON_CYAN, 0x4400F0FF, NEON_YELLOW);
        context.fill(bx, by, bx + 3, by + boxH, NEON_YELLOW);

        context.getMatrices().push();
        context.getMatrices().translate(bx + 7, by + 4, 0);
        context.getMatrices().scale(0.8f, 0.8f, 1.0f);
        context.drawText(textRenderer, readyText, 0, 0, NEON_CYAN, true);
        context.getMatrices().pop();
    }

    private void drawKiroshiScanner(DrawContext context, TextRenderer textRenderer, MinecraftClient client, int screenW, int screenH) {
        LivingEntity target = null;
        if (client.targetedEntity instanceof LivingEntity living && living.isAlive()) {
            target = living;
        } else {
            double range = 36.0;
            Vec3d eyePos = client.player.getCameraPosVec(1.0f);
            Vec3d lookVec = client.player.getRotationVec(1.0f);
            Vec3d endPos = eyePos.add(lookVec.multiply(range));
            Box searchBox = client.player.getBoundingBox().stretch(lookVec.multiply(range)).expand(1.0, 1.0, 1.0);
            EntityHitResult hit = ProjectileUtil.raycast(
                    client.player,
                    eyePos,
                    endPos,
                    searchBox,
                    e -> e instanceof LivingEntity && !e.isSpectator() && e.isAlive(),
                    range * range
            );
            if (hit != null && hit.getEntity() instanceof LivingEntity living) {
                target = living;
            }
        }

        if (target == null) {
            return;
        }

        int centerX = screenW / 2;
        int centerY = screenH / 2;
        int boxW = 142;
        int boxH = 46;
        int boxX = centerX + 20;
        int boxY = centerY - 22;

        // Pulsing neon cyan/yellow Kiroshi target-lock bracket reticle around center crosshair
        double pulse = Math.sin(System.currentTimeMillis() / 120.0);
        int lockOffset = 8 + (int) Math.round(pulse * 2.0);
        int tickLen = 4;
        int reticleColor = pulse >= 0.0 ? NEON_CYAN : NEON_YELLOW;
        int reticleGlow = pulse >= 0.0 ? 0x4400F0FF : 0x44FCEE09;
        // Top-left bracket
        context.fill(centerX - lockOffset - 1, centerY - lockOffset - 1, centerX - lockOffset + tickLen + 1, centerY - lockOffset + 2, reticleGlow);
        context.fill(centerX - lockOffset, centerY - lockOffset, centerX - lockOffset + tickLen, centerY - lockOffset + 1, reticleColor);
        context.fill(centerX - lockOffset, centerY - lockOffset, centerX - lockOffset + 1, centerY - lockOffset + tickLen, reticleColor);
        // Top-right bracket
        context.fill(centerX + lockOffset - tickLen, centerY - lockOffset - 1, centerX + lockOffset + 2, centerY - lockOffset + 2, reticleGlow);
        context.fill(centerX + lockOffset - tickLen + 1, centerY - lockOffset, centerX + lockOffset + 1, centerY - lockOffset + 1, reticleColor);
        context.fill(centerX + lockOffset, centerY - lockOffset, centerX + lockOffset + 1, centerY - lockOffset + tickLen, reticleColor);
        // Bottom-left bracket
        context.fill(centerX - lockOffset - 1, centerY + lockOffset - 1, centerX - lockOffset + tickLen + 1, centerY + lockOffset + 2, reticleGlow);
        context.fill(centerX - lockOffset, centerY + lockOffset, centerX - lockOffset + tickLen, centerY + lockOffset + 1, reticleColor);
        context.fill(centerX - lockOffset, centerY + lockOffset - tickLen + 1, centerX - lockOffset + 1, centerY + lockOffset + 1, reticleColor);
        // Bottom-right bracket
        context.fill(centerX + lockOffset - tickLen, centerY + lockOffset - 1, centerX + lockOffset + 2, centerY + lockOffset + 2, reticleGlow);
        context.fill(centerX + lockOffset - tickLen + 1, centerY + lockOffset, centerX + lockOffset + 1, centerY + lockOffset + 1, reticleColor);
        context.fill(centerX + lockOffset, centerY + lockOffset - tickLen + 1, centerX + lockOffset + 1, centerY + lockOffset + 1, reticleColor);

        // Connecting elbow pointer line from (centerX + 6, centerY) to scanner box
        context.fill(centerX + 6, centerY, centerX + 13, centerY + 1, NEON_CYAN);
        context.fill(centerX + 13, boxY + 10, centerX + 14, centerY + 1, NEON_CYAN);
        context.fill(centerX + 13, boxY + 10, boxX, boxY + 11, NEON_CYAN);

        // Dark obsidian box with glowing outer drop-shadow, neon cyan/yellow border, and corner ticks
        drawGlowPanel(context, boxX, boxY, boxW, boxH, BG_DARK, NEON_CYAN, 0x4400F0FF, NEON_YELLOW);
        context.fill(boxX, boxY, boxX + 2, boxY + boxH, NEON_YELLOW);

        // Row 1 (boxY + 4): Header left, distance right
        Text header = Text.translatable("cyber.hud.kiroshi_scan");
        context.getMatrices().push();
        context.getMatrices().translate(boxX + 5, boxY + 4, 0);
        context.getMatrices().scale(0.62f, 0.62f, 1.0f);
        context.drawText(textRenderer, header, 0, 0, NEON_CYAN, true);
        context.getMatrices().pop();

        String distText = String.format("%.1fm", client.player.distanceTo(target));
        int distW = (int) (textRenderer.getWidth(distText) * 0.65f);
        context.getMatrices().push();
        context.getMatrices().translate(boxX + boxW - distW - 5, boxY + 4, 0);
        context.getMatrices().scale(0.65f, 0.65f, 1.0f);
        context.drawText(textRenderer, distText, 0, 0, NEON_EMERALD, true);
        context.getMatrices().pop();

        // Row 2 (boxY + 14): Target name on its own full-width line (auto-scaled to max 130px)
        Text targetName = target.getDisplayName();
        float nameScale = Math.min(0.72f, 130.0f / Math.max(1, textRenderer.getWidth(targetName)));
        context.getMatrices().push();
        context.getMatrices().translate(boxX + 5, boxY + 14, 0);
        context.getMatrices().scale(nameScale, nameScale, 1.0f);
        context.drawText(textRenderer, targetName, 0, 0, NEON_YELLOW, true);
        context.getMatrices().pop();

        // Row 3 (boxY + 25): Exact HP readout on its own line
        float maxHp = Math.max(1.0f, target.getMaxHealth());
        float hp = Math.max(0.0f, target.getHealth());
        float hpRatio = Math.max(0.0f, Math.min(1.0f, hp / maxHp));
        String hpText = String.format("HP: %.0f / %.0f", hp, maxHp);

        context.getMatrices().push();
        context.getMatrices().translate(boxX + 5, boxY + 25, 0);
        context.getMatrices().scale(0.68f, 0.68f, 1.0f);
        context.drawText(textRenderer, hpText, 0, 0, NEON_RED, true);
        context.getMatrices().pop();

        // Row 4 (boxY + 36..40): Neon health bar with segmented ticks
        int hpBarX = boxX + 5;
        int hpBarY = boxY + 36;
        int hpBarW = boxW - 10;
        int hpFillW = (int) (hpBarW * hpRatio);
        context.fill(hpBarX, hpBarY, hpBarX + hpBarW, hpBarY + 4, 0x44FF003C);
        context.fill(hpBarX, hpBarY, hpBarX + hpFillW, hpBarY + 4, hpRatio > 0.35f ? NEON_YELLOW : NEON_RED);
        for (int sx = hpBarX + 13; sx < hpBarX + hpBarW; sx += 13) {
            context.fill(sx, hpBarY, sx + 1, hpBarY + 4, BG_DARK);
        }
    }

    private void drawSecondHeartCooldown(DrawContext context, TextRenderer textRenderer, int by) {
        String text = "SECOND HEART: " + (CyberwareState.getSecondHeartCooldown() / 20) + "s";
        int textW = (int) (textRenderer.getWidth(text) * 0.72f);
        int boxW = textW + 12;
        int boxH = 13;
        int bx = 10;

        context.fill(bx, by, bx + boxW, by + boxH, BG_DARK);
        drawBorder(context, bx, by, boxW, boxH, NEON_RED);
        context.fill(bx, by, bx + 2, by + boxH, NEON_CYAN);

        context.getMatrices().push();
        context.getMatrices().translate(bx + 6, by + 3, 0);
        context.getMatrices().scale(0.72f, 0.72f, 1.0f);
        context.drawText(textRenderer, text, 0, 0, NEON_RED, true);
        context.getMatrices().pop();
    }

    private static void drawGlowPanel(DrawContext context, int x, int y, int width, int height,
                                      int bgColor, int borderColor, int glowColor, int accentColor) {
        // 1px outer glow drop-shadow
        drawBorder(context, x - 1, y - 1, width + 2, height + 2, glowColor);
        context.fill(x, y, x + width, y + height, bgColor);
        drawBorder(context, x, y, width, height, borderColor);

        // Angled/stepped corner accent ticks
        int tick = 4;
        context.fill(x - 1, y - 1, x + tick, y, accentColor);
        context.fill(x - 1, y - 1, x, y + tick, accentColor);
        context.fill(x + width - tick, y + height, x + width + 1, y + height + 1, accentColor);
        context.fill(x + width, y + height - tick, x + width + 1, y + height + 1, accentColor);
    }

    private static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }
}
