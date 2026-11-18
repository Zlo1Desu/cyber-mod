package name.cyber_zlo1.client.mixin;

import name.cyber_zlo1.cyberware.CyberwareState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
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
