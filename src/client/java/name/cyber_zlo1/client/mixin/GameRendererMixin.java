package name.cyber_zlo1.client.mixin;

import name.cyber_zlo1.client.render.CyberPostShaderManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.resource.ResourceFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    public abstract boolean isRenderingPanorama();

    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void cyber$applyPostProcessingShaders(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!this.isRenderingPanorama()) {
            CyberPostShaderManager.renderPostEffects(tickCounter);
        }
    }

    @Inject(method = "onResized", at = @At("TAIL"))
    private void cyber$resizePostShaders(int width, int height, CallbackInfo ci) {
        CyberPostShaderManager.onResized(width, height);
    }

    @Inject(method = "loadPrograms", at = @At("TAIL"))
    private void cyber$reloadPostShaders(ResourceFactory factory, CallbackInfo ci) {
        CyberPostShaderManager.invalidate();
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void cyber$closePostShaders(CallbackInfo ci) {
        CyberPostShaderManager.close();
    }
}
