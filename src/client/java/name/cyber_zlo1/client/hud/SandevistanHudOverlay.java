package name.cyber_zlo1.client.hud;

import name.cyber_zlo1.client.CyberClient;
import name.cyber_zlo1.client.audio.CyberOpenALSynth;
import name.cyber_zlo1.client.render.CyberPostShaderManager;
import name.cyber_zlo1.client.ui.CyberUiRenderer;
import name.cyber_zlo1.cyberware.CyberwareState;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class SandevistanHudOverlay implements HudRenderCallback {
    private boolean wasActiveLastFrame = false;
    private int deactivationAnimTicks = 0;
    private int lastKiroshiTargetId = -1;
    private long kiroshiLockTimeMs = 0L;
    private long lastPopupBucket = -1L;
    private long lastGlitchAudioMs = 0L;

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.options.hudHidden) {
            return;
        }

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        TextRenderer textRenderer = client.textRenderer;
        float tickDelta = tickCounter.getTickDelta(true);

        if (CyberClient.isInCyberpsychosisCutscene()) {
            drawCyberpsychosisCutsceneOverlay(context, textRenderer, screenW, screenH, client.player.age, tickDelta);
            return;
        }

        boolean activeNow = CyberwareState.isActive();
        if (wasActiveLastFrame && !activeNow) {
            deactivationAnimTicks = 10;
        }
        wasActiveLastFrame = activeNow;

        boolean hasTopCenterBar = activeNow || CyberwareState.getCooldownTicks() > 0;
        int centerBarLeft = (screenW - 216) / 2;
        int leftMaxW = (hasTopCenterBar && centerBarLeft < 156) ? Math.max(88, centerBarLeft - 16) : 142;

        int leftY = 10;
        if (activeNow) {
            drawActiveOverlay(context, textRenderer, screenW, screenH, tickDelta);
        } else {
            if (deactivationAnimTicks > 0) {
                if (!CyberPostShaderManager.isShaderActive()) {
                    drawDeactivationFade(context, screenW, screenH);
                }
                deactivationAnimTicks--;
            }
            if (CyberwareState.getCooldownTicks() > 0) {
                drawCooldownBar(context, textRenderer, screenW);
            } else if (CyberwareState.isSandevistanEquipped()) {
                drawReadyIndicator(context, textRenderer, leftY, leftMaxW);
                leftY += 20;
            }
        }

        if (CyberwareState.getSecondHeartCooldown() > 0) {
            drawSecondHeartCooldown(context, textRenderer, leftY, leftMaxW);
            leftY += 21;
        }

        if (CyberwareState.getCyberpsychosis() > 0
                || CyberwareState.getTotalCapacity() > CyberwareState.SAFE_CAPACITY_THRESHOLD) {
            drawCyberpsychosisMeter(context, textRenderer, leftY, leftMaxW);
            leftY += 26;
        }

        if (CyberwareState.getCyberpsychosis() >= 450 || CyberwareState.isCyberpsychoRampage()) {
            drawCyberpsychosisGlitch(context, textRenderer, screenW, screenH, client.player.age);
        } else {
            lastPopupBucket = -1L;
        }

        if (CyberwareState.isKiroshiEquipped()) {
            drawKiroshiScanner(context, textRenderer, client, screenW, screenH);
        } else {
            lastKiroshiTargetId = -1;
        }
    }

    private void drawDeactivationFade(DrawContext context, int screenW, int screenH) {
        float t = deactivationAnimTicks / 10.0f;
        int alpha = (int) (t * 36);
        int edgeColor = (alpha << 24) | 0x5CEBF5;
        int band = Math.max(12, (int) (screenH * 0.14f));
        context.fillGradient(0, 0, screenW, band, edgeColor, 0x005CEBF5);
        context.fillGradient(0, screenH - band, screenW, screenH, 0x005CEBF5, edgeColor);
    }

    private void drawActiveOverlay(DrawContext context, TextRenderer textRenderer, int screenW, int screenH, float tickDelta) {
        int activeTicks = CyberwareState.getActiveTicks();
        boolean shaderActive = CyberPostShaderManager.isShaderActive();
        long now = System.currentTimeMillis();

        // 1. Fallback subtle dark-teal edge vignette ONLY when the GPU GLSL shader is inactive
        if (!shaderActive) {
            int topH = Math.max(12, (int) (screenH * 0.18f));
            int botH = Math.max(12, (int) (screenH * 0.18f));
            context.fillGradient(0, 0, screenW, topH, 0x360C3D4A, 0x000C3D4A);
            context.fillGradient(0, screenH - botH, screenW, screenH, 0x000C3D4A, 0x360C3D4A);

            int sideW = Math.max(10, (int) (screenW * 0.12f));
            int strips = 8;
            int stripW = Math.max(1, sideW / strips);
            for (int i = 0; i < strips; i++) {
                float r = 1.0f - (i / (float) strips);
                int alpha = (int) (0x2A * r * r);
                int c = (alpha << 24) | 0x155E6B;
                int lx = i * stripW;
                context.fill(lx, 0, lx + stripW, screenH, c);
                int rx = screenW - (i + 1) * stripW;
                context.fill(rx, 0, rx + stripW, screenH, c);
            }

            // Subtle dark-teal top/bottom edge scanlines
            for (int sy = 0; sy < 16; sy += 4) {
                context.fill(0, sy, screenW, sy + 1, 0x165CEBF5);
                context.fill(0, screenH - 1 - sy, screenW, screenH - sy, 0x165CEBF5);
            }
        }

        // 2. Minimalist Cyberpunk 2077 curved/angled peripheral HUD visor arcs & corner brackets
        drawPeripheralVisorArcs(context, screenW, screenH, now);

        int margin = 8;
        int bLen = 16;
        int bracketColor = activeTicks <= 30 && ((now / 130L) % 2L == 0L)
                ? CyberUiRenderer.CP_CRIMSON
                : CyberUiRenderer.CP_CYAN;
        CyberUiRenderer.drawCornerBrackets(context, margin, margin, screenW - margin * 2, screenH - margin * 2, bLen, bracketColor);

        // Tiny acid-yellow outer corner calibration ticks
        context.fill(margin - 1, margin - 1, margin + 4, margin, CyberUiRenderer.CP_YELLOW);
        context.fill(screenW - margin - 4, margin - 1, screenW - margin + 1, margin, CyberUiRenderer.CP_YELLOW);
        context.fill(margin - 1, screenH - margin, margin + 4, screenH - margin + 1, CyberUiRenderer.CP_YELLOW);
        context.fill(screenW - margin - 4, screenH - margin, screenW - margin + 1, screenH - margin + 1, CyberUiRenderer.CP_YELLOW);

        // Minimalist corner lock-in vectors on first 12 ticks of activation (clean, non-obstructive)
        if (activeTicks > 148) {
            float exactTicks = activeTicks - tickDelta;
            float bootProgress = MathHelper.clamp((160.0f - exactTicks) / 12.0f, 0.0f, 1.0f);
            int lineAlpha = (int) ((1.0f - bootProgress) * 200);
            int lockColor = (lineAlpha << 24) | 0x5CEBF5;
            int cx = screenW / 2;
            int cy = screenH / 2;
            int lenX = (int) ((cx - 36) * bootProgress);
            int lenY = (int) ((cy - 28) * bootProgress);
            context.fill(margin + 2, margin + 2, margin + 2 + lenX, margin + 3, lockColor);
            context.fill(margin + 2, margin + 2, margin + 3, margin + 2 + lenY, lockColor);
            context.fill(screenW - margin - 2 - lenX, margin + 2, screenW - margin - 2, margin + 3, lockColor);
            context.fill(screenW - margin - 3, margin + 2, screenW - margin - 2, margin + 2 + lenY, lockColor);
            context.fill(margin + 2, screenH - margin - 3, margin + 2 + lenX, screenH - margin - 2, lockColor);
            context.fill(margin + 2, screenH - margin - 2 - lenY, margin + 3, screenH - margin - 2, lockColor);
            context.fill(screenW - margin - 2 - lenX, screenH - margin - 3, screenW - margin - 2, screenH - margin - 2, lockColor);
            context.fill(screenW - margin - 3, screenH - margin - 2 - lenY, screenW - margin - 2, screenH - margin - 2, lockColor);
        }

        // 3. Top-Center Sleek Chamfered Militech Sandevistan Duration Bar
        float ratio = MathHelper.clamp((activeTicks - tickDelta) / (float) CyberwareState.MAX_ACTIVE_TICKS, 0.0f, 1.0f);
        String seconds = String.format("%.1fs", activeTicks / 20.0f);
        boolean expiring = activeTicks <= 30;
        boolean flash = expiring && ((now / 140L) % 2L == 0L);

        int barW = 216;
        int barH = 36;
        int bx = (screenW - barW) / 2;
        int by = 10;

        int panelBorder = flash ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_CYAN;
        int panelAccent = expiring ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_YELLOW;
        CyberUiRenderer.drawDoubleChamferedPanel(
                context,
                bx,
                by,
                barW,
                barH,
                6,
                CyberUiRenderer.CP_DARK,
                panelBorder,
                panelAccent
        );

        // Live animated neural spine icon (bx + 6 .. bx + 22)
        drawNeuralSpineIcon(context, bx + 6, by + 4, 28, true, ratio);

        int contentX = bx + 27;
        int contentW = barW - 34;

        // Row 1 (by + 4.5): Left active label (max 126px), right countdown seconds (max 46px)
        Text label = Text.translatable("cyber.hud.sandevistan_active");
        CyberUiRenderer.drawClampedText(
                context,
                textRenderer,
                label,
                contentX,
                by + 4.5f,
                0.70f,
                126.0f,
                flash ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_CYAN,
                true
        );

        CyberUiRenderer.drawClampedTextRight(
                context,
                textRenderer,
                seconds,
                bx + barW - 7.0f,
                by + 4.5f,
                0.78f,
                46.0f,
                flash ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_YELLOW,
                true
        );

        // Row 2 (by + 14.5): "SANDEVISTAN // 0.04x" left + "-96% TIME" right
        CyberUiRenderer.drawClampedText(
                context,
                textRenderer,
                "SANDEVISTAN // 0.04x",
                contentX,
                by + 14.5f,
                0.60f,
                124.0f,
                CyberUiRenderer.CP_EMERALD,
                true
        );
        CyberUiRenderer.drawClampedTextRight(
                context,
                textRenderer,
                "-96% TIME",
                bx + barW - 7.0f,
                by + 14.5f,
                0.56f,
                50.0f,
                CyberUiRenderer.CP_TEXT_MUTED,
                false
        );

        // Row 3 (by + 24..29): Segmented angled emerald-cyan-yellow progress bar
        int barFillColor;
        if (ratio <= 0.15f) {
            barFillColor = flash ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_YELLOW;
        } else if (ratio <= 0.35f) {
            barFillColor = CyberUiRenderer.CP_YELLOW;
        } else {
            barFillColor = (ratio > 0.65f) ? CyberUiRenderer.CP_EMERALD : CyberUiRenderer.CP_CYAN;
        }

        CyberUiRenderer.drawSegmentedGauge(
                context,
                contentX,
                by + 24,
                contentW,
                5,
                ratio,
                18,
                barFillColor,
                0x305CEBF5,
                0x66060810
        );

        // Subtle traveling energy highlight glint along the filled bar
        int fillW = Math.round(contentW * ratio);
        if (fillW > 8) {
            int glintX = contentX + (int) ((now / 18L) % Math.max(1, fillW - 6));
            context.fill(glintX, by + 24, Math.min(contentX + fillW, glintX + 6), by + 25, 0xAAFFFFFF);
        }
    }

    private static void drawPeripheralVisorArcs(DrawContext context, int screenW, int screenH, long now) {
        int cx = screenW / 2;
        int cy = screenH / 2;
        float radiusX = Math.min(screenW * 0.42f, Math.max(120.0f, screenW * 0.38f));
        float radiusY = Math.min(screenH * 0.40f, Math.max(70.0f, screenH * 0.36f));
        double pulse = 0.55 + 0.45 * Math.sin(now / 260.0);
        int arcAlpha = (int) (0x48 + pulse * 0x40);
        int arcColor = (arcAlpha << 24) | 0x5CEBF5;
        int tickColor = ((arcAlpha + 0x28) << 24) | 0x39FFB4;

        int steps = 26;
        for (int i = 0; i <= steps; i++) {
            // Skip tiny micro-gaps for sleek Cyberpunk 2077 optical HUD dashes
            if (i == 8 || i == 18) {
                continue;
            }
            double t = (i / (double) steps) * 2.0 - 1.0; // -1 .. +1
            double angle = t * 0.58; // ~ +/- 33 deg
            int dx = (int) Math.round(Math.cos(angle) * radiusX);
            int dy = (int) Math.round(Math.sin(angle) * radiusY);

            int lx = cx - dx;
            int rx = cx + dx;
            int py = cy + dy;

            context.fill(lx, py, lx + 1, py + 2, arcColor);
            context.fill(rx, py, rx + 1, py + 2, arcColor);

            // Calibration ticks along the peripheral optical arc
            if (i == 0 || i == steps / 2 || i == steps) {
                context.fill(lx - 4, py, lx, py + 1, tickColor);
                context.fill(rx + 1, py, rx + 5, py + 1, tickColor);
            }
        }
    }

    private static void drawNeuralSpineIcon(DrawContext context, int ix, int iy, int height, boolean active, float chargeRatio) {
        int boxW = 16;
        CyberUiRenderer.drawChamferedPanel(
                context,
                ix,
                iy,
                boxW,
                height,
                3,
                0xCC0A0509,
                active ? CyberUiRenderer.CP_CYAN_DIM : CyberUiRenderer.CP_CRIMSON_DARK,
                0
        );

        int midX = ix + boxW / 2;
        context.fill(midX - 1, iy + 2, midX + 1, iy + height - 2, active ? 0x555CEBF5 : 0x33FF4D5A);

        int segments = 6;
        int stepY = Math.max(3, (height - 4) / segments);
        int pulseIdx = (int) ((System.currentTimeMillis() / 85L) % segments);

        for (int s = 0; s < segments; s++) {
            int sy = iy + 2 + s * stepY;
            int sh = Math.max(2, stepY - 1);
            boolean lit;
            int segColor;
            if (active) {
                lit = true;
                segColor = (s == pulseIdx) ? CyberUiRenderer.CP_YELLOW
                        : ((s % 2 == 0) ? CyberUiRenderer.CP_EMERALD : CyberUiRenderer.CP_CYAN);
            } else {
                float threshold = 1.0f - ((s + 1) / (float) segments);
                lit = chargeRatio >= threshold;
                segColor = lit ? CyberUiRenderer.CP_YELLOW : 0x38FF4D5A;
            }
            context.fill(midX - 3, sy, midX + 3, sy + sh, segColor);
            if (lit) {
                context.fill(midX - 5, sy + 1, midX - 3, sy + sh - 1, active ? CyberUiRenderer.CP_CYAN : 0xAAFF4D5A);
                context.fill(midX + 3, sy + 1, midX + 5, sy + sh - 1, active ? CyberUiRenderer.CP_CYAN : 0xAAFF4D5A);
            }
        }
    }

    private void drawCooldownBar(DrawContext context, TextRenderer textRenderer, int screenW) {
        int cooldownTicks = CyberwareState.getCooldownTicks();
        float remainingRatio = MathHelper.clamp(cooldownTicks / (float) CyberwareState.MAX_COOLDOWN_TICKS, 0.0f, 1.0f);
        float progress = 1.0f - remainingRatio;
        String seconds = String.format("%.1fs", cooldownTicks / 20.0f);

        int barW = 216;
        int barH = 28;
        int bx = (screenW - barW) / 2;
        int by = 10;

        CyberUiRenderer.drawDoubleChamferedPanel(
                context,
                bx,
                by,
                barW,
                barH,
                5,
                CyberUiRenderer.CP_CRIMSON_BG,
                CyberUiRenderer.CP_CRIMSON,
                CyberUiRenderer.CP_YELLOW
        );
        drawNeuralSpineIcon(context, bx + 6, by + 4, 20, false, progress);

        int contentX = bx + 27;
        int contentW = barW - 34;

        Text label = Text.translatable("cyber.hud.sandevistan_cooldown");
        CyberUiRenderer.drawClampedText(
                context,
                textRenderer,
                label,
                contentX,
                by + 4.5f,
                0.70f,
                128.0f,
                CyberUiRenderer.CP_YELLOW,
                true
        );

        CyberUiRenderer.drawClampedTextRight(
                context,
                textRenderer,
                seconds,
                bx + barW - 7.0f,
                by + 4.5f,
                0.78f,
                46.0f,
                CyberUiRenderer.CP_CRIMSON,
                true
        );

        CyberUiRenderer.drawSegmentedGauge(
                context,
                contentX,
                by + 16,
                contentW,
                5,
                progress,
                18,
                CyberUiRenderer.CP_YELLOW,
                0x36FF4D5A,
                0x660A0406
        );
    }

    private void drawReadyIndicator(DrawContext context, TextRenderer textRenderer, int by, int maxBoxW) {
        Text readyText = Text.translatable("cyber.hud.sandevistan_ready");
        int idealW = (int) (textRenderer.getWidth(readyText) * 0.76f) + 20;
        int boxW = MathHelper.clamp(idealW, 108, Math.max(108, maxBoxW));
        int boxH = 16;
        int bx = 10;

        CyberUiRenderer.drawChamferedPanel(
                context,
                bx,
                by,
                boxW,
                boxH,
                4,
                CyberUiRenderer.CP_CRIMSON_BG,
                CyberUiRenderer.CP_CYAN,
                CyberUiRenderer.CP_YELLOW
        );

        // Pulsing status diode pip
        boolean pulse = ((System.currentTimeMillis() / 420L) % 2L == 0L);
        context.fill(bx + 5, by + 5, bx + 8, by + 11, pulse ? CyberUiRenderer.CP_EMERALD : CyberUiRenderer.CP_CYAN);

        CyberUiRenderer.drawClampedText(
                context,
                textRenderer,
                readyText,
                bx + 11,
                by + 5.0f,
                0.74f,
                boxW - 18.0f,
                CyberUiRenderer.CP_CYAN,
                true
        );
    }

    private void drawSecondHeartCooldown(DrawContext context, TextRenderer textRenderer, int by, int maxBoxW) {
        int cdTicks = CyberwareState.getSecondHeartCooldown();
        String secStr = (cdTicks / 20) + "s";
        int boxW = Math.min(136, maxBoxW);
        int boxH = 17;
        int bx = 10;

        CyberUiRenderer.drawChamferedPanel(
                context,
                bx,
                by,
                boxW,
                boxH,
                4,
                CyberUiRenderer.CP_CRIMSON_BG,
                CyberUiRenderer.CP_CRIMSON,
                CyberUiRenderer.CP_CYAN
        );

        CyberUiRenderer.drawClampedText(
                context,
                textRenderer,
                "SECOND HEART",
                bx + 6,
                by + 3.5f,
                0.66f,
                boxW - 36.0f,
                CyberUiRenderer.CP_CRIMSON,
                true
        );

        CyberUiRenderer.drawClampedTextRight(
                context,
                textRenderer,
                secStr,
                bx + boxW - 6.0f,
                by + 3.5f,
                0.68f,
                26.0f,
                CyberUiRenderer.CP_YELLOW,
                true
        );

        // 120s (2400 tick) recharge progress strip
        float recharge = MathHelper.clamp(1.0f - (cdTicks / 2400.0f), 0.0f, 1.0f);
        CyberUiRenderer.drawSegmentedGauge(
                context,
                bx + 6,
                by + 12,
                boxW - 13,
                2,
                recharge,
                10,
                CyberUiRenderer.CP_CRIMSON,
                0x30FF4D5A,
                0x55080306
        );
    }

    private void drawCyberpsychosisMeter(DrawContext context, TextRenderer textRenderer, int by, int maxBoxW) {
        int psychosis = CyberwareState.getCyberpsychosis();
        int pct = Math.min(100, Math.max(0, psychosis / 10));
        int accent = pct >= 70 ? CyberUiRenderer.CP_CRIMSON
                : (pct >= 40 ? CyberUiRenderer.CP_YELLOW : CyberUiRenderer.CP_CYAN);

        int boxW = Math.min(138, maxBoxW);
        int boxH = 22;
        int bx = 10;

        CyberUiRenderer.drawChamferedPanel(
                context,
                bx,
                by,
                boxW,
                boxH,
                5,
                CyberUiRenderer.CP_CRIMSON_BG,
                accent,
                accent
        );

        Text label = Text.translatable("cyber.hud.cyberpsychosis");
        CyberUiRenderer.drawClampedText(
                context,
                textRenderer,
                label,
                bx + 6,
                by + 3.5f,
                0.64f,
                boxW - 38.0f,
                accent,
                true
        );

        CyberUiRenderer.drawClampedTextRight(
                context,
                textRenderer,
                pct + "%",
                bx + boxW - 6.0f,
                by + 3.5f,
                0.68f,
                28.0f,
                pct >= 70 ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_YELLOW,
                true
        );

        CyberUiRenderer.drawSegmentedGauge(
                context,
                bx + 6,
                by + 13,
                boxW - 13,
                5,
                pct / 100.0f,
                12,
                accent,
                0x307A1924,
                0x66080306
        );
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
            lastKiroshiTargetId = -1;
            return;
        }

        long now = System.currentTimeMillis();
        if (target.getId() != lastKiroshiTargetId) {
            lastKiroshiTargetId = target.getId();
            kiroshiLockTimeMs = now;
            CyberOpenALSynth.play("kiroshi_lock", 0.75f, 1.0f);
        }

        boolean hostile = target instanceof HostileEntity
                || target instanceof Monster
                || (target instanceof MobEntity mob && mob.getTarget() == client.player);

        int centerX = screenW / 2;
        int centerY = screenH / 2;
        int boxW = 156;
        int boxH = 54;
        int boxX = Math.min(screenW - boxW - 6, centerX + 22);
        int boxY = MathHelper.clamp(centerY - 27, 10, screenH - boxH - 10);

        // 1. V's Kiroshi Optics 4-corner locking reticle snapping cleanly onto the crosshair
        long lockElapsed = now - kiroshiLockTimeMs;
        float lockSnap = lockElapsed < 220L ? (1.0f - lockElapsed / 220.0f) : 0.0f;
        double pulse = Math.sin(now / 140.0);
        int lockRadius = 8 + Math.round(lockSnap * 11.0f) + (int) Math.round(pulse * 1.2);
        int reticleColor = hostile ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_CYAN;
        int reticleGlow = hostile ? 0x44FF4D5A : 0x445CEBF5;

        CyberUiRenderer.drawCornerBracketsWithGlow(
                context,
                centerX - lockRadius,
                centerY - lockRadius,
                lockRadius * 2 + 1,
                lockRadius * 2 + 1,
                4,
                reticleColor,
                reticleGlow
        );

        // Micro crosshair side ticks
        context.fill(centerX - lockRadius - 4, centerY, centerX - lockRadius - 1, centerY + 1, reticleColor);
        context.fill(centerX + lockRadius + 2, centerY, centerX + lockRadius + 5, centerY + 1, reticleColor);

        // Connecting Kiroshi lead trace from reticle to chamfered target card
        int leadStartX = centerX + lockRadius + 5;
        if (boxX > leadStartX + 4) {
            int elbowX = (leadStartX + boxX) / 2;
            CyberUiRenderer.drawHLine(context, leadStartX, elbowX, centerY, reticleColor);
            CyberUiRenderer.drawVLine(context, elbowX, centerY, boxY + 11, reticleColor);
            CyberUiRenderer.drawHLine(context, elbowX, boxX, boxY + 11, reticleColor);
            CyberUiRenderer.drawDiamondNode(context, boxX, boxY + 11, 1, CyberUiRenderer.CP_YELLOW);
        }

        // 2. Chamfered Dark-Red-Obsidian Target Card
        int panelBorder = hostile ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_CYAN;
        int panelAccent = hostile ? CyberUiRenderer.CP_YELLOW : CyberUiRenderer.CP_CRIMSON;
        CyberUiRenderer.drawDoubleChamferedPanel(
                context,
                boxX,
                boxY,
                boxW,
                boxH,
                6,
                CyberUiRenderer.CP_CRIMSON_BG,
                panelBorder,
                panelAccent
        );

        // Subtle header divider line
        context.fill(boxX + 6, boxY + 12, boxX + boxW - 4, boxY + 13, hostile ? 0x55FF4D5A : 0x445CEBF5);

        // Row 1 (boxY + 4): KIROSHI // SCAN left + HOSTILE/NEUTRAL pill badge right
        Text header = Text.translatable("cyber.hud.kiroshi_scan");
        CyberUiRenderer.drawClampedText(
                context,
                textRenderer,
                header,
                boxX + 7,
                boxY + 4.0f,
                0.56f,
                88.0f,
                CyberUiRenderer.CP_CYAN,
                true
        );

        String badgeText = hostile ? "HOSTILE" : "NEUTRAL";
        int badgeColor = hostile ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_CYAN;
        int badgeBg = hostile ? 0x44FF4D5A : 0x335CEBF5;
        int badgeW = 46;
        int badgeH = 8;
        int badgeX = boxX + boxW - badgeW - 5;
        int badgeY = boxY + 3;
        CyberUiRenderer.drawChamferedPanel(context, badgeX, badgeY, badgeW, badgeH, 2, badgeBg, badgeColor, 0);
        CyberUiRenderer.drawClampedTextCentered(
                context,
                textRenderer,
                badgeText,
                badgeX + badgeW * 0.5f,
                badgeY + 1.5f,
                0.54f,
                badgeW - 6.0f,
                badgeColor,
                false
        );

        // Row 2 (boxY + 15): Target display name left + distance right
        String distText = String.format("%.1fm", client.player.distanceTo(target));
        CyberUiRenderer.drawClampedTextRight(
                context,
                textRenderer,
                distText,
                boxX + boxW - 6.0f,
                boxY + 15.0f,
                0.64f,
                38.0f,
                CyberUiRenderer.CP_CYAN,
                true
        );
        CyberUiRenderer.drawClampedText(
                context,
                textRenderer,
                target.getDisplayName(),
                boxX + 6,
                boxY + 15.0f,
                0.70f,
                boxW - 52.0f,
                CyberUiRenderer.CP_YELLOW,
                true
        );

        // Row 3 (boxY + 26): Exact HP left + ARMOR right
        float maxHp = Math.max(1.0f, target.getMaxHealth());
        float hp = Math.max(0.0f, target.getHealth());
        float hpRatio = MathHelper.clamp(hp / maxHp, 0.0f, 1.0f);
        String hpText = String.format("HP %.0f / %.0f", hp, maxHp);

        CyberUiRenderer.drawClampedText(
                context,
                textRenderer,
                hpText,
                boxX + 6,
                boxY + 26.0f,
                0.62f,
                84.0f,
                CyberUiRenderer.CP_CRIMSON,
                true
        );

        int armor = target.getArmor();
        String armorText = "ARM " + armor;
        CyberUiRenderer.drawClampedTextRight(
                context,
                textRenderer,
                armorText,
                boxX + boxW - 6.0f,
                boxY + 26.0f,
                0.62f,
                50.0f,
                armor > 0 ? CyberUiRenderer.CP_CYAN : CyberUiRenderer.CP_TEXT_MUTED,
                true
        );

        // Row 4 (boxY + 36..41): Segmented coral-red HP bar
        int barX = boxX + 6;
        int barW = boxW - 12;
        CyberUiRenderer.drawSegmentedGauge(
                context,
                barX,
                boxY + 36,
                barW,
                5,
                hpRatio,
                14,
                hpRatio > 0.30f ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_CRIMSON_BRIGHT,
                0x367A1924,
                0x660A0408
        );

        // Row 5 (boxY + 44..47): Segmented ice-cyan ARMOR bar (above bottom-right chamfer)
        float armRatio = MathHelper.clamp(armor / 20.0f, 0.0f, 1.0f);
        CyberUiRenderer.drawSegmentedGauge(
                context,
                barX,
                boxY + 44,
                barW - 4,
                3,
                armRatio,
                10,
                CyberUiRenderer.CP_CYAN,
                0x265CEBF5,
                0x55060B10
        );
    }

    private static final String[][] ERROR_MESSAGES = {
            {"CRITICAL: LIMBIC_SYSTEM_HIJACK", "ПОТЕРЯ КОНТРОЛЯ МОТОРИКИ"},
            {"RELIC_BIOS // NEURAL_SYNAPSE_ERR", "СБОЙ НЕЙРОСЕТИ: КИБЕРПСИХОЗ"},
            {"SANDEVISTAN // FORCED_OVERDRIVE", "САНДЕВИСТАН: АВТО-ФОРСАЖ"},
            {"ERR 0xDEAD: HUMANITY_CORRUPTED", "ИНДЕКС ЧЕЛОВЕЧНОСТИ: 0.00%"},
            {"MANTIS_ACTUATORS: LETHAL_MODE", "КЛИНКИ БОГОМОЛА: АВТО-АТАКА"},
            {"KIROSHI_OPTICS // BUFFER_BLEED", "ГАЛЛЮЦИНАЦИИ ОПТИЧЕСКОГО БУФЕРА"},
            {"KERNEL_PANIC // INJECT_BLOCKER!", "ВКОЛИТЕ ИММУНОБЛОКАТОР [ПКМ]!"}
    };

    private void drawCyberpsychosisGlitch(DrawContext context, TextRenderer textRenderer, int screenW, int screenH, int playerAge) {
        int psychosis = CyberwareState.getCyberpsychosis();
        boolean rampage = CyberwareState.isCyberpsychoRampage();
        boolean shaderActive = CyberPostShaderManager.isCyberpsychosisShaderActive();
        float intensity = MathHelper.clamp((psychosis - 450) / 550.0f, 0.0f, 1.0f);
        long now = System.currentTimeMillis();
        long timeBucket = now / 75L;
        long popupBucket = timeBucket / (rampage ? 2L : 4L);

        // Authentic Cyberpunk 2077 OpenAL Relic glitch spike audio
        if (popupBucket != lastPopupBucket) {
            lastPopupBucket = popupBucket;
            long minInterval = rampage ? 300L : 580L;
            if (now - lastGlitchAudioMs >= minInterval) {
                lastGlitchAudioMs = now;
                float rng = ((Math.abs(now * 31L + playerAge * 17L)) % 25L) / 100.0f;
                CyberOpenALSynth.play("cyberpsychosis_glitch", rampage ? 0.55f : 0.35f, 0.9f + rng);
            }
        }

        // Fallback full-screen crimson edge vignette & optical tear bands when GPU shader is inactive
        if (!shaderActive) {
            if (rampage) {
                float pulse = 0.65f + 0.35f * (float) Math.sin(now / 90.0);
                int vigAlpha = (int) (0x4A * pulse);
                int vigTopColor = (vigAlpha << 24) | 0xFF4D5A;
                int bandH = Math.max(16, (int) (screenH * 0.20f));
                context.fillGradient(0, 0, screenW, bandH, vigTopColor, 0x00FF4D5A);
                context.fillGradient(0, screenH - bandH, screenW, screenH, 0x00FF4D5A, vigTopColor);
            }

            int tearCount = rampage ? 18 : Math.max(3, (int) (intensity * 10));
            for (int i = 0; i < tearCount; i++) {
                long seed = Math.abs(timeBucket * 53L + i * 131L + playerAge * 17L);
                int y = (int) (seed % Math.max(1, screenH - 6));
                int h = 1 + (int) ((seed >> 3) % (rampage ? 4 : 2));
                int maxW = Math.max(40, rampage ? (int) (screenW * 0.65f) : (int) (screenW * 0.36f));
                int w = 28 + (int) ((seed * 5L) % maxW);
                int x = (int) ((seed * 11L) % Math.max(1, screenW - Math.min(screenW - 1, w)));
                int barColor = switch (i % 3) {
                    case 0 -> 0x48FF4D5A;
                    case 1 -> 0x3C5CEBF5;
                    default -> 0x440C0408;
                };
                context.fill(x, y, Math.min(screenW, x + w), Math.min(screenH, y + h), barColor);

                if (i % 2 == 0 && y + h + 2 < screenH) {
                    int shift = (int) ((seed % 9) - 4);
                    context.fill(Math.max(0, x + shift), y + h + 1, Math.min(screenW, x + w + shift), y + h + 2, 0x3C5CEBF5);
                }
            }
        } else {
            // Subtle crisp RGB HUD micro-glitches complementing the GPU shader
            int microTears = rampage ? 6 : 2;
            for (int i = 0; i < microTears; i++) {
                long seed = Math.abs(timeBucket * 59L + i * 149L + playerAge * 19L);
                int y = (int) (seed % Math.max(1, screenH - 4));
                int w = 24 + (int) ((seed * 7L) % Math.max(32, screenW / 4));
                int x = (int) ((seed * 13L) % Math.max(1, screenW - Math.min(screenW - 1, w)));
                context.fill(x, y, Math.min(screenW, x + w), y + 1, (i % 2 == 0) ? 0x445CEBF5 : 0x44FF4D5A);
            }
        }

        // Cascading Stylized Cyberpunk 2077 Terminal Error Windows
        int windowCount = rampage ? 14 : (2 + (int) (intensity * 4));
        int cx = screenW / 2;
        int cy = screenH / 2;
        for (int i = 0; i < windowCount; i++) {
            long seed = Math.abs(i * 4999L + popupBucket * 101L);
            int ww = 116 + (int) (seed % 40);
            int wh = 38 + (int) ((seed >> 5) % 10);
            int maxWx = Math.max(1, screenW - ww - 4);
            int maxWy = Math.max(1, screenH - wh - 4);

            int wx;
            int wy;
            if (!rampage) {
                boolean onLeft = (i % 2 == 0);
                wx = onLeft ? (4 + (int) (seed % Math.max(1, screenW / 5)))
                            : Math.max(4, screenW - ww - 4 - (int) (seed % Math.max(1, screenW / 5)));
                int minWy = Math.min(maxWy, 82);
                wy = minWy + (int) ((seed * 7L) % Math.max(1, maxWy - minWy + 1));
            } else {
                wx = 4 + (int) ((seed * 17L) % maxWx);
                int minWy = Math.min(maxWy, 80);
                wy = minWy + (int) ((seed * 31L) % Math.max(1, maxWy - minWy + 1));
                int winCx = wx + ww / 2;
                int winCy = wy + wh / 2;
                if (Math.abs(winCx - cx) < 62 && Math.abs(winCy - cy) < 38) {
                    wx = (winCx < cx) ? Math.max(4, cx - 62 - ww) : Math.min(maxWx, cx + 62);
                }
            }

            int rng = (int) ((playerAge * 37L + i * 73L) & 0x7FFF);
            if ((playerAge + i * 3) % 5 == 0) {
                wx = Math.max(2, Math.min(maxWx, wx + (rng % 5 - 2)));
                wy = Math.max(2, Math.min(maxWy, wy + ((rng >> 3) % 5 - 2)));
            }

            drawErrorPopupWindow(context, textRenderer, wx, wy, ww, wh, i, playerAge, now);
        }

        // Authentic Cyberpunk 2077 "RELIC MALFUNCTION DETECTED" warning box (positioned cleanly at by = 52 below Sandevistan bar)
        if (rampage || (psychosis >= 700 && ((now / 450L) % 3L != 0L))) {
            int boxW = Math.min(238, screenW - 20);
            int boxH = 24;
            int bx = (screenW - boxW) / 2;
            int by = 52;
            boolean glitchFrame = ((now / 120L) % 4L == 0L);
            int jitterX = (rampage && glitchFrame) ? ((playerAge % 2 == 0) ? 1 : -1) : 0;

            CyberUiRenderer.drawDoubleChamferedPanel(
                    context,
                    bx + jitterX,
                    by,
                    boxW,
                    boxH,
                    6,
                    0xEA18060B,
                    glitchFrame ? CyberUiRenderer.CP_CYAN : CyberUiRenderer.CP_CRIMSON,
                    CyberUiRenderer.CP_CYAN
            );

            // Top micro-line: RELIC MALFUNCTION DETECTED
            CyberUiRenderer.drawRgbGlitchText(
                    context,
                    textRenderer,
                    "RELIC MALFUNCTION DETECTED",
                    bx + jitterX + 10.0f,
                    by + 3.0f,
                    0.54f,
                    boxW - 20.0f,
                    CyberUiRenderer.CP_CYAN,
                    true
            );

            // Bottom main alert line: localized "⚠ КИБЕРПСИХОЗ // СБОЙ НЕЙРОСЕТИ ⚠" / "⚠ CYBERPSYCHOSIS // SYSTEM OVERRIDE ⚠"
            Text warning = Text.translatable("cyber.hud.cyberpsychosis_rampage");
            int alertColor = ((now / 190L) % 2L == 0L) ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_YELLOW;
            CyberUiRenderer.drawClampedTextCentered(
                    context,
                    textRenderer,
                    warning,
                    bx + jitterX + boxW * 0.5f,
                    by + 12.0f,
                    0.72f,
                    boxW - 16.0f,
                    alertColor,
                    true
            );
        }
    }

    private void drawErrorPopupWindow(
            DrawContext context,
            TextRenderer textRenderer,
            int wx,
            int wy,
            int ww,
            int wh,
            int index,
            int playerAge,
            long now
    ) {
        int borderColor = (index % 3 == 0) ? CyberUiRenderer.CP_CRIMSON
                : ((index % 3 == 1) ? CyberUiRenderer.CP_CYAN : CyberUiRenderer.CP_YELLOW);
        int accentColor = (index % 2 == 0) ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_CYAN;

        CyberUiRenderer.drawChamferedPanel(
                context,
                wx,
                wy,
                ww,
                wh,
                5,
                CyberUiRenderer.CP_CRIMSON_BG,
                borderColor,
                accentColor
        );

        // RGB-split header bar
        int headerH = 10;
        context.fill(wx + 2, wy + 1, wx + ww - 1, wy + headerH, 0xEE350814);
        context.fill(wx + 2, wy + headerH, wx + ww - 1, wy + headerH + 1, borderColor);

        // Pixel-art warning triangle in header left
        int iconX = wx + 4;
        int iconY = wy + 2;
        context.fill(iconX + 3, iconY, iconX + 4, iconY + 1, CyberUiRenderer.CP_YELLOW);
        context.fill(iconX + 2, iconY + 1, iconX + 5, iconY + 3, CyberUiRenderer.CP_YELLOW);
        context.fill(iconX + 1, iconY + 3, iconX + 6, iconY + 5, CyberUiRenderer.CP_YELLOW);
        context.fill(iconX, iconY + 5, iconX + 7, iconY + 7, CyberUiRenderer.CP_YELLOW);
        context.fill(iconX + 3, iconY + 2, iconX + 4, iconY + 5, 0xFF14040A);
        context.fill(iconX + 3, iconY + 6, iconX + 4, iconY + 7, 0xFF14040A);

        String title = "RELIC_ERR 0x" + Integer.toHexString(0x2077 + index * 419).toUpperCase();
        CyberUiRenderer.drawRgbGlitchText(
                context,
                textRenderer,
                title,
                wx + 13.0f,
                wy + 3.0f,
                0.56f,
                ww - 28.0f,
                CyberUiRenderer.CP_YELLOW,
                (index % 2 == 0)
        );

        // [X] close badge top-right
        int closeX = wx + ww - 10;
        int closeY = wy + 2;
        context.fill(closeX, closeY, closeX + 8, closeY + 7, CyberUiRenderer.CP_CRIMSON);
        CyberUiRenderer.drawScaledText(context, textRenderer, "X", closeX + 2.0f, closeY + 1.0f, 0.55f, 0xFFFFFFFF, false);

        // Error body lines (auto-clamped, zero overlap)
        String[] pair = ERROR_MESSAGES[(index + (playerAge / 20)) % ERROR_MESSAGES.length];
        float maxTextW = ww - 10.0f;
        boolean rgbSplit = ((now / 95L + index) % 3L == 0L);

        CyberUiRenderer.drawRgbGlitchText(
                context,
                textRenderer,
                pair[0],
                wx + 5.0f,
                wy + 13.5f,
                0.55f,
                maxTextW,
                CyberUiRenderer.CP_CRIMSON,
                rgbSplit
        );

        CyberUiRenderer.drawClampedText(
                context,
                textRenderer,
                pair[1],
                wx + 5.0f,
                wy + 22.5f,
                0.52f,
                maxTextW,
                CyberUiRenderer.CP_TEXT_LIGHT,
                false
        );

        // Bottom segmented corruption bar
        float corruptRatio = ((now / 45L + index * 17L) % 100L) / 100.0f;
        CyberUiRenderer.drawSegmentedGauge(
                context,
                wx + 5,
                wy + wh - 6,
                ww - 14,
                3,
                corruptRatio,
                10,
                (index % 2 == 0) ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_CYAN,
                0x36FF4D5A,
                0x6612040A
        );
    }

    private void drawCyberpsychosisCutsceneOverlay(
            DrawContext context,
            TextRenderer textRenderer,
            int screenW,
            int screenH,
            int playerAge,
            float tickDelta
    ) {
        float progress = CyberClient.getCutsceneProgress(tickDelta);
        long now = System.currentTimeMillis();
        boolean shaderActive = CyberPostShaderManager.isCyberpsychosisShaderActive();

        // 1. Heartbeat-synced crimson neural vignette & corner fractures
        float beatFreq = progress < 0.33f ? 14.0f : (progress < 0.75f ? 26.0f : 42.0f);
        float heartbeat = 0.52f + 0.48f * (float) Math.abs(Math.sin(progress * beatFreq));
        if (!shaderActive) {
            int vigAlpha = Math.min(0x9A, (int) ((0x2C + progress * 0x4A) * heartbeat));
            int vigColor = (vigAlpha << 24) | 0xFF4D5A;
            int vigBand = Math.max(18, (int) (screenH * 0.24f));
            context.fillGradient(0, 0, screenW, vigBand, vigColor, 0x00FF4D5A);
            context.fillGradient(0, screenH - vigBand, screenW, screenH, 0x00FF4D5A, vigColor);
        }

        int barH = Math.max(2, (int) (screenH * 0.14f * Math.min(1.0f, progress * 4.0f)));

        // Neural vein fractures pulsing inward from 4 corners in Arasaka Crimson & Ice-Cyan
        int veinAlpha = Math.min(220, (int) ((95 + 120 * progress) * heartbeat));
        int veinRed = (veinAlpha << 24) | 0xFF4D5A;
        int veinCyan = ((veinAlpha / 2) << 24) | 0x5CEBF5;
        float reach = 0.12f + progress * 0.22f;
        int maxReachX = (int) (screenW * reach);
        int maxReachY = (int) (screenH * reach);
        for (int c = 0; c < 4; c++) {
            boolean right = (c == 1 || c == 3);
            boolean bottom = (c == 2 || c == 3);
            int originX = right ? screenW - 1 : 0;
            int originY = bottom ? screenH - barH : barH;
            int dirX = right ? -1 : 1;
            int dirY = bottom ? -1 : 1;
            for (int v = 0; v < 4; v++) {
                int steps = 6 + v * 2;
                int curX = originX;
                int curY = originY;
                for (int s = 0; s < steps; s++) {
                    long seed = (c * 97L + v * 31L + s * 17L + (playerAge / 3) * 13L);
                    int stepX = Math.max(4, (maxReachX / steps) + (int) (seed % 7) - 2);
                    int stepY = Math.max(3, (maxReachY / steps) + (int) ((seed >> 3) % 5) - 1);
                    int nextX = curX + dirX * stepX;
                    int nextY = curY + dirY * ((s + v) % 2 == 0 ? stepY : Math.max(1, stepY / 2));
                    int minX = Math.max(0, Math.min(curX, nextX));
                    int maxX = Math.min(screenW, Math.max(curX, nextX) + 1);
                    int minY = Math.max(barH, Math.min(curY, nextY));
                    int maxY = Math.min(screenH - barH, Math.max(curY, nextY) + 1);
                    context.fill(minX, curY, maxX, curY + 1, (v % 2 == 0 && s % 2 == 0) ? veinCyan : veinRed);
                    context.fill(nextX, minY, nextX + 1, maxY, veinRed);
                    curX = nextX;
                    curY = nextY;
                }
            }
        }

        // 2. Sleek Cinema Widescreen Letterbox Bars with Cyberpunk 2077 Red/Cyan/Yellow trim
        context.fill(0, 0, screenW, barH, 0xF4080306);
        context.fill(0, screenH - barH, screenW, screenH, 0xF4080306);

        // Subtle scanlines inside letterbox bars
        for (int sy = 2; sy < barH - 2; sy += 3) {
            context.fill(0, sy, screenW, sy + 1, CyberUiRenderer.CP_SCANLINE);
            context.fill(0, screenH - barH + sy, screenW, screenH - barH + sy + 1, CyberUiRenderer.CP_SCANLINE);
        }

        context.fill(0, barH - 2, screenW, barH, CyberUiRenderer.CP_CRIMSON);
        context.fill(0, screenH - barH, screenW, screenH - barH + 2, CyberUiRenderer.CP_CRIMSON);
        context.fill(0, barH - 3, screenW, barH - 2, 0x555CEBF5);
        context.fill(0, screenH - barH + 2, screenW, screenH - barH + 3, 0x555CEBF5);

        int trimW = Math.max(24, screenW / 6);
        context.fill(0, barH - 3, trimW, barH - 2, CyberUiRenderer.CP_YELLOW);
        context.fill(screenW - trimW, barH - 3, screenW, barH - 2, CyberUiRenderer.CP_YELLOW);
        context.fill(0, screenH - barH + 2, trimW, screenH - barH + 3, CyberUiRenderer.CP_YELLOW);
        context.fill(screenW - trimW, screenH - barH + 2, screenW, screenH - barH + 3, CyberUiRenderer.CP_YELLOW);

        // 3. Authentic Red/Cyan 2077 BIOS Telemetry inside top & bottom cinema bars
        if (barH >= 14) {
            String fullLine;
            float localT;
            int lineColor;
            if (progress < 0.33f) {
                fullLine = "[RELIC_ERR 0x2077] // КРИТИЧЕСКИЙ ОТКАЗ НЕЙРОКОРЫ — ЛИМБИЧЕСКИЙ ПРОРЫВ";
                localT = progress / 0.33f;
                lineColor = CyberUiRenderer.CP_CRIMSON;
            } else if (progress < 0.75f) {
                fullLine = "[WARNING] // ИНДЕКС ЧЕЛОВЕЧНОСТИ: 0.00% — ФОРСАЖ ИМПЛАНТОВ АКТИВИРОВАН";
                localT = (progress - 0.33f) / 0.42f;
                lineColor = CyberUiRenderer.CP_YELLOW;
            } else {
                fullLine = "⚠ КИБЕРПСИХОЗ // ПОЛНЫЙ ПЕРЕХВАТ МОТОРИКИ — ДИРЕКТИВА: УНИЧТОЖИТЬ ВСЕХ ⚠";
                localT = (progress - 0.75f) / 0.25f;
                lineColor = CyberUiRenderer.CP_CRIMSON;
            }

            int chars = Math.max(1, Math.min(fullLine.length(), (int) (fullLine.length() * Math.min(1.0f, localT * 1.40f))));
            String cursor = ((now / 110L) % 2L == 0L) ? "_" : "";
            String typed = fullLine.substring(0, chars) + cursor;

            float maxTopW = Math.max(60.0f, screenW - 24.0f);
            float topTextY = Math.max(3.0f, (barH - 8.0f) * 0.5f);

            if (barH >= 24) {
                CyberUiRenderer.drawClampedText(
                        context,
                        textRenderer,
                        "ARASAKA_NEURAL_BIOS v2.077 // RELIC_MALFUNCTION",
                        12.0f,
                        3.5f,
                        0.54f,
                        maxTopW * 0.65f,
                        CyberUiRenderer.CP_CYAN,
                        false
                );
                CyberUiRenderer.drawClampedTextRight(
                        context,
                        textRenderer,
                        "SEC_LVL: CRITICAL",
                        screenW - 12.0f,
                        3.5f,
                        0.54f,
                        maxTopW * 0.30f,
                        CyberUiRenderer.CP_CRIMSON,
                        false
                );
                topTextY = Math.max(12.0f, barH * 0.48f);
            }

            float topScale = Math.min(0.84f, maxTopW / Math.max(1, textRenderer.getWidth(fullLine + "_")));
            float typedW = textRenderer.getWidth(typed) * topScale;
            float topTextX = Math.max(10.0f, (screenW - typedW) * 0.5f);

            // RGB-split cyan echo + primary typed line
            CyberUiRenderer.drawScaledText(context, textRenderer, typed, topTextX + 1.0f, topTextY, topScale, 0x885CEBF5, false);
            CyberUiRenderer.drawScaledText(context, textRenderer, typed, topTextX, topTextY, topScale, lineColor, true);

            // Bottom bar: Synchronization readout + hex telemetry + segmented gauge
            int botY = screenH - barH;
            int padX = 14;
            int contentW = Math.max(40, screenW - padX * 2);
            int pct = Math.min(100, Math.max(0, (int) (progress * 100.0f)));
            String syncLabel = "КИБЕРПСИХОЗ // СИНХРОНИЗАЦИЯ: " + pct + "%";
            String hexDump = String.format("0xDEAD_2077 // OVERRIDE_TRUE [0x%04X]", (int) (((now / 50L) * 73L + playerAge * 131L) & 0xFFFF));

            int labelY = botY + Math.max(4, (barH / 2) - 8);
            CyberUiRenderer.drawClampedText(
                    context,
                    textRenderer,
                    syncLabel,
                    padX,
                    labelY,
                    0.70f,
                    contentW * 0.52f,
                    CyberUiRenderer.CP_YELLOW,
                    true
            );
            CyberUiRenderer.drawClampedTextRight(
                    context,
                    textRenderer,
                    hexDump,
                    screenW - padX,
                    labelY,
                    0.64f,
                    contentW * 0.44f,
                    CyberUiRenderer.CP_CYAN,
                    true
            );

            int syncBarY = Math.min(screenH - 7, labelY + 10);
            int barColor = progress > 0.75f ? CyberUiRenderer.CP_YELLOW : CyberUiRenderer.CP_CRIMSON;
            CyberUiRenderer.drawSegmentedGauge(
                    context,
                    padX,
                    syncBarY,
                    contentW,
                    5,
                    progress,
                    Math.max(12, contentW / 14),
                    barColor,
                    0x38FF4D5A,
                    0x88080306
            );
        }

        // 4. Act III Climax Optical Flash (progress > 0.78f)
        if (progress > 0.78f) {
            float flashT = MathHelper.clamp((progress - 0.78f) / 0.22f, 0.0f, 1.0f);
            int crimsonAlpha = (int) (Math.sin(flashT * Math.PI) * 135.0f);
            context.fill(0, 0, screenW, screenH, (crimsonAlpha << 24) | 0xFF4D5A);

            if (flashT > 0.35f) {
                float cyanT = (flashT - 0.35f) / 0.65f;
                int cyanAlpha = (int) (Math.sin(cyanT * Math.PI) * 125.0f);
                context.fill(0, 0, screenW, screenH, (cyanAlpha << 24) | 0x5CEBF5);
            }

            if (flashT > 0.62f) {
                float whiteT = (flashT - 0.62f) / 0.38f;
                int whiteAlpha = Math.min(215, (int) (whiteT * whiteT * 205.0f));
                context.fill(0, 0, screenW, screenH, (whiteAlpha << 24) | 0xFFFFFF);
            }
        }
    }
}
