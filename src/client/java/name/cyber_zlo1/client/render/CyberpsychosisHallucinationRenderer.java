package name.cyber_zlo1.client.render;

import name.cyber_zlo1.client.CyberClient;
import name.cyber_zlo1.cyberware.CyberwareState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class CyberpsychosisHallucinationRenderer {
    private static final List<PhantomMob> PHANTOMS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static final DustParticleEffect CRIMSON_DUST = new DustParticleEffect(new Vector3f(1.0f, 0.0f, 0.24f), 1.4f);

    private static int nextId = 1;
    private static int spawnCooldown = 0;

    private CyberpsychosisHallucinationRenderer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CyberpsychosisHallucinationRenderer::onEndClientTick);
        WorldRenderEvents.AFTER_ENTITIES.register(CyberpsychosisHallucinationRenderer::onAfterEntities);
    }

    public static Vec3d getNearestPhantomPos(PlayerEntity player) {
        if (player == null || PHANTOMS.isEmpty()) {
            return null;
        }
        Vec3d playerPos = player.getPos();
        Vec3d best = null;
        double bestDistSq = 22.0 * 22.0;
        for (PhantomMob phantom : PHANTOMS) {
            double distSq = phantom.pos.squaredDistanceTo(playerPos);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = phantom.pos.add(0.0, Math.max(0.9, phantom.dummy.getHeight() * 0.65), 0.0);
            }
        }
        return best;
    }

    public static void spawnCutsceneCirclePhantoms(MinecraftClient client) {
        if (client == null || client.world == null || client.player == null) {
            return;
        }
        PHANTOMS.clear();
        int refY = client.player.getBlockY();
        for (int i = 0; i < 5; i++) {
            double baseAngle = (i * (Math.PI * 2.0) / 5.0) + (RANDOM.nextDouble() - 0.5) * 0.3;
            Vec3d spawnPos = null;
            for (int attempt = 0; attempt < 4; attempt++) {
                double radius = 5.0 + RANDOM.nextDouble() * 3.0;
                double angle = baseAngle + (attempt * 0.22);
                int bx = MathHelper.floor(client.player.getX() + Math.cos(angle) * radius);
                int bz = MathHelper.floor(client.player.getZ() + Math.sin(angle) * radius);
                double groundY = findSolidGroundY(client.world, bx, refY, bz);
                if (!Double.isNaN(groundY)) {
                    spawnPos = new Vec3d(bx + 0.5, groundY, bz + 0.5);
                    break;
                }
            }
            if (spawnPos == null) {
                continue;
            }
            LivingEntity dummy = createPhantomDummy(client.world);
            if (dummy == null) {
                continue;
            }
            PhantomMob phantom = new PhantomMob(nextId++, dummy, spawnPos);
            aimPhantomAtPlayer(phantom, client.player);
            PHANTOMS.add(phantom);
        }
    }

    private static void onEndClientTick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            PHANTOMS.clear();
            spawnCooldown = 0;
            return;
        }
        if (client.isPaused()) {
            return;
        }

        boolean rampage = CyberwareState.isCyberpsychoRampage();
        int psychosis = CyberwareState.getCyberpsychosis();
        if (psychosis < 550 && !rampage) {
            PHANTOMS.clear();
            spawnCooldown = 0;
            return;
        }

        boolean inCutscene = CyberClient.isInCyberpsychosisCutscene();
        int maxPhantoms = rampage ? 10 : 8;
        if (spawnCooldown > 0) {
            spawnCooldown--;
        }
        if (!inCutscene && spawnCooldown <= 0 && PHANTOMS.size() < maxPhantoms) {
            spawnPhantom(client);
            spawnCooldown = 15 + RANDOM.nextInt(11); // 15..25 ticks
        }

        Vec3d playerPos = client.player.getPos();
        Vec3d playerEyePos = client.player.getEyePos();
        Vec3d playerLook = client.player.getRotationVec(1.0f);

        Iterator<PhantomMob> it = PHANTOMS.iterator();
        while (it.hasNext()) {
            PhantomMob phantom = it.next();
            phantom.age++;
            phantom.prevPos = phantom.pos;

            // Verify solid ground beneath the stationary phantom
            int bx = MathHelper.floor(phantom.pos.x);
            int bz = MathHelper.floor(phantom.pos.z);
            BlockPos belowPos = new BlockPos(bx, MathHelper.floor(phantom.pos.y - 0.01), bz);
            if (client.world.getBlockState(belowPos).getCollisionShape(client.world, belowPos).isEmpty()) {
                double newGroundY = findSolidGroundY(client.world, bx, MathHelper.floor(phantom.pos.y), bz);
                if (Double.isNaN(newGroundY)) {
                    spawnDissipateBurst(client, phantom.pos);
                    it.remove();
                    continue;
                }
                phantom.pos = new Vec3d(phantom.pos.x, newGroundY, phantom.pos.z);
                phantom.prevPos = phantom.pos;
            }

            // Stare directly at player from ground perch
            aimPhantomAtPlayer(phantom, client.player);

            // Neural glitch twitch every 8..12 ticks (+-12 deg head yaw)
            if (phantom.age >= phantom.nextTwitchAge) {
                phantom.headTwitchYaw = (RANDOM.nextFloat() * 24.0f) - 12.0f;
                phantom.twitchHoldTicks = 3;
                phantom.nextTwitchAge = phantom.age + 8 + RANDOM.nextInt(5);
            } else if (phantom.twitchHoldTicks > 0) {
                phantom.twitchHoldTicks--;
                if (phantom.twitchHoldTicks == 0) {
                    phantom.headTwitchYaw = 0.0f;
                }
            }

            // Stationary breathing/twitching (NOT running in place)
            phantom.dummy.age = client.player.age;
            phantom.dummy.limbAnimator.updateLimbs(0.0f, 0.05f);
            boolean twitchSwing = (phantom.age % 35 < 6);
            phantom.dummy.handSwinging = twitchSwing;
            phantom.dummy.handSwingProgress = twitchSwing ? ((phantom.age % 6) / 6.0f) : 0.0f;

            if (!inCutscene) {
                Vec3d toPhantom = phantom.pos.add(0.0, 1.0, 0.0).subtract(playerEyePos).normalize();
                double dot = playerLook.dotProduct(toPhantom);
                if (dot > 0.94) {
                    phantom.stareTicks++;
                }

                // Glitch-blink relocation once when stared at for 18 ticks
                if (phantom.stareTicks == 18 && !phantom.hasRelocated) {
                    phantom.hasRelocated = true;
                    Vec3d relocatedPos = findRelocationSpotBehindOrSide(client, playerLook);
                    if (relocatedPos != null) {
                        spawnDissipateBurst(client, phantom.pos);
                        phantom.pos = relocatedPos;
                        phantom.prevPos = relocatedPos;
                        aimPhantomAtPlayer(phantom, client.player);
                        client.player.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.35f, 0.55f);
                    }
                }
            }

            double dist = phantom.pos.distanceTo(playerPos);
            boolean playerSwingingNear = client.player.handSwinging && dist < 3.8;
            if (!inCutscene && (dist < 2.2 || playerSwingingNear || phantom.age > 140 || phantom.stareTicks > 40)) {
                spawnDissipateBurst(client, phantom.pos);
                it.remove();
            }
        }
    }

    private static void aimPhantomAtPlayer(PhantomMob phantom, PlayerEntity player) {
        Vec3d toPlayer = player.getPos().subtract(phantom.pos);
        double horizDist = Math.hypot(toPlayer.x, toPlayer.z);
        if (horizDist > 0.001) {
            phantom.yaw = (float) (MathHelper.atan2(toPlayer.z, toPlayer.x) * (180.0 / Math.PI)) - 90.0f;
        }
        double phantomEyeY = phantom.pos.y + Math.max(1.2, phantom.dummy.getStandingEyeHeight());
        double eyeDy = player.getEyeY() - phantomEyeY;
        phantom.headPitch = MathHelper.clamp(
                (float) -(MathHelper.atan2(eyeDy, Math.max(0.05, horizDist)) * (180.0 / Math.PI)),
                -60.0f,
                60.0f
        );
    }

    private static Vec3d findRelocationSpotBehindOrSide(MinecraftClient client, Vec3d playerLook) {
        if (client.world == null || client.player == null) {
            return null;
        }
        double backAngle = Math.atan2(-playerLook.z, -playerLook.x);
        int refY = client.player.getBlockY();
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = backAngle + (RANDOM.nextDouble() - 0.5) * (Math.PI * 1.15);
            double radius = 4.0 + RANDOM.nextDouble() * 4.0; // 4..8 blocks
            int bx = MathHelper.floor(client.player.getX() + Math.cos(angle) * radius);
            int bz = MathHelper.floor(client.player.getZ() + Math.sin(angle) * radius);
            double groundY = findSolidGroundY(client.world, bx, refY, bz);
            if (!Double.isNaN(groundY)) {
                return new Vec3d(bx + 0.5, groundY, bz + 0.5);
            }
        }
        return null;
    }

    private static double findSolidGroundY(ClientWorld world, int bx, int refY, int bz) {
        for (int dy = 6; dy >= -8; dy--) {
            int y = refY + dy;
            BlockPos groundPos = new BlockPos(bx, y - 1, bz);
            BlockPos feetPos = new BlockPos(bx, y, bz);
            BlockPos headPos = new BlockPos(bx, y + 1, bz);
            VoxelShape groundShape = world.getBlockState(groundPos).getCollisionShape(world, groundPos);
            if (!groundShape.isEmpty()
                    && world.getBlockState(feetPos).getCollisionShape(world, feetPos).isEmpty()
                    && world.getBlockState(headPos).getCollisionShape(world, headPos).isEmpty()) {
                return (y - 1) + groundShape.getMax(Direction.Axis.Y);
            }
        }
        return Double.NaN;
    }

    private static LivingEntity createPhantomDummy(ClientWorld world) {
        return switch (RANDOM.nextInt(5)) {
            case 0 -> EntityType.ZOMBIE.create(world);
            case 1 -> EntityType.SKELETON.create(world);
            case 2 -> EntityType.CREEPER.create(world);
            case 3 -> EntityType.ENDERMAN.create(world);
            default -> EntityType.WARDEN.create(world);
        };
    }

    private static void spawnPhantom(MinecraftClient client) {
        Vec3d spawnPos = null;
        int refY = client.player.getBlockY();
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            double radius = 4.5 + RANDOM.nextDouble() * 8.5; // 4.5..13 blocks
            double px = client.player.getX() + Math.cos(angle) * radius;
            double pz = client.player.getZ() + Math.sin(angle) * radius;
            int bx = MathHelper.floor(px);
            int bz = MathHelper.floor(pz);
            double groundY = findSolidGroundY(client.world, bx, refY, bz);
            if (!Double.isNaN(groundY)) {
                spawnPos = new Vec3d(bx + 0.5, groundY, bz + 0.5);
                break;
            }
        }
        if (spawnPos == null) {
            return;
        }

        LivingEntity dummy = createPhantomDummy(client.world);
        if (dummy == null) {
            return;
        }

        PhantomMob phantom = new PhantomMob(nextId++, dummy, spawnPos);
        aimPhantomAtPlayer(phantom, client.player);
        PHANTOMS.add(phantom);

        // Eerie client-only hallucination audio
        int soundPick = RANDOM.nextInt(3);
        if (soundPick == 0) {
            client.player.playSound(SoundEvents.ENTITY_ENDERMAN_STARE, 0.35f, 1.5f);
        } else if (soundPick == 1) {
            client.player.playSound(SoundEvents.ENTITY_WARDEN_STEP, 0.40f, 1.2f);
        } else {
            client.player.playSound(SoundEvents.ENTITY_CREEPER_PRIMED, 0.30f, 1.4f);
        }
    }

    private static void spawnDissipateBurst(MinecraftClient client, Vec3d pos) {
        if (client.world == null) {
            return;
        }
        for (int i = 0; i < 12; i++) {
            double ox = (RANDOM.nextDouble() - 0.5) * 0.9;
            double oy = RANDOM.nextDouble() * 1.6;
            double oz = (RANDOM.nextDouble() - 0.5) * 0.9;
            client.world.addParticle(ParticleTypes.SMOKE, pos.x + ox, pos.y + oy, pos.z + oz, 0.0, 0.04, 0.0);
            client.world.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.x + ox, pos.y + oy, pos.z + oz, ox * 0.15, 0.05, oz * 0.15);
            client.world.addParticle(CRIMSON_DUST, pos.x + ox, pos.y + oy, pos.z + oz, 0.0, 0.02, 0.0);
        }
    }

    private static void onAfterEntities(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean active = CyberwareState.isCyberpsychoRampage() || CyberwareState.getCyberpsychosis() >= 550;
        if (client.player == null || client.world == null || !active) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }

        Vec3d cameraPos = context.camera().getPos();
        float tickDelta = context.tickCounter().getTickDelta(true);

        for (PhantomMob phantom : PHANTOMS) {
            // Random hologram flicker
            if ((client.player.age + phantom.id) % 6 == 0) {
                continue;
            }

            double ix = MathHelper.lerp(tickDelta, phantom.prevPos.x, phantom.pos.x);
            double iy = MathHelper.lerp(tickDelta, phantom.prevPos.y, phantom.pos.y);
            double iz = MathHelper.lerp(tickDelta, phantom.prevPos.z, phantom.pos.z);
            double dx = ix - cameraPos.x;
            double dy = iy - cameraPos.y;
            double dz = iz - cameraPos.z;

            phantom.dummy.setPos(ix, iy, iz);
            phantom.dummy.prevX = phantom.prevPos.x;
            phantom.dummy.prevY = phantom.prevPos.y;
            phantom.dummy.prevZ = phantom.prevPos.z;
            phantom.dummy.setYaw(phantom.yaw);
            phantom.dummy.prevYaw = phantom.yaw;
            phantom.dummy.bodyYaw = phantom.yaw;
            phantom.dummy.prevBodyYaw = phantom.yaw;
            phantom.dummy.headYaw = phantom.yaw + phantom.headTwitchYaw;
            phantom.dummy.prevHeadYaw = phantom.dummy.headYaw;
            phantom.dummy.setPitch(phantom.headPitch);
            phantom.dummy.prevPitch = phantom.headPitch;

            client.getEntityRenderDispatcher().render(
                    phantom.dummy,
                    dx,
                    dy,
                    dz,
                    phantom.yaw,
                    tickDelta,
                    matrices,
                    consumers,
                    0xF000F0
            );

            renderGlitchCageAndTag(
                    client,
                    matrices,
                    consumers,
                    dx,
                    dy,
                    dz,
                    phantom.dummy.getWidth(),
                    phantom.dummy.getHeight(),
                    phantom.id,
                    phantom.age,
                    tickDelta
            );
        }

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)
                    || living == client.player
                    || !living.isAlive()
                    || living.isSpectator()
                    || client.player.squaredDistanceTo(living) > 24.0 * 24.0) {
                continue;
            }
            if ((client.player.age + living.getId()) % 11 == 0) {
                continue;
            }

            double ix = MathHelper.lerp(tickDelta, living.lastRenderX, living.getX());
            double iy = MathHelper.lerp(tickDelta, living.lastRenderY, living.getY());
            double iz = MathHelper.lerp(tickDelta, living.lastRenderZ, living.getZ());
            double dx = ix - cameraPos.x;
            double dy = iy - cameraPos.y;
            double dz = iz - cameraPos.z;

            renderGlitchCageAndTag(
                    client,
                    matrices,
                    consumers,
                    dx,
                    dy,
                    dz,
                    living.getWidth(),
                    living.getHeight(),
                    living.getId(),
                    living.age,
                    tickDelta
            );
        }
    }

    private static void renderGlitchCageAndTag(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider consumers,
                                               double dx, double dy, double dz, float entityWidth, float entityHeight,
                                               int seedId, float animAge, float tickDelta) {
        // Flickering translucent crimson & cyan glitch bounding boxes around the mob
        VertexConsumer boxBuffer = consumers.getBuffer(RenderLayer.getDebugFilledBox());
        float w = Math.max(0.35f, entityWidth * 0.55f);
        float h = Math.max(1.2f, entityHeight);
        matrices.push();
        matrices.translate(dx, dy, dz);
        for (int b = 0; b < 4; b++) {
            long seed = (client.player.age * 31L + seedId * 97L + b * 17L);
            double ox = ((seed % 7) - 3) * 0.06;
            double oy = (((seed >> 3) % 5) - 2) * 0.08;
            double oz = (((seed >> 6) % 7) - 3) * 0.06;
            boolean crimson = (b % 2 == 0);
            float r = crimson ? 1.0f : 0.0f;
            float g = crimson ? 0.0f : 0.94f;
            float bl = crimson ? 0.24f : 1.0f;
            WorldRenderer.renderFilledBox(
                    matrices,
                    boxBuffer,
                    -w + ox,
                    oy,
                    -w + oz,
                    w + ox,
                    h + oy,
                    w + oz,
                    r,
                    g,
                    bl,
                    0.20f
            );
        }

        // Red holographic scanline cage (corner pillars, top/bottom frame, and sweeping scanline rings)
        float cw = w + 0.14f;
        float ch = h + 0.10f;
        float t = 0.018f;
        // 4 vertical corner pillars
        WorldRenderer.renderFilledBox(matrices, boxBuffer, -cw, 0.0, -cw, -cw + t, ch, -cw + t, 1.0f, 0.04f, 0.22f, 0.78f);
        WorldRenderer.renderFilledBox(matrices, boxBuffer, cw - t, 0.0, -cw, cw, ch, -cw + t, 1.0f, 0.04f, 0.22f, 0.78f);
        WorldRenderer.renderFilledBox(matrices, boxBuffer, -cw, 0.0, cw - t, -cw + t, ch, cw, 1.0f, 0.04f, 0.22f, 0.78f);
        WorldRenderer.renderFilledBox(matrices, boxBuffer, cw - t, 0.0, cw - t, cw, ch, cw, 1.0f, 0.04f, 0.22f, 0.78f);
        // Bottom & top horizontal cage frames
        renderCageRing(matrices, boxBuffer, cw, 0.02f, 0.022f, 1.0f, 0.0f, 0.24f, 0.75f);
        renderCageRing(matrices, boxBuffer, cw, ch, 0.022f, 1.0f, 0.0f, 0.24f, 0.75f);
        // 2 animated sweeping horizontal scanline rings
        float scanPhase1 = ((animAge + tickDelta) * 0.075f) % 1.0f;
        float scanPhase2 = (scanPhase1 + 0.5f) % 1.0f;
        renderCageRing(matrices, boxBuffer, cw + 0.02f, scanPhase1 * ch, 0.030f, 1.0f, 0.12f, 0.28f, 0.90f);
        renderCageRing(matrices, boxBuffer, cw + 0.02f, scanPhase2 * ch, 0.020f, 1.0f, 0.45f, 0.15f, 0.65f);

        // Floating "ERR://PHANTOM_ENTITY" glitch tag above the mob
        matrices.push();
        float tagJitterX = ((client.player.age + seedId) % 5 == 0) ? 0.05f : 0.0f;
        matrices.translate(tagJitterX, ch + 0.36f, 0.0f);
        matrices.multiply(client.getEntityRenderDispatcher().getRotation());
        matrices.scale(0.024f, -0.024f, 0.024f);
        Matrix4f posMatrix = matrices.peek().getPositionMatrix();
        String tag = "ERR://PHANTOM_ENTITY";
        float halfW = -client.textRenderer.getWidth(tag) / 2.0f;
        client.textRenderer.draw(
                tag,
                halfW + 1.0f,
                0.5f,
                0xAA00F0FF,
                false,
                posMatrix,
                consumers,
                TextRenderer.TextLayerType.NORMAL,
                0,
                0xF000F0
        );
        client.textRenderer.draw(
                tag,
                halfW,
                0.0f,
                0xFFFF003C,
                false,
                posMatrix,
                consumers,
                TextRenderer.TextLayerType.NORMAL,
                0x88140208,
                0xF000F0
        );
        matrices.pop();

        matrices.pop();
    }

    private static void renderCageRing(MatrixStack matrices, VertexConsumer buffer, float cw, float y, float thickness,
                                       float r, float g, float b, float a) {
        WorldRenderer.renderFilledBox(matrices, buffer, -cw, y, -cw, cw, y + thickness, -cw + thickness, r, g, b, a);
        WorldRenderer.renderFilledBox(matrices, buffer, -cw, y, cw - thickness, cw, y + thickness, cw, r, g, b, a);
        WorldRenderer.renderFilledBox(matrices, buffer, -cw, y, -cw, -cw + thickness, y + thickness, cw, r, g, b, a);
        WorldRenderer.renderFilledBox(matrices, buffer, cw - thickness, y, -cw, cw, y + thickness, cw, r, g, b, a);
    }

    private static final class PhantomMob {
        final int id;
        final LivingEntity dummy;
        Vec3d pos;
        Vec3d prevPos;
        float yaw;
        float headPitch;
        float headTwitchYaw;
        int twitchHoldTicks;
        int nextTwitchAge = 8 + RANDOM.nextInt(5);
        int age;
        int stareTicks;
        boolean hasRelocated;

        PhantomMob(int id, LivingEntity dummy, Vec3d pos) {
            this.id = id;
            this.dummy = dummy;
            this.pos = pos;
            this.prevPos = pos;
        }
    }
}
