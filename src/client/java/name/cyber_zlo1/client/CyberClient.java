package name.cyber_zlo1.client;

import name.cyber_zlo1.Cyber;
import name.cyber_zlo1.client.audio.CyberOpenALSynth;
import name.cyber_zlo1.client.hud.SandevistanHudOverlay;
import name.cyber_zlo1.client.render.CyberpsychosisHallucinationRenderer;
import name.cyber_zlo1.client.render.MantisBladesFeatureRenderer;
import name.cyber_zlo1.client.render.SandevistanAfterimageRenderer;
import name.cyber_zlo1.client.screen.CyberwareScreen;
import name.cyber_zlo1.cyberware.CyberwareState;
import name.cyber_zlo1.network.CyberSoundPayload;
import name.cyber_zlo1.network.CyberwareNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class CyberClient implements ClientModInitializer {
    private static KeyBinding openCyberwareKey;
    private static KeyBinding activateSandevistanKey;

    private static boolean wasJumpPressed = false;
    private static boolean canDoubleJump = false;
    private static int ticksOffGround = 0;

    private static boolean wasRampageLastTick = false;
    private static int cutsceneTicks = 0;
    private static Perspective savedPerspective = null;
    private static float cutsceneBaseYaw = 0.0f;
    private static boolean wasMantisEquipped = false;
    private static boolean wasMantisDeployed = false;

    public static boolean isInCyberpsychosisCutscene() {
        return cutsceneTicks > 0;
    }

    public static int getCutsceneTicks() {
        return cutsceneTicks;
    }

    public static float getCutsceneProgress(float tickDelta) {
        return MathHelper.clamp((60.0f - (cutsceneTicks - tickDelta)) / 60.0f, 0.0f, 1.0f);
    }

    @Override
    public void onInitializeClient() {
        HandledScreens.register(Cyber.CYBERWARE_SCREEN_HANDLER, CyberwareScreen::new);

        ClientPlayNetworking.registerGlobalReceiver(
                CyberwareNetworking.SyncCyberwareStatePayload.ID,
                (payload, context) -> context.client().execute(() ->
                        CyberwareState.updateFull(
                                payload.activeTicks(),
                                payload.cooldownTicks(),
                                payload.equipped(),
                                payload.kiroshiEquipped(),
                                payload.reinforcedTendonsEquipped(),
                                payload.secondHeartCooldown(),
                                payload.mantisBladesEquipped(),
                                payload.cyberpsychosis(),
                                payload.cyberpsychoRampage(),
                                payload.totalCapacity()
                        )
                )
        );

        ClientPlayNetworking.registerGlobalReceiver(
                CyberSoundPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        CyberOpenALSynth.play(payload.soundId(), payload.volume(), payload.pitch())
                )
        );

        openCyberwareKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cyber.open_cyberware",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                "category.cyber.cyberware"
        ));

        activateSandevistanKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cyber.activate_sandevistan",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.cyber.cyberware"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MantisBladesFeatureRenderer.tick(client);
            if (client.player == null) {
                wasJumpPressed = false;
                canDoubleJump = false;
                ticksOffGround = 0;
                if (cutsceneTicks > 0 && savedPerspective != null) {
                    client.options.setPerspective(savedPerspective);
                    client.options.sneakKey.setPressed(false);
                }
                cutsceneTicks = 0;
                wasRampageLastTick = false;
                savedPerspective = null;
                wasMantisEquipped = false;
                wasMantisDeployed = false;
                return;
            }
            if (client.world != null && !client.isPaused()) {
                int active = CyberwareState.getActiveTicks();
                int cooldown = CyberwareState.getCooldownTicks();
                int shCooldown = CyberwareState.getSecondHeartCooldown();
                if (active > 0 || cooldown > 0 || shCooldown > 0) {
                    CyberwareState.updateFull(
                            Math.max(0, active - 1),
                            Math.max(0, cooldown - 1),
                            CyberwareState.isSandevistanEquipped(),
                            CyberwareState.isKiroshiEquipped(),
                            CyberwareState.isReinforcedTendonsEquipped(),
                            Math.max(0, shCooldown - 1),
                            CyberwareState.isMantisBladesEquipped(),
                            CyberwareState.getCyberpsychosis(),
                            CyberwareState.isCyberpsychoRampage(),
                            CyberwareState.getTotalCapacity()
                    );
                }
            }
            while (openCyberwareKey.wasPressed()) {
                ClientPlayNetworking.send(CyberwareNetworking.OpenCyberwarePayload.INSTANCE);
            }
            while (activateSandevistanKey.wasPressed()) {
                ClientPlayNetworking.send(CyberwareNetworking.ActivateSandevistanPayload.INSTANCE);
            }

            if (client.player.isOnGround()) {
                canDoubleJump = true;
                ticksOffGround = 0;
            } else {
                ticksOffGround++;
            }

            boolean jumpPressed = client.options.jumpKey.isPressed();
            if (cutsceneTicks == 0
                    && CyberwareState.isReinforcedTendonsEquipped()
                    && canDoubleJump
                    && ticksOffGround >= 4
                    && jumpPressed
                    && !wasJumpPressed
                    && !client.player.getAbilities().flying
                    && !client.player.isTouchingWater()
                    && !client.player.isClimbing()) {
                canDoubleJump = false;
                Vec3d v = client.player.getVelocity();
                client.player.setVelocity(v.x * 1.15, 0.56, v.z * 1.15);
                ClientPlayNetworking.send(CyberwareNetworking.DoubleJumpPayload.INSTANCE);
            }
            wasJumpPressed = jumpPressed;

            if (client.world != null && !client.isPaused()) {
                boolean rampageNow = CyberwareState.isCyberpsychoRampage();
                if (rampageNow && !wasRampageLastTick) {
                    cutsceneTicks = 60;
                    savedPerspective = client.options.getPerspective();
                    client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                    cutsceneBaseYaw = client.player.getYaw();
                    CyberpsychosisHallucinationRenderer.spawnCutsceneCirclePhantoms(client);
                }
                wasRampageLastTick = rampageNow;

                if (cutsceneTicks > 0) {
                    if (!rampageNow) {
                        cutsceneTicks = 0;
                        client.options.setPerspective(savedPerspective != null ? savedPerspective : Perspective.FIRST_PERSON);
                        savedPerspective = null;
                        client.options.sneakKey.setPressed(false);
                    } else {
                        Vec3d v = client.player.getVelocity();
                        client.player.setVelocity(0.0, v.y, 0.0);

                        if (cutsceneTicks > 40) {
                            // Act I (0.0s - 1.0s): NEURAL COLLAPSE
                            client.options.sneakKey.setPressed(true);
                            client.player.setPitch(38.0f + (float) Math.sin(cutsceneTicks * 1.8) * 5.0f);
                            client.player.setYaw(cutsceneBaseYaw + (float) Math.sin(cutsceneTicks * 2.4) * 14.0f);
                        } else if (cutsceneTicks > 15) {
                            // Act II (1.0s - 2.25s): IMPLANT OVERDRIVE & MANTIS DEPLOY
                            client.options.sneakKey.setPressed(true);
                            if (cutsceneTicks == 35
                                    && CyberwareState.isMantisBladesEquipped()
                                    && !client.player.getMainHandStack().isOf(Cyber.IMMUNOBLOCKER)) {
                                for (int slot = 0; slot < 9; slot++) {
                                    if (client.player.getInventory().getStack(slot).isEmpty()) {
                                        client.player.getInventory().selectedSlot = slot;
                                        break;
                                    }
                                }
                            }
                            if (cutsceneTicks == 28) {
                                client.player.swingHand(Hand.MAIN_HAND);
                            }
                            client.player.setPitch(34.0f + (float) Math.sin(cutsceneTicks * 1.4) * 4.0f);
                            client.player.setYaw(cutsceneBaseYaw + (float) Math.sin(cutsceneTicks * 3.1) * 18.0f);
                        } else {
                            // Act III (2.25s - 3.0s): THE SNAP / AWAKENING
                            client.options.sneakKey.setPressed(false);
                            float snapT = (15 - cutsceneTicks) / 15.0f;
                            client.player.setPitch(MathHelper.lerp(snapT, 35.0f, -28.0f));
                            client.player.setYaw(cutsceneBaseYaw + (float) Math.sin(cutsceneTicks * 4.2) * 8.0f);
                            if (cutsceneTicks == 10 || cutsceneTicks == 4) {
                                client.player.swingHand(Hand.MAIN_HAND);
                            }
                        }

                        cutsceneTicks--;
                        if (cutsceneTicks == 0) {
                            client.options.setPerspective(savedPerspective != null ? savedPerspective : Perspective.FIRST_PERSON);
                            savedPerspective = null;
                            client.options.sneakKey.setPressed(false);
                            handleCyberpsychoRampageControl(client);
                        }
                    }
                } else if (rampageNow) {
                    handleCyberpsychoRampageControl(client);
                }

                boolean mantisEquipped = CyberwareState.isMantisBladesEquipped();
                boolean mantisDeployed = mantisEquipped && client.player.getMainHandStack().isEmpty();
                if (mantisEquipped && wasMantisEquipped && !(client.currentScreen instanceof CyberwareScreen)) {
                    if (mantisDeployed && !wasMantisDeployed) {
                        CyberOpenALSynth.play("mantis_unsheathe", 0.85f, 1.0f);
                    } else if (!mantisDeployed && wasMantisDeployed) {
                        CyberOpenALSynth.play("mantis_retract", 0.7f, 1.0f);
                    }
                }
                wasMantisEquipped = mantisEquipped;
                wasMantisDeployed = mantisDeployed;
            }
        });

        SandevistanAfterimageRenderer.register();
        CyberpsychosisHallucinationRenderer.register();
        HudRenderCallback.EVENT.register(new SandevistanHudOverlay());
    }

    private static void handleCyberpsychoRampageControl(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }

        // 1) Auto-Activate Sandevistan
        if (CyberwareState.isSandevistanEquipped() && !CyberwareState.isActive()) {
            ClientPlayNetworking.send(CyberwareNetworking.ActivateSandevistanPayload.INSTANCE);
        }

        // 2) Find Highest-Priority Target within 22 blocks
        double maxDistSq = 22.0 * 22.0;
        PlayerEntity nearestPlayer = null;
        double nearestPlayerDistSq = maxDistSq;
        LivingEntity nearestMob = null;
        double nearestMobDistSq = maxDistSq;

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()) {
                continue;
            }
            if (living == client.player) {
                continue;
            }
            double distSq = client.player.squaredDistanceTo(living);
            if (distSq > maxDistSq) {
                continue;
            }
            if (living instanceof PlayerEntity p) {
                if (!p.isCreative() && distSq < nearestPlayerDistSq) {
                    nearestPlayerDistSq = distSq;
                    nearestPlayer = p;
                }
            } else if (distSq < nearestMobDistSq) {
                nearestMobDistSq = distSq;
                nearestMob = living;
            }
        }

        LivingEntity realTarget = (nearestPlayer != null) ? nearestPlayer : nearestMob;
        Vec3d targetEyePos = (realTarget != null)
                ? realTarget.getEyePos()
                : CyberpsychosisHallucinationRenderer.getNearestPhantomPos(client.player);

        // 3) Seize Camera, Movement & Attacks
        if (targetEyePos != null) {
            Vec3d eyePos = client.player.getEyePos();
            double dx = targetEyePos.x - eyePos.x;
            double dy = targetEyePos.y - eyePos.y;
            double dz = targetEyePos.z - eyePos.z;
            double horizDist = Math.hypot(dx, dz);
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            float targetYaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            float targetPitch = (float) -(MathHelper.atan2(dy, Math.max(0.001, horizDist)) * (180.0 / Math.PI));

            float jitterYaw = (float) (Math.sin(client.player.age * 1.7) * 4.5);
            float jitterPitch = (float) (Math.cos(client.player.age * 2.3) * 3.5);
            client.player.setYaw(MathHelper.lerpAngleDegrees(0.45f, client.player.getYaw(), targetYaw + jitterYaw));
            client.player.setPitch(MathHelper.clamp(
                    MathHelper.lerp(0.45f, client.player.getPitch(), targetPitch + jitterPitch),
                    -90.0f,
                    90.0f
            ));

            if (realTarget != null) {
                client.player.setSprinting(true);
                double speed = CyberwareState.isActive() ? 0.45 : 0.30;
                if (horizDist > 0.2) {
                    Vec3d v = client.player.getVelocity();
                    client.player.setVelocity((dx / horizDist) * speed, v.y, (dz / horizDist) * speed);
                }

                if (CyberwareState.isReinforcedTendonsEquipped()
                        && !client.player.isOnGround()
                        && client.player.age % 16 == 0) {
                    Vec3d v = client.player.getVelocity();
                    client.player.setVelocity(v.x * 1.15, 0.56, v.z * 1.15);
                    ClientPlayNetworking.send(CyberwareNetworking.DoubleJumpPayload.INSTANCE);
                }
                if (client.player.isOnGround() && (client.player.horizontalCollision || dist > 5.0)) {
                    client.player.jump();
                }

                // Auto-Slash / Attack
                if (dist <= 4.3 && client.player.age % 4 == 0) {
                    if (CyberwareState.isMantisBladesEquipped()
                            && !client.player.getMainHandStack().isEmpty()
                            && !client.player.getMainHandStack().isOf(Cyber.IMMUNOBLOCKER)) {
                        for (int slot = 0; slot < 9; slot++) {
                            if (client.player.getInventory().getStack(slot).isEmpty()) {
                                client.player.getInventory().selectedSlot = slot;
                                break;
                            }
                        }
                    }
                    if (client.interactionManager != null) {
                        client.interactionManager.attackEntity(client.player, realTarget);
                    }
                    client.player.swingHand(Hand.MAIN_HAND);
                }
            } else {
                // Grounded phantom only: glare & twitch erratically toward it, slash if close, never leap off cliffs
                client.player.setSprinting(false);
                if (dist <= 3.8 && client.player.age % 5 == 0) {
                    client.player.swingHand(Hand.MAIN_HAND);
                } else if (client.player.age % 14 == 0) {
                    client.player.swingHand(Hand.MAIN_HAND);
                }
            }
        } else {
            // Erratic twitch & dash when no target is within 22 blocks
            float twitchYaw = (float) (Math.sin(client.player.age * 0.9) * 14.0);
            float twitchPitch = (float) (Math.cos(client.player.age * 1.3) * 6.0);
            client.player.setYaw(client.player.getYaw() + twitchYaw * 0.25f);
            client.player.setPitch(MathHelper.clamp(client.player.getPitch() + twitchPitch * 0.25f, -90.0f, 90.0f));
            if (client.player.age % 12 == 0) {
                double rad = Math.toRadians(client.player.getYaw());
                Vec3d v = client.player.getVelocity();
                client.player.setVelocity(-Math.sin(rad) * 0.32, v.y, Math.cos(rad) * 0.32);
            }
            if (client.player.age % 7 == 0) {
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }
}
