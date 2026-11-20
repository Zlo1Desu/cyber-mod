package name.cyber_zlo1.client.mixin;

import name.cyber_zlo1.client.render.MantisBladesFeatureRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Shadow
    protected abstract void renderArmHoldingItem(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            float equipProgress,
            float swingProgress,
            Arm arm
    );

    @Inject(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderArmHoldingItem(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IFFLnet/minecraft/util/Arm;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void cyber$animateMainMantisBlade(
            AbstractClientPlayerEntity player,
            float tickDelta,
            float pitch,
            Hand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        if (hand == Hand.MAIN_HAND
                && item.isEmpty()
                && player.getMainHandStack().isEmpty()
                && MantisBladesFeatureRenderer.shouldRenderBlades(player)) {
            float deploy = MantisBladesFeatureRenderer.getDeployProgress(player, tickDelta);
            float breath = (float) Math.sin((player.age + tickDelta) * 0.11f) * 0.012f * deploy;
            float slashArc = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI) * deploy;
            float dir = (player.getMainArm() == Arm.RIGHT) ? 1.0f : -1.0f;
            matrices.translate(0.0, breath, -slashArc * 0.06f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(dir * slashArc * 16.0f));
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("TAIL"))
    private void cyber$renderOffhandMantisBlade(
            AbstractClientPlayerEntity player,
            float tickDelta,
            float pitch,
            Hand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        if (hand == Hand.MAIN_HAND
                && item.isEmpty()
                && player.getMainHandStack().isEmpty()
                && player.getOffHandStack().isEmpty()
                && MantisBladesFeatureRenderer.shouldRenderBlades(player)) {
            float deploy = MantisBladesFeatureRenderer.getDeployProgress(player, tickDelta);
            float breath = (float) Math.sin((player.age + tickDelta) * 0.11f + 0.6f) * 0.012f * deploy;
            float slashArc = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI) * deploy;
            float dir = (player.getMainArm() == Arm.RIGHT) ? 1.0f : -1.0f;

            matrices.push();
            matrices.translate(0.0, breath, -slashArc * 0.08f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-dir * slashArc * 22.0f));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-slashArc * 14.0f));
            this.renderArmHoldingItem(
                    matrices,
                    vertexConsumers,
                    light,
                    equipProgress,
                    swingProgress,
                    player.getMainArm().getOpposite()
            );
            matrices.pop();
        }
    }
}
