package name.cyber_zlo1.client.mixin;

import name.cyber_zlo1.client.render.MantisBladesFeatureRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    protected PlayerEntityRendererMixin(
            EntityRendererFactory.Context ctx,
            PlayerEntityModel<AbstractClientPlayerEntity> model,
            float shadowRadius
    ) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cyber$addMantisBladesFeature(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
        PlayerEntityRenderer self = (PlayerEntityRenderer) (Object) this;
        this.addFeature(new MantisBladesFeatureRenderer(self));
    }

    @Inject(method = "renderArm", at = @At("TAIL"))
    private void cyber$renderFirstPersonArmMantisBlade(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            AbstractClientPlayerEntity player,
            ModelPart arm,
            ModelPart sleeve,
            CallbackInfo ci
    ) {
        if (MantisBladesFeatureRenderer.shouldRenderBlades(player)) {
            boolean isRight = (arm == this.getModel().rightArm);
            MinecraftClient client = MinecraftClient.getInstance();
            float tickDelta = client != null ? client.getRenderTickCounter().getTickDelta(true) : 0.0f;
            float deployProgress = MantisBladesFeatureRenderer.getDeployProgress(player, tickDelta);
            matrices.push();
            arm.rotate(matrices);
            MantisBladesFeatureRenderer.renderArmMantisBlade(
                    matrices,
                    vertexConsumers,
                    isRight,
                    deployProgress,
                    player.age,
                    tickDelta
            );
            matrices.pop();
        }
    }
}
