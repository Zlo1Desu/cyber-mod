package name.cyber_zlo1.client;

import name.cyber_zlo1.Cyber;
import name.cyber_zlo1.client.hud.SandevistanHudOverlay;
import name.cyber_zlo1.client.render.SandevistanAfterimageRenderer;
import name.cyber_zlo1.client.screen.CyberwareScreen;
import name.cyber_zlo1.cyberware.CyberwareState;
import name.cyber_zlo1.network.CyberwareNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class CyberClient implements ClientModInitializer {
    private static KeyBinding openCyberwareKey;
    private static KeyBinding activateSandevistanKey;

    private static boolean wasJumpPressed = false;
    private static boolean canDoubleJump = false;
    private static int ticksOffGround = 0;

    @Override
    public void onInitializeClient() {
        HandledScreens.register(Cyber.CYBERWARE_SCREEN_HANDLER, CyberwareScreen::new);

        ClientPlayNetworking.registerGlobalReceiver(
                CyberwareNetworking.SyncCyberwareStatePayload.ID,
                (payload, context) -> context.client().execute(() ->
                        CyberwareState.updateExtended(
                                payload.activeTicks(),
                                payload.cooldownTicks(),
                                payload.equipped(),
                                payload.kiroshiEquipped(),
                                payload.reinforcedTendonsEquipped(),
                                payload.secondHeartCooldown()
                        )
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
            if (client.player == null) {
                wasJumpPressed = false;
                canDoubleJump = false;
                ticksOffGround = 0;
                return;
            }
            if (client.world != null && !client.isPaused()) {
                int active = CyberwareState.getActiveTicks();
                int cooldown = CyberwareState.getCooldownTicks();
                int shCooldown = CyberwareState.getSecondHeartCooldown();
                if (active > 0 || cooldown > 0 || shCooldown > 0) {
                    CyberwareState.updateExtended(
                            Math.max(0, active - 1),
                            Math.max(0, cooldown - 1),
                            CyberwareState.isSandevistanEquipped(),
                            CyberwareState.isKiroshiEquipped(),
                            CyberwareState.isReinforcedTendonsEquipped(),
                            Math.max(0, shCooldown - 1)
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
            if (CyberwareState.isReinforcedTendonsEquipped()
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
        });

        SandevistanAfterimageRenderer.register();
        HudRenderCallback.EVENT.register(new SandevistanHudOverlay());
    }
}
