package name.cyber_zlo1.mixin;

import name.cyber_zlo1.cyberware.CyberwareState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class EntityTimeDilationMixin {
	@ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
	private Vec3d cyber$dilateMovement(Vec3d movement) {
		Entity self = (Entity) (Object) this;
		if (CyberwareState.isWorldTimeDilated(self.getWorld()) && !CyberwareState.isEntityImmuneToDilation(self)) {
			return movement.multiply(CyberwareState.TIME_DILATION_FACTOR);
		}
		return movement;
	}
}
