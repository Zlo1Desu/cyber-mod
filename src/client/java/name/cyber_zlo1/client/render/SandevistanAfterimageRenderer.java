package name.cyber_zlo1.client.render;

import name.cyber_zlo1.cyberware.CyberwareHolder;
import name.cyber_zlo1.cyberware.CyberwareState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class SandevistanAfterimageRenderer {
    private static final int MAX_SNAPSHOTS = 28;
    private static final int MAX_AGE = 22;
    private static final int SHOCKWAVE_MAX_TICKS = 20;
    private static final int MAX_DATA_PARTICLES = 36;

    private static final int[] EDGERUNNERS_PALETTE = {
            0x00FFA3, // Neon Emerald
            0x00F0FF, // Electric Cyan
            0xFCEE09, // Cyber Yellow
            0xFF0055, // Neon Magenta
            0x7B2BFF  // Cyber Violet
    };

    private static final Deque<AfterimageSnapshot> SNAPSHOTS = new ArrayDeque<>();
    private static final Map<UUID, Vec3d> LAST_POSITIONS = new HashMap<>();
    private static final List<ShockwaveOrigin> SHOCKWAVES = new ArrayList<>();
    private static final List<DataParticle> DATA_PARTICLES = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private static int paletteIndex = 0;
    private static boolean wasLocalActive = false;

    private SandevistanAfterimageRenderer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SandevistanAfterimageRenderer::onEndClientTick);
        WorldRenderEvents.AFTER_ENTITIES.register(SandevistanAfterimageRenderer::onAfterEntities);
    }

    private static void onEndClientTick(MinecraftClient client) {
        if (client.world == null) {
            SNAPSHOTS.clear();
            LAST_POSITIONS.clear();
            SHOCKWAVES.clear();
            DATA_PARTICLES.clear();
            wasLocalActive = false;
            return;
        }
        if (client.isPaused()) {
            return;
        }

        Iterator<AfterimageSnapshot> it = SNAPSHOTS.iterator();
        while (it.hasNext()) {
            AfterimageSnapshot snapshot = it.next();
            snapshot.age++;
            if (snapshot.age >= MAX_AGE) {
                it.remove();
            }
        }

        Iterator<ShockwaveOrigin> waveIt = SHOCKWAVES.iterator();
        while (waveIt.hasNext()) {
            ShockwaveOrigin wave = waveIt.next();
            wave.age++;
            if (wave.age >= SHOCKWAVE_MAX_TICKS) {
                waveIt.remove();
            }
        }

        boolean localActiveNow = client.player != null && CyberwareState.isActive();
        if (localActiveNow && !wasLocalActive && client.player != null) {
            SHOCKWAVES.add(new ShockwaveOrigin(client.player.getX(), client.player.getY(), client.player.getZ()));
            spawnDataParticlesAround(client.player);
        }
        wasLocalActive = localActiveNow;

        if (localActiveNow && client.player != null) {
            Iterator<DataParticle> pIt = DATA_PARTICLES.iterator();
            while (pIt.hasNext()) {
                DataParticle p = pIt.next();
                p.age++;
                if (p.age >= p.maxAge || client.player.squaredDistanceTo(p.x, p.y, p.z) > 144.0) {
                    pIt.remove();
                }
            }
            while (DATA_PARTICLES.size() < MAX_DATA_PARTICLES) {
                spawnSingleDataParticle(client.player);
            }
        } else {
            DATA_PARTICLES.clear();
        }

        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            boolean isLocalActive = (player == client.player) && CyberwareState.isActive();
            boolean isHolderActive = ((CyberwareHolder) player).cyber$getSandevistanActiveTicks() > 0;
            if (!isLocalActive && !isHolderActive) {
                continue;
            }

            UUID uuid = player.getUuid();
            Vec3d lastPos = LAST_POSITIONS.get(uuid);
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();

            boolean moved = lastPos == null || player.squaredDistanceTo(lastPos.x, lastPos.y, lastPos.z) > 0.004;
            if (moved || player.handSwinging || player.age % 2 == 0) {
                LAST_POSITIONS.put(uuid, new Vec3d(x, y, z));
                int colorRgb = EDGERUNNERS_PALETTE[paletteIndex % EDGERUNNERS_PALETTE.length];
                paletteIndex++;

                SNAPSHOTS.addLast(new AfterimageSnapshot(
                        uuid,
                        x, y, z,
                        player.bodyYaw,
                        player.headYaw,
                        player.getPitch(),
                        player.limbAnimator.getPos(),
                        player.limbAnimator.getSpeed(),
                        player.getHandSwingProgress(1.0f),
                        player.isSneaking(),
                        colorRgb
                ));

                while (SNAPSHOTS.size() > MAX_SNAPSHOTS) {
                    SNAPSHOTS.removeFirst();
                }
            }
        }
    }

    private static void spawnDataParticlesAround(PlayerEntity player) {
        DATA_PARTICLES.clear();
        for (int i = 0; i < MAX_DATA_PARTICLES; i++) {
            spawnSingleDataParticle(player);
        }
    }

    private static void spawnSingleDataParticle(PlayerEntity player) {
        double angle = RANDOM.nextDouble() * Math.PI * 2.0;
        double dist = 1.5 + RANDOM.nextDouble() * 6.5;
        double px = player.getX() + Math.cos(angle) * dist;
        double py = player.getY() + 0.3 + RANDOM.nextDouble() * 3.0;
        double pz = player.getZ() + Math.sin(angle) * dist;
        int color = EDGERUNNERS_PALETTE[RANDOM.nextInt(EDGERUNNERS_PALETTE.length)];
        float size = 0.035f + RANDOM.nextFloat() * 0.045f;
        DATA_PARTICLES.add(new DataParticle(px, py, pz, color, size, 30 + RANDOM.nextInt(30)));
    }

    private static void onAfterEntities(WorldRenderContext context) {
        if (SNAPSHOTS.isEmpty() && SHOCKWAVES.isEmpty() && DATA_PARTICLES.isEmpty()) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        boolean firstPerson = client != null && client.options.getPerspective().isFirstPerson();
        PlayerEntity localPlayer = client != null ? client.player : null;

        Vec3d camPos = context.camera().getPos();
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getDebugFilledBox());

        // 1. Expanding 3D neon ground shockwave rings
        for (ShockwaveOrigin wave : SHOCKWAVES) {
            float progress = wave.age / (float) SHOCKWAVE_MAX_TICKS;
            float radius = 0.6f + progress * 11.4f;
            float alpha = (1.0f - progress) * 0.72f;
            renderShockwaveRing(matrices, buffer, camPos, wave.x, wave.y + 0.08, wave.z, radius, 0.0f, 1.0f, 0.64f, alpha);
            if (radius > 1.2f) {
                renderShockwaveRing(matrices, buffer, camPos, wave.x, wave.y + 0.12, wave.z, radius * 0.85f, 0.0f, 0.94f, 1.0f, alpha * 0.75f);
            }
        }

        // 2. Suspended neon data-particles/cubes frozen in air
        for (DataParticle p : DATA_PARTICLES) {
            float life = 1.0f - (p.age / (float) p.maxAge);
            float alpha = 0.75f * Math.min(1.0f, life * 2.0f);
            float r = ((p.colorRgb >> 16) & 0xFF) / 255.0f;
            float g = ((p.colorRgb >> 8) & 0xFF) / 255.0f;
            float b = (p.colorRgb & 0xFF) / 255.0f;

            matrices.push();
            matrices.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
            float s = p.size;
            WorldRenderer.renderFilledBox(matrices, buffer, -s, -s, -s, s, s, s, r, g, b, alpha);
            matrices.pop();
        }

        // 3. Multi-layered 3D afterimage clones + connecting neon light-ribbons + lightning arcs + ground ripples
        long animTime = System.currentTimeMillis() / 65L;
        Map<UUID, AfterimageSnapshot> prevByPlayer = new HashMap<>();
        int snapshotIdx = 0;
        for (AfterimageSnapshot s : SNAPSHOTS) {
            AfterimageSnapshot prev = prevByPlayer.put(s.playerUuid, s);
            snapshotIdx++;

            float life = 1.0f - (s.age / (float) MAX_AGE);
            if (life <= 0.0f) {
                continue;
            }

            float r = ((s.colorRgb >> 16) & 0xFF) / 255.0f;
            float g = ((s.colorRgb >> 8) & 0xFF) / 255.0f;
            float b = (s.colorRgb & 0xFF) / 255.0f;
            float alpha = 0.44f * life;

            // Ground footstep neon ripple (expanding flat cyan/yellow diamond ring)
            if (snapshotIdx % 2 == 0) {
                float rippleProgress = 1.0f - life;
                float rippleRadius = 0.25f + rippleProgress * 0.85f;
                float rippleAlpha = life * 0.55f;
                boolean yellowRipple = (snapshotIdx % 4 == 0);
                float rr = yellowRipple ? 0.99f : 0.0f;
                float rg = yellowRipple ? 0.93f : 0.94f;
                float rb = yellowRipple ? 0.04f : 1.0f;
                renderFootstepRipple(matrices, buffer, camPos, s.x, s.y + 0.03, s.z, rippleRadius, rr, rg, rb, rippleAlpha);
            }

            if (prev != null) {
                renderLightRibbon(matrices, buffer, camPos, prev, s, r, g, b, alpha * 0.85f);
                // Crackling 3D electric neural lightning arc between consecutive clones
                if (snapshotIdx % 2 == 0) {
                    long arcSeed = animTime * 31L + snapshotIdx * 17L;
                    boolean magentaArc = (snapshotIdx % 6 == 0);
                    float ar = magentaArc ? 1.0f : (snapshotIdx % 4 == 0 ? 0.99f : 0.0f);
                    float ag = magentaArc ? 0.0f : 0.94f;
                    float ab = magentaArc ? 0.33f : (snapshotIdx % 4 == 0 ? 0.04f : 1.0f);
                    renderLightningArc(matrices, buffer, camPos,
                            prev.x, prev.y + 1.15, prev.z,
                            s.x, s.y + 1.15, s.z,
                            ar, ag, ab, Math.min(0.90f, life * 0.85f), 0.16, arcSeed);
                }
            }

            if (firstPerson && localPlayer != null && s.playerUuid.equals(localPlayer.getUuid())) {
                double dx = s.x - camPos.x;
                double dy = (s.y + 1.4) - camPos.y;
                double dz = s.z - camPos.z;
                if (dx * dx + dy * dy + dz * dz < 0.55 * 0.55) {
                    continue;
                }
            }

            float auraAlpha = alpha * 0.45f;
            float coreAlpha = 0.84f * life;

            matrices.push();
            matrices.translate(s.x - camPos.x, s.y - camPos.y, s.z - camPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - s.bodyYaw));
            if (s.sneaking) {
                matrices.translate(0.0, -0.15, 0.0);
            }

            // Outer translucent aura shell (+0.03 expansion)
            WorldRenderer.renderFilledBox(matrices, buffer, -0.28, 0.72, -0.155, 0.28, 1.53, 0.155, r, g, b, auraAlpha);

            // Main Torso
            WorldRenderer.renderFilledBox(matrices, buffer, -0.25, 0.75, -0.125, 0.25, 1.50, 0.125, r, g, b, alpha);

            // 3D Cyber Ribcage plates across torso
            for (int rib = 0; rib < 3; rib++) {
                double ry1 = 1.00 + rib * 0.13;
                double ry2 = ry1 + 0.045;
                WorldRenderer.renderFilledBox(matrices, buffer, -0.265, ry1, -0.145, -0.05, ry2, 0.10, 0.0f, 0.96f, 1.0f, coreAlpha * 0.75f);
                WorldRenderer.renderFilledBox(matrices, buffer, 0.05, ry1, -0.145, 0.265, ry2, 0.10, 0.0f, 0.96f, 1.0f, coreAlpha * 0.75f);
            }

            // 3D Shoulder Hologram Plates
            WorldRenderer.renderFilledBox(matrices, buffer, -0.45, 1.42, -0.14, -0.25, 1.52, 0.14, r, g, b, alpha * 1.1f);
            WorldRenderer.renderFilledBox(matrices, buffer, 0.25, 1.42, -0.14, 0.45, 1.52, 0.14, r, g, b, alpha * 1.1f);
            WorldRenderer.renderFilledBox(matrices, buffer, -0.46, 1.49, -0.15, -0.25, 1.53, 0.15, 0.0f, 1.0f, 0.64f, coreAlpha * 0.85f);
            WorldRenderer.renderFilledBox(matrices, buffer, 0.25, 1.49, -0.15, 0.46, 1.53, 0.15, 0.0f, 1.0f, 0.64f, coreAlpha * 0.85f);

            // 6 segmented glowing Sandevistan spine vertebrae down the back
            for (int v = 0; v < 6; v++) {
                double vy1 = 0.82 + v * 0.11;
                double vy2 = vy1 + 0.075;
                float coreR = (v % 2 == 0) ? 0.0f : 0.99f;
                float coreG = 0.95f;
                float coreB = (v % 2 == 0) ? 1.0f : 0.04f;
                WorldRenderer.renderFilledBox(matrices, buffer, -0.065, vy1, 0.09, 0.065, vy2, 0.175, coreR, coreG, coreB, coreAlpha);
            }

            // Head + outer aura + glowing visor bar across the eyes
            matrices.push();
            matrices.translate(0.0, 1.50, 0.0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(s.bodyYaw - s.headYaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(s.pitch));
            WorldRenderer.renderFilledBox(matrices, buffer, -0.25, -0.03, -0.25, 0.25, 0.47, 0.25, r, g, b, auraAlpha);
            WorldRenderer.renderFilledBox(matrices, buffer, -0.22, 0.0, -0.22, 0.22, 0.44, 0.22, r, g, b, alpha);
            // Glowing cybernetic visor bar across front of head (-Z is forward in this rotated frame)
            WorldRenderer.renderFilledBox(matrices, buffer, -0.20, 0.21, -0.24, 0.20, 0.29, -0.18, 0.0f, 0.98f, 1.0f, coreAlpha);
            matrices.pop();

            // Legs & Arms (with outer aura)
            renderLeg(matrices, buffer, true, s.limbPos, s.limbSpeed, r, g, b, alpha, auraAlpha);
            renderLeg(matrices, buffer, false, s.limbPos, s.limbSpeed, r, g, b, alpha, auraAlpha);
            renderArm(matrices, buffer, true, s.limbPos, s.limbSpeed, s.handSwing, r, g, b, alpha, auraAlpha);
            renderArm(matrices, buffer, false, s.limbPos, s.limbSpeed, s.handSwing, r, g, b, alpha, auraAlpha);

            matrices.pop();
        }

        // 4. Crackling neural lightning arcs around the active player's shoulders & spine
        if (localPlayer != null && CyberwareState.isActive() && !firstPerson) {
            double px = localPlayer.getX();
            double py = localPlayer.getY();
            double pz = localPlayer.getZ();
            renderLightningArc(matrices, buffer, camPos,
                    px - 0.36, py + 1.42, pz,
                    px + 0.36, py + 1.42, pz,
                    0.0f, 0.94f, 1.0f, 0.85f, 0.14, animTime * 73L);
            renderLightningArc(matrices, buffer, camPos,
                    px, py + 0.85, pz,
                    px, py + 1.50, pz,
                    0.99f, 0.93f, 0.04f, 0.85f, 0.12, animTime * 137L);
        }
    }

    private static void renderShockwaveRing(MatrixStack matrices, VertexConsumer buffer, Vec3d camPos,
                                            double cx, double cy, double cz, float radius,
                                            float r, float g, float b, float alpha) {
        int segments = 16;
        double segHalfLen = radius * Math.sin(Math.PI / segments) * 1.05;
        for (int i = 0; i < segments; i++) {
            double theta = (i + 0.5) * (Math.PI * 2.0 / segments);
            matrices.push();
            matrices.translate(cx - camPos.x, cy - camPos.y, cz - camPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) -theta));
            matrices.translate(radius, 0.0, 0.0);
            WorldRenderer.renderFilledBox(matrices, buffer, -0.07, 0.0, -segHalfLen, 0.07, 0.16, segHalfLen, r, g, b, alpha);
            matrices.pop();
        }
    }

    private static void renderLightRibbon(MatrixStack matrices, VertexConsumer buffer, Vec3d camPos,
                                          AfterimageSnapshot prev, AfterimageSnapshot curr,
                                          float r, float g, float b, float alpha) {
        double x1 = prev.x;
        double y1 = prev.y + 1.15;
        double z1 = prev.z;
        double dx = curr.x - x1;
        double dy = (curr.y + 1.15) - y1;
        double dz = curr.z - z1;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq < 0.0004 || distSq > 16.0) {
            return;
        }
        double len = Math.sqrt(distSq);
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.atan2(dx, dz);
        float pitch = (float) -Math.atan2(dy, horiz);

        matrices.push();
        matrices.translate(x1 - camPos.x, y1 - camPos.y, z1 - camPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(pitch));
        WorldRenderer.renderFilledBox(matrices, buffer, -0.025, -0.025, 0.0, 0.025, 0.025, len, r, g, b, alpha);
        matrices.pop();
    }

    private static void renderLightningArc(MatrixStack matrices, VertexConsumer buffer, Vec3d camPos,
                                           double x1, double y1, double z1,
                                           double x2, double y2, double z2,
                                           float r, float g, float b, float alpha,
                                           double jitterAmp, long seed) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double totalDistSq = dx * dx + dy * dy + dz * dz;
        if (totalDistSq < 0.001 || totalDistSq > 25.0) {
            return;
        }

        int segments = 4;
        double prevX = x1;
        double prevY = y1;
        double prevZ = z1;
        long state = seed;

        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            double nextX = x1 + dx * t;
            double nextY = y1 + dy * t;
            double nextZ = z1 + dz * t;
            if (i < segments) {
                state = state * 6364136223846793005L + 1442695040888963407L;
                double jx = (((state >>> 16) & 0xFF) / 255.0 - 0.5) * 2.0 * jitterAmp;
                state = state * 6364136223846793005L + 1442695040888963407L;
                double jy = (((state >>> 16) & 0xFF) / 255.0 - 0.5) * 2.0 * jitterAmp;
                state = state * 6364136223846793005L + 1442695040888963407L;
                double jz = (((state >>> 16) & 0xFF) / 255.0 - 0.5) * 2.0 * jitterAmp;
                nextX += jx;
                nextY += jy;
                nextZ += jz;
            }

            double sdx = nextX - prevX;
            double sdy = nextY - prevY;
            double sdz = nextZ - prevZ;
            double segLen = Math.sqrt(sdx * sdx + sdy * sdy + sdz * sdz);
            if (segLen > 0.001) {
                double horiz = Math.sqrt(sdx * sdx + sdz * sdz);
                float yaw = (float) Math.atan2(sdx, sdz);
                float pitch = (float) -Math.atan2(sdy, horiz);

                matrices.push();
                matrices.translate(prevX - camPos.x, prevY - camPos.y, prevZ - camPos.z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotation(yaw));
                matrices.multiply(RotationAxis.POSITIVE_X.rotation(pitch));
                WorldRenderer.renderFilledBox(matrices, buffer, -0.014, -0.014, 0.0, 0.014, 0.014, segLen, r, g, b, alpha);
                matrices.pop();
            }

            prevX = nextX;
            prevY = nextY;
            prevZ = nextZ;
        }
    }

    private static void renderFootstepRipple(MatrixStack matrices, VertexConsumer buffer, Vec3d camPos,
                                             double cx, double cy, double cz, float radius,
                                             float r, float g, float b, float alpha) {
        double edgeHalfLen = radius * 0.72;
        double offset = radius * 0.707;
        for (int i = 0; i < 4; i++) {
            float angleDeg = 45.0f + i * 90.0f;
            matrices.push();
            matrices.translate(cx - camPos.x, cy - camPos.y, cz - camPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angleDeg));
            matrices.translate(offset, 0.0, 0.0);
            WorldRenderer.renderFilledBox(matrices, buffer, -0.02, 0.0, -edgeHalfLen, 0.02, 0.025, edgeHalfLen, r, g, b, alpha);
            matrices.pop();
        }
    }

    private static void renderLeg(MatrixStack matrices, VertexConsumer buffer, boolean isLeft,
                                  float limbPos, float limbSpeed, float r, float g, float b, float alpha, float auraAlpha) {
        matrices.push();
        matrices.translate(isLeft ? 0.12 : -0.12, 0.75, 0.0);
        float angle = MathHelper.cos(limbPos * 0.6662f + (isLeft ? (float) Math.PI : 0.0f)) * 1.2f * limbSpeed;
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(angle));
        WorldRenderer.renderFilledBox(matrices, buffer, -0.14, -0.78, -0.14, 0.14, 0.03, 0.14, r, g, b, auraAlpha);
        WorldRenderer.renderFilledBox(matrices, buffer, -0.11, -0.75, -0.11, 0.11, 0.0, 0.11, r, g, b, alpha);
        matrices.pop();
    }

    private static void renderArm(MatrixStack matrices, VertexConsumer buffer, boolean isLeft,
                                  float limbPos, float limbSpeed, float handSwing, float r, float g, float b, float alpha, float auraAlpha) {
        matrices.push();
        matrices.translate(isLeft ? 0.36 : -0.36, 1.38, 0.0);
        float angle = MathHelper.cos(limbPos * 0.6662f + (isLeft ? 0.0f : (float) Math.PI)) * 1.1f * limbSpeed
                - (!isLeft ? handSwing * 1.4f : 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(angle));
        WorldRenderer.renderFilledBox(matrices, buffer, -0.14, -0.68, -0.14, 0.14, 0.13, 0.14, r, g, b, auraAlpha);
        WorldRenderer.renderFilledBox(matrices, buffer, -0.11, -0.65, -0.11, 0.11, 0.10, 0.11, r, g, b, alpha);
        matrices.pop();
    }

    private static final class ShockwaveOrigin {
        final double x;
        final double y;
        final double z;
        int age;

        ShockwaveOrigin(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.age = 0;
        }
    }

    private static final class DataParticle {
        final double x;
        final double y;
        final double z;
        final int colorRgb;
        final float size;
        final int maxAge;
        int age;

        DataParticle(double x, double y, double z, int colorRgb, float size, int maxAge) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.colorRgb = colorRgb;
            this.size = size;
            this.maxAge = maxAge;
            this.age = 0;
        }
    }

    private static final class AfterimageSnapshot {
        final UUID playerUuid;
        final double x;
        final double y;
        final double z;
        final float bodyYaw;
        final float headYaw;
        final float pitch;
        final float limbPos;
        final float limbSpeed;
        final float handSwing;
        final boolean sneaking;
        final int colorRgb;
        int age;

        AfterimageSnapshot(UUID playerUuid, double x, double y, double z,
                           float bodyYaw, float headYaw, float pitch,
                           float limbPos, float limbSpeed, float handSwing,
                           boolean sneaking, int colorRgb) {
            this.playerUuid = playerUuid;
            this.x = x;
            this.y = y;
            this.z = z;
            this.bodyYaw = bodyYaw;
            this.headYaw = headYaw;
            this.pitch = pitch;
            this.limbPos = limbPos;
            this.limbSpeed = limbSpeed;
            this.handSwing = handSwing;
            this.sneaking = sneaking;
            this.colorRgb = colorRgb;
            this.age = 0;
        }
    }
}
