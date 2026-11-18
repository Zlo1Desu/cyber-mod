package name.cyber_zlo1.mixin;

import name.cyber_zlo1.cyberware.CyberwareState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(World.class)
public abstract class WorldTimeDilationMixin {
	@Unique
	private static final ThreadLocal<Vec3d> SAVED_VELOCITY = new ThreadLocal<>();

	@Unique
	private static final ThreadLocal<Boolean> SAVED_NO_GRAVITY = new ThreadLocal<>();

	@Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
	private <T extends Entity> void cyber$onTickEntityHead(Consumer<T> tickConsumer, T entity, CallbackInfo ci) {
		SAVED_VELOCITY.remove();
		SAVED_NO_GRAVITY.remove();

		World world = (World) (Object) this;
		if (!CyberwareState.isWorldTimeDilated(world) || CyberwareState.isEntityImmuneToDilation(entity)) {
			return;
		}

		if (entity instanceof LivingEntity living) {
			living.timeUntilRegen = 0;
			if ((world.getTime() + entity.getId()) % 20 != 0) {
				entity.prevX = entity.getX();
				entity.prevY = entity.getY();
				entity.prevZ = entity.getZ();
				entity.lastRenderX = entity.getX();
				entity.lastRenderY = entity.getY();
				entity.lastRenderZ = entity.getZ();
				entity.prevYaw = entity.getYaw();
				entity.prevPitch = entity.getPitch();
				living.prevBodyYaw = living.bodyYaw;
				living.prevHeadYaw = living.headYaw;
				living.lastHandSwingProgress = living.handSwingProgress;
				ci.cancel();
			}
			return;
		}

		Vec3d savedVel = entity.getVelocity();
		if (savedVel.lengthSquared() > 1.0E-7) {
			SAVED_VELOCITY.set(savedVel);
			SAVED_NO_GRAVITY.set(entity.hasNoGravity());
			entity.setVelocity(savedVel.multiply(CyberwareState.TIME_DILATION_FACTOR));
			entity.setNoGravity(true);
		}
	}

	@Inject(method = "tickEntity", at = @At("RETURN"))
	private <T extends Entity> void cyber$onTickEntityReturn(Consumer<T> tickConsumer, T entity, CallbackInfo ci) {
		Vec3d realVel = SAVED_VELOCITY.get();
		if (realVel == null) {
			return;
		}

		boolean oldNoGrav = Boolean.TRUE.equals(SAVED_NO_GRAVITY.get());
		SAVED_VELOCITY.remove();
		SAVED_NO_GRAVITY.remove();

		entity.setNoGravity(oldNoGrav);
		Vec3d postVel = entity.getVelocity();
		boolean inGround = entity.isOnGround() || postVel.lengthSquared() < 1.0E-8;
		if (!inGround) {
			double grav = oldNoGrav ? 0.0 : -0.002;
			entity.setVelocity(realVel.multiply(0.999).add(0.0, grav, 0.0));
		}
	}
}
