package name.cyber_zlo1.client.mixin;

import name.cyber_zlo1.client.CyberClient;
import name.cyber_zlo1.cyberware.CyberwareState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class ClientPlayerFovMixin {
    @Inject(method = "getFovMultiplier", at = @At("RETURN"), cancellable = true)
    private void cyber$sandevistanFovWarp(CallbackInfoReturnable<Float> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || (Object) this != client.player) {
            return;
        }
        if (CyberClient.isInCyberpsychosisCutscene()) {
            float p = CyberClient.getCutsceneProgress(1.0f);
            float fov;
            if (p < 0.75f) {
                float t = p / 0.75f;
                float pulse = (float) Math.sin(p * 28.0f) * 0.025f;
                fov = MathHelper.lerp(t, 1.0f, 0.72f) + pulse;
            } else {
                float t = MathHelper.clamp((p - 0.75f) / 0.25f, 0.0f, 1.0f);
                float eased = 1.0f - (1.0f - t) * (1.0f - t);
                fov = MathHelper.lerp(eased, 0.72f, 1.26f);
            }
            cir.setReturnValue(fov);
            return;
        }
        if (!CyberwareState.isActive()) {
            return;
        }

        int activeTicks = CyberwareState.getActiveTicks();
        float fov;
        if (activeTicks > 148) {
            // Quick inward-then-outward warp pulse on activation (160..148)
            float t = (160 - activeTicks) / 12.0f;
            float pulse = (float) Math.sin(t * Math.PI) * 0.06f;
            fov = 1.05f + (0.17f * t) + pulse;
        } else {
            // Steady hyper-reflex FOV so Speed IV + sprinting doesn't over-stretch
            fov = 1.18f;
        }
        cir.setReturnValue(fov);
    }
}
