package name.cyber_zlo1.client.render;

import name.cyber_zlo1.cyberware.CyberwareHolder;
import name.cyber_zlo1.cyberware.CyberwareState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.EntityModelLoader;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
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
import java.util.UUID;

public final class SandevistanAfterimageRenderer {
    private static final int MAX_SNAPSHOTS = 24;
    private static final int MAX_AGE = 20;
    private static final int SHOCKWAVE_MAX_TICKS = 18;

    // Authentic Cyberpunk 2077 / Edgerunners Sandevistan holographic trail palette
    private static final int[] EDGERUNNERS_PALETTE = {
            0x00FF9D, // Emerald
            0x00E5FF, // Ice Cyan
            0xFCEE09, // Cyber Yellow
            0xFF3B5C, // Arasaka Coral
            0x7A2EFF  // Deep Violet
    };

    private static final Deque<AfterimageSnapshot> SNAPSHOTS = new ArrayDeque<>();
    private static final Map<UUID, Vec3d> LAST_POSITIONS = new HashMap<>();
    private static final List<ShockwaveOrigin> SHOCKWAVES = new ArrayList<>();

    private static PlayerEntityModel<AbstractClientPlayerEntity> wideModel;
    private static PlayerEntityModel<AbstractClientPlayerEntity> slimModel;

    private static int paletteIndex = 0;
    private static boolean wasLocalActive = false;

    private SandevistanAfterimageRenderer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SandevistanAfterimageRenderer::onEndClientTick);
        WorldRenderEvents.AFTER_ENTITIES.register(SandevistanAfterimageRenderer::onAfterEntities);
    }

    private static void ensureModels(MinecraftClient client) {
        if (wideModel != null && slimModel != null) {
            return;
        }
        try {
            EntityModelLoader loader = client.getEntityModelLoader();
            wideModel = new PlayerEntityModel<>(loader.getModelPart(EntityModelLayers.PLAYER), false);
            slimModel = new PlayerEntityModel<>(loader.getModelPart(EntityModelLayers.PLAYER_SLIM), true);
        } catch (Throwable ignored) {
            wideModel = null;
            slimModel = null;
        }
    }

    private static void onEndClientTick(MinecraftClient client) {
        if (client.world == null) {
            SNAPSHOTS.clear();
            LAST_POSITIONS.clear();
            SHOCKWAVES.clear();
            wasLocalActive = false;
            return;
        }
        if (client.isPaused()) {
            return;
        }

        Iterator<AfterimageSnapshot> it = SNAPSHOTS.iterator();
        while (it.hasNext()) {
            AfterimageSnapshot snapshot = it.next();
            snapshot.prevX = snapshot.x;
            snapshot.prevY = snapshot.y;
            snapshot.prevZ = snapshot.z;
            snapshot.x += snapshot.vx;
            snapshot.y += snapshot.vy;
            snapshot.z += snapshot.vz;
            snapshot.vx *= 0.84;
            snapshot.vy *= 0.84;
            snapshot.vz *= 0.84;
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
            SHOCKWAVES.add(new ShockwaveOrigin(client.player.getX(), client.player.getY(), client.player.getZ(), 1.0f));
        }
        wasLocalActive = localActiveNow;

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

            boolean moved = lastPos == null || player.squaredDistanceTo(lastPos.x, lastPos.y, lastPos.z) > 0.006;
            if (moved || player.handSwinging || player.age % 2 == 0) {
                LAST_POSITIONS.put(uuid, new Vec3d(x, y, z));
                int colorRgb = EDGERUNNERS_PALETTE[paletteIndex % EDGERUNNERS_PALETTE.length];
                paletteIndex++;

                SkinTextures skinTextures = player.getSkinTextures();
                boolean slim = skinTextures.model() == SkinTextures.Model.SLIM;
                Identifier skinTexture = skinTextures.texture();

                Vec3d vel = player.getVelocity();
                SNAPSHOTS.addLast(new AfterimageSnapshot(
                        uuid,
                        player.prevX, player.prevY, player.prevZ,
                        x, y, z,
                        vel.x * 0.04, vel.y * 0.02, vel.z * 0.04,
                        player.prevBodyYaw, player.bodyYaw,
                        player.prevHeadYaw, player.headYaw,
                        player.prevPitch, player.getPitch(),
                        player.limbAnimator.getPos(),
                        player.limbAnimator.getSpeed(),
                        player.getHandSwingProgress(1.0f),
                        player.isSneaking(),
                        slim,
                        skinTexture,
                        colorRgb
                ));

                while (SNAPSHOTS.size() > MAX_SNAPSHOTS) {
                    SNAPSHOTS.removeFirst();
                }
            }
        }
    }

    private static void onAfterEntities(WorldRenderContext context) {
        if (SNAPSHOTS.isEmpty() && SHOCKWAVES.isEmpty()) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }

        boolean firstPerson = client.options.getPerspective().isFirstPerson();
        AbstractClientPlayerEntity localPlayer = client.player;
        float tickDelta = context.tickCounter().getTickDelta(true);
        Vec3d camPos = context.camera().getPos();

        // Pass 1: Sleek 3D ground shockwave rings & subtle neural spine trail ribbons
        VertexConsumer fxBuffer = consumers.getBuffer(RenderLayer.getDebugFilledBox());

        for (ShockwaveOrigin wave : SHOCKWAVES) {
            float progress = MathHelper.clamp((wave.age + tickDelta) / (float) SHOCKWAVE_MAX_TICKS, 0.0f, 1.0f);
            float easeOut = 1.0f - (1.0f - progress) * (1.0f - progress);
            float radius = (0.55f + easeOut * 9.5f) * wave.scale;
            float alpha = (1.0f - progress) * 0.65f * wave.scale;
            renderShockwaveRing(matrices, fxBuffer, camPos, wave.x, wave.y + 0.05, wave.z, radius, 0.0f, 1.0f, 0.62f, alpha);
            if (radius > 1.1f) {
                renderShockwaveRing(matrices, fxBuffer, camPos, wave.x, wave.y + 0.08, wave.z, radius * 0.84f, 0.0f, 0.90f, 1.0f, alpha * 0.58f);
            }
        }

        Map<UUID, double[]> prevPosByPlayer = new HashMap<>();
        for (AfterimageSnapshot s : SNAPSHOTS) {
            float exactAge = s.age + tickDelta;
            float life = 1.0f - (exactAge / (float) MAX_AGE);
            if (life <= 0.0f) {
                continue;
            }

            double ix = MathHelper.lerp(tickDelta, s.prevX, s.x);
            double iy = MathHelper.lerp(tickDelta, s.prevY, s.y);
            double iz = MathHelper.lerp(tickDelta, s.prevZ, s.z);
            double[] prevPos = prevPosByPlayer.put(s.playerUuid, new double[]{ix, iy, iz});

            if (prevPos != null) {
                float r = ((s.colorRgb >> 16) & 0xFF) / 255.0f;
                float g = ((s.colorRgb >> 8) & 0xFF) / 255.0f;
                float b = (s.colorRgb & 0xFF) / 255.0f;
                float ribbonAlpha = 0.28f * life * life;
                renderSpineRibbon(matrices, fxBuffer, camPos, prevPos[0], prevPos[1], prevPos[2], ix, iy, iz, r, g, b, ribbonAlpha);
            }
        }

        if (localPlayer != null && CyberwareState.isActive()) {
            double[] latestPos = prevPosByPlayer.get(localPlayer.getUuid());
            if (latestPos != null) {
                double lpx = MathHelper.lerp(tickDelta, localPlayer.prevX, localPlayer.getX());
                double lpy = MathHelper.lerp(tickDelta, localPlayer.prevY, localPlayer.getY());
                double lpz = MathHelper.lerp(tickDelta, localPlayer.prevZ, localPlayer.getZ());
                renderSpineRibbon(matrices, fxBuffer, camPos, latestPos[0], latestPos[1], latestPos[2], lpx, lpy, lpz, 0.0f, 1.0f, 0.62f, 0.30f);
            }
        }

        // Pass 2: True PlayerEntityModel holographic afterimage silhouettes (wide & slim skin accurate!)
        ensureModels(client);
        if (wideModel == null || slimModel == null || localPlayer == null) {
            return;
        }

        for (AfterimageSnapshot s : SNAPSHOTS) {
            if (s.skinTexture == null) {
                continue;
            }
            float exactAge = s.age + tickDelta;
            float life = 1.0f - (exactAge / (float) MAX_AGE);
            if (life <= 0.01f) {
                continue;
            }

            double ix = MathHelper.lerp(tickDelta, s.prevX, s.x);
            double iy = MathHelper.lerp(tickDelta, s.prevY, s.y);
            double iz = MathHelper.lerp(tickDelta, s.prevZ, s.z);

            if (firstPerson && s.playerUuid.equals(localPlayer.getUuid())) {
                double dx = ix - camPos.x;
                double dy = (iy + 1.45) - camPos.y;
                double dz = iz - camPos.z;
                if (dx * dx + dy * dy + dz * dz < 0.68 * 0.68) {
                    continue;
                }
            }

            float iBodyYaw = MathHelper.lerpAngleDegrees(tickDelta, s.prevBodyYaw, s.bodyYaw);
            float iHeadYaw = MathHelper.lerpAngleDegrees(tickDelta, s.prevHeadYaw, s.headYaw);
            float iPitch = MathHelper.lerp(tickDelta, s.prevPitch, s.pitch);

            PlayerEntity targetEntity = client.world.getPlayerByUuid(s.playerUuid);
            AbstractClientPlayerEntity poseEntity = (targetEntity instanceof AbstractClientPlayerEntity acpe)
                    ? acpe
                    : localPlayer;

            PlayerEntityModel<AbstractClientPlayerEntity> model = s.slim ? slimModel : wideModel;
            model.child = false;
            model.riding = false;
            model.sneaking = s.sneaking;
            model.handSwingProgress = s.handSwing;
            model.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
            model.rightArmPose = BipedEntityModel.ArmPose.EMPTY;
            model.setVisible(true);

            float relativeHeadYaw = MathHelper.wrapDegrees(iHeadYaw - iBodyYaw);
            model.setAngles(poseEntity, s.limbPos, s.limbSpeed, 0.0f, relativeHeadYaw, iPitch);

            float alphaF = MathHelper.clamp(0.54f * life * life + 0.14f * life, 0.0f, 0.68f);
            int alphaByte = MathHelper.clamp((int) (alphaF * 255.0f), 0, 255);
            int redByte = (s.colorRgb >> 16) & 0xFF;
            int greenByte = (s.colorRgb >> 8) & 0xFF;
            int blueByte = s.colorRgb & 0xFF;
            int argbColor = ColorHelper.Argb.getArgb(alphaByte, redByte, greenByte, blueByte);

            matrices.push();
            double sneakOffsetY = s.sneaking ? -0.125 : 0.0;
            matrices.translate(ix - camPos.x, iy + sneakOffsetY - camPos.y, iz - camPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - iBodyYaw));
            matrices.scale(-0.9375f, -0.9375f, 0.9375f);
            matrices.translate(0.0, -1.501, 0.0);

            VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(s.skinTexture));
            model.render(matrices, vc, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, argbColor);
            matrices.pop();
        }
    }

    private static void renderShockwaveRing(MatrixStack matrices, VertexConsumer buffer, Vec3d camPos,
                                            double cx, double cy, double cz, float radius,
                                            float r, float g, float b, float alpha) {
        int segments = 24;
        double segHalfLen = radius * Math.sin(Math.PI / segments) * 1.04;
        for (int i = 0; i < segments; i++) {
            double theta = (i + 0.5) * (Math.PI * 2.0 / segments);
            matrices.push();
            matrices.translate(cx - camPos.x, cy - camPos.y, cz - camPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) -theta));
            matrices.translate(radius, 0.0, 0.0);
            WorldRenderer.renderFilledBox(matrices, buffer, -0.032, 0.0, -segHalfLen, 0.032, 0.055, segHalfLen, r, g, b, alpha);
            matrices.pop();
        }
    }

    private static void renderSpineRibbon(MatrixStack matrices, VertexConsumer buffer, Vec3d camPos,
                                          double px, double py, double pz,
                                          double cx, double cy, double cz,
                                          float r, float g, float b, float alpha) {
        double x1 = px;
        double y1 = py + 1.18;
        double z1 = pz;
        double dx = cx - x1;
        double dy = (cy + 1.18) - y1;
        double dz = cz - z1;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq < 0.0009 || distSq > 16.0) {
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
        WorldRenderer.renderFilledBox(matrices, buffer, -0.012, -0.012, 0.0, 0.012, 0.012, len, r, g, b, alpha);
        matrices.pop();
    }

    private static final class ShockwaveOrigin {
        final double x;
        final double y;
        final double z;
        final float scale;
        int age;

        ShockwaveOrigin(double x, double y, double z, float scale) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.scale = scale;
            this.age = 0;
        }
    }

    private static final class AfterimageSnapshot {
        final UUID playerUuid;
        double prevX;
        double prevY;
        double prevZ;
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        final float prevBodyYaw;
        final float bodyYaw;
        final float prevHeadYaw;
        final float headYaw;
        final float prevPitch;
        final float pitch;
        final float limbPos;
        final float limbSpeed;
        final float handSwing;
        final boolean sneaking;
        final boolean slim;
        final Identifier skinTexture;
        final int colorRgb;
        int age;

        AfterimageSnapshot(UUID playerUuid,
                           double prevX, double prevY, double prevZ,
                           double x, double y, double z,
                           double vx, double vy, double vz,
                           float prevBodyYaw, float bodyYaw,
                           float prevHeadYaw, float headYaw,
                           float prevPitch, float pitch,
                           float limbPos, float limbSpeed, float handSwing,
                           boolean sneaking, boolean slim, Identifier skinTexture,
                           int colorRgb) {
            this.playerUuid = playerUuid;
            this.prevX = prevX;
            this.prevY = prevY;
            this.prevZ = prevZ;
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.prevBodyYaw = prevBodyYaw;
            this.bodyYaw = bodyYaw;
            this.prevHeadYaw = prevHeadYaw;
            this.headYaw = headYaw;
            this.prevPitch = prevPitch;
            this.pitch = pitch;
            this.limbPos = limbPos;
            this.limbSpeed = limbSpeed;
            this.handSwing = handSwing;
            this.sneaking = sneaking;
            this.slim = slim;
            this.skinTexture = skinTexture;
            this.colorRgb = colorRgb;
            this.age = 0;
        }
    }
}
