package name.cyber_zlo1.mixin;

import name.cyber_zlo1.cyberware.CyberwareState;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobTimeDilationMixin {
	@Inject(method = "tickNewAi", at = @At("HEAD"), cancellable = true)
	private void cyber$onTickNewAi(CallbackInfo ci) {
		MobEntity self = (MobEntity) (Object) this;
		if (CyberwareState.isWorldTimeDilated(self.getWorld()) && (self.age % 5 != 0)) {
			ci.cancel();
		}
	}
}
