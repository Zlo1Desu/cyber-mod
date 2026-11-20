package name.cyber_zlo1.mixin;

import name.cyber_zlo1.Cyber;
import name.cyber_zlo1.cyberware.CyberwareHolder;
import name.cyber_zlo1.cyberware.CyberwareItem;
import name.cyber_zlo1.cyberware.CyberwareSlotType;
import name.cyber_zlo1.cyberware.CyberwareState;
import name.cyber_zlo1.cyberware.SandevistanItem;
import name.cyber_zlo1.network.CyberwareNetworking;
import name.cyber_zlo1.sound.CyberSoundManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements CyberwareHolder {
	@Unique
	private static final Vector3f[] EDGERUNNERS_COLORS = new Vector3f[]{
			new Vector3f(0.0f, 1.0f, 0.64f),
			new Vector3f(0.0f, 0.94f, 1.0f),
			new Vector3f(0.99f, 0.93f, 0.04f),
			new Vector3f(1.0f, 0.0f, 0.33f)
	};

	@Unique
	private final SimpleInventory cyberwareInventory = new SimpleInventory(10);

	@Unique
	private int sandevistanActiveTicks = 0;

	@Unique
	private int sandevistanCooldownTicks = 0;

	@Unique
	private int secondHeartCooldown = 0;

	@Unique
	private int kerenzikovCooldownTicks = 0;

	@Unique
	private int cyberpsychosis = 0;

	@Unique
	private int immunoblockerTicks = 0;

	@Unique
	private int cyberpsychoRampageTicks = 0;

	@Unique
	private boolean lastSyncedEquipped = false;

	@Unique
	private int lastSyncedActiveTicks = 0;

	@Unique
	private int lastSyncedCooldownTicks = 0;

	@Unique
	private boolean lastSyncedKiroshi = false;

	@Unique
	private boolean lastSyncedTendons = false;

	@Unique
	private int lastSyncedSecondHeartCooldown = 0;

	@Unique
	private boolean lastSyncedMantis = false;

	@Unique
	private int lastSyncedCyberpsychosis = 0;

	@Unique
	private boolean lastSyncedRampage = false;

	@Unique
	private int lastSyncedTotalCapacity = 0;

	/** Mantis Blades combo counter (drives alternating slash arcs and pitch-stepped audio). */
	@Unique
	private int mantisComboCounter = 0;

	/** Whether Sandevistan was active at the end of the previous server tick (rising-edge detection). */
	@Unique
	private boolean sandevistanWasActive = false;

	/** Remaining frames of the expanding activation shockwave choreography (counts down from SHOCKWAVE_FRAMES). */
	@Unique
	private int sandevistanShockwaveFrames = 0;

	@Unique
	private static final int SHOCKWAVE_FRAMES = 9;

	@Override
	public SimpleInventory cyber$getCyberwareInventory() {
		return cyberwareInventory;
	}

	@Override
	public int cyber$getSandevistanActiveTicks() {
		return sandevistanActiveTicks;
	}

	@Override
	public void cyber$setSandevistanActiveTicks(int ticks) {
		this.sandevistanActiveTicks = Math.max(0, ticks);
	}

	@Override
	public int cyber$getSandevistanCooldownTicks() {
		return sandevistanCooldownTicks;
	}

	@Override
	public void cyber$setSandevistanCooldownTicks(int ticks) {
		this.sandevistanCooldownTicks = Math.max(0, ticks);
	}

	@Override
	public boolean cyber$hasSandevistanEquipped() {
		ItemStack osStack = cyberwareInventory.getStack(CyberwareSlotType.OPERATING_SYSTEM.getIndex());
		return !osStack.isEmpty() && osStack.getItem() instanceof SandevistanItem;
	}

	@Override
	public boolean cyber$hasImplantInSlot(CyberwareSlotType slot, Item item) {
		ItemStack stack = cyberwareInventory.getStack(slot.getIndex());
		return !stack.isEmpty() && stack.isOf(item);
	}

	@Override
	public boolean cyber$hasKiroshiEquipped() {
		return cyber$hasImplantInSlot(CyberwareSlotType.OCULAR, Cyber.KIROSHI_OPTICS);
	}

	@Override
	public boolean cyber$hasReinforcedTendonsEquipped() {
		return cyber$hasImplantInSlot(CyberwareSlotType.LEGS, Cyber.REINFORCED_TENDONS);
	}

	@Override
	public int cyber$getSecondHeartCooldown() {
		return secondHeartCooldown;
	}

	@Override
	public void cyber$setSecondHeartCooldown(int ticks) {
		this.secondHeartCooldown = Math.max(0, ticks);
	}

	@Override
	public int cyber$getTotalEquippedCapacity() {
		int total = 0;
		for (int i = 0; i < 10; i++) {
			ItemStack stack = cyberwareInventory.getStack(i);
			if (!stack.isEmpty() && stack.getItem() instanceof CyberwareItem ci) {
				total += ci.getCyberCapacityCost();
			}
		}
		return total;
	}

	@Override
	public boolean cyber$hasMantisBladesEquipped() {
		return cyber$hasImplantInSlot(CyberwareSlotType.ARMS, Cyber.MANTIS_BLADES);
	}

	@Override
	public int cyber$getCyberpsychosis() {
		return cyberpsychosis;
	}

	@Override
	public void cyber$setCyberpsychosis(int value) {
		this.cyberpsychosis = Math.max(0, Math.min(CyberwareState.MAX_CYBERPSYCHOSIS, value));
	}

	@Override
	public int cyber$getImmunoblockerTicks() {
		return immunoblockerTicks;
	}

	@Override
	public void cyber$setImmunoblockerTicks(int ticks) {
		int previous = this.immunoblockerTicks;
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (ticks > previous && !self.getWorld().isClient) {
			if (this.cyberpsychoRampageTicks > 0) {
				CyberSoundManager.cancelAll(self);
			}
			CyberSoundManager.playImmunoblockerInject(self);
			if (self.getWorld() instanceof ServerWorld serverWorld) {
				DustParticleEffect mintDust = new DustParticleEffect(new Vector3f(0.35f, 1.0f, 0.75f), 0.9f);
				serverWorld.spawnParticles(mintDust, self.getX(), self.getY() + 1.2, self.getZ(), 14, 0.3, 0.4, 0.3, 0.02);
				serverWorld.spawnParticles(ParticleTypes.WAX_OFF, self.getX(), self.getY() + 1.4, self.getZ(), 8, 0.35, 0.35, 0.35, 0.4);
			}
		}
		this.immunoblockerTicks = Math.max(0, ticks);
		if (this.immunoblockerTicks > 0) {
			this.cyberpsychoRampageTicks = 0;
		}
	}

	@Override
	public boolean cyber$isCyberpsychoRampage() {
		return cyberpsychoRampageTicks > 0;
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void cyber$writeCustomDataToNbt(NbtCompound nbt, CallbackInfo ci) {
		NbtCompound cyberNbt = new NbtCompound();
		DefaultedList<ItemStack> stacks = DefaultedList.ofSize(10, ItemStack.EMPTY);
		for (int i = 0; i < 10; i++) {
			stacks.set(i, cyberwareInventory.getStack(i));
		}
		Inventories.writeNbt(cyberNbt, stacks, ((Entity) (Object) this).getRegistryManager());
		cyberNbt.putInt("SandevistanActiveTicks", sandevistanActiveTicks);
		cyberNbt.putInt("SandevistanCooldownTicks", sandevistanCooldownTicks);
		cyberNbt.putInt("SecondHeartCooldown", secondHeartCooldown);
		cyberNbt.putInt("KerenzikovCooldownTicks", kerenzikovCooldownTicks);
		cyberNbt.putInt("Cyberpsychosis", cyberpsychosis);
		cyberNbt.putInt("ImmunoblockerTicks", immunoblockerTicks);
		cyberNbt.putInt("CyberpsychoRampageTicks", cyberpsychoRampageTicks);
		nbt.put("CyberwareData", cyberNbt);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void cyber$readCustomDataFromNbt(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.contains("CyberwareData", NbtElement.COMPOUND_TYPE)) {
			NbtCompound cyberNbt = nbt.getCompound("CyberwareData");
			DefaultedList<ItemStack> stacks = DefaultedList.ofSize(10, ItemStack.EMPTY);
			Inventories.readNbt(cyberNbt, stacks, ((Entity) (Object) this).getRegistryManager());
			for (int i = 0; i < 10; i++) {
				cyberwareInventory.setStack(i, stacks.get(i));
			}
			sandevistanActiveTicks = cyberNbt.getInt("SandevistanActiveTicks");
			sandevistanCooldownTicks = cyberNbt.getInt("SandevistanCooldownTicks");
			secondHeartCooldown = cyberNbt.getInt("SecondHeartCooldown");
			kerenzikovCooldownTicks = cyberNbt.getInt("KerenzikovCooldownTicks");
			cyberpsychosis = Math.max(0, Math.min(CyberwareState.MAX_CYBERPSYCHOSIS, cyberNbt.getInt("Cyberpsychosis")));
			immunoblockerTicks = Math.max(0, cyberNbt.getInt("ImmunoblockerTicks"));
			cyberpsychoRampageTicks = Math.max(0, cyberNbt.getInt("CyberpsychoRampageTicks"));
		}
	}

	@Unique
	private void cyber$tryTriggerSecondHeart(PlayerEntity self) {
		if (!self.getWorld().isClient
				&& cyber$hasImplantInSlot(CyberwareSlotType.CIRCULATORY, Cyber.SECOND_HEART)
				&& secondHeartCooldown == 0
				&& self.getHealth() > 0.0f
				&& self.getHealth() <= 4.0f) {
			self.setHealth(self.getMaxHealth());
			self.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 2));
			self.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 2));
			self.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 0));
			CyberSoundManager.playSecondHeartDefib(self);
			if (self.getWorld() instanceof ServerWorld serverWorld) {
				DustParticleEffect crimsonDust = new DustParticleEffect(new Vector3f(1.0f, 0.0f, 0.24f), 1.4f);
				DustParticleEffect goldDust = new DustParticleEffect(new Vector3f(0.99f, 0.85f, 0.04f), 1.4f);
				serverWorld.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, self.getX(), self.getY() + 1.0, self.getZ(),
						35, 0.4, 0.7, 0.4, 0.35);
				serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, self.getX(), self.getY() + 1.0, self.getZ(),
						28, 0.45, 0.6, 0.45, 0.2);
				serverWorld.spawnParticles(crimsonDust, self.getX(), self.getY() + 1.0, self.getZ(),
						20, 0.5, 0.6, 0.5, 0.08);
				serverWorld.spawnParticles(goldDust, self.getX(), self.getY() + 1.0, self.getZ(),
						20, 0.5, 0.6, 0.5, 0.08);
				// Defibrillator discharge ring around the chest.
				int ringPoints = 20;
				for (int i = 0; i < ringPoints; i++) {
					double angle = (2.0 * Math.PI * i) / ringPoints;
					double rx = Math.cos(angle);
					double rz = Math.sin(angle);
					serverWorld.spawnParticles(i % 2 == 0 ? crimsonDust : goldDust,
							self.getX() + rx * 0.7, self.getY() + 1.25, self.getZ() + rz * 0.7, 1, 0.0, 0.0, 0.0, 0.0);
					serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
							self.getX() + rx * 0.7, self.getY() + 1.25, self.getZ() + rz * 0.7, 0, rx, 0.05, rz, 0.25);
				}
			}
			self.sendMessage(Text.translatable("cyber.msg.second_heart_triggered"), true);
			secondHeartCooldown = 2400;
		}
	}

	@Inject(method = "applyDamage", at = @At("TAIL"))
	private void cyber$onApplyDamage(DamageSource source, float amount, CallbackInfo ci) {
		cyber$tryTriggerSecondHeart((PlayerEntity) (Object) this);
	}

	@Inject(method = "attack", at = @At("HEAD"))
	private void cyber$onAttack(Entity target, CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (self.getWorld().isClient) {
			return;
		}
		if (cyber$hasImplantInSlot(CyberwareSlotType.ARMS, Cyber.MANTIS_BLADES) && self.getMainHandStack().isEmpty()) {
			target.damage(self.getDamageSources().playerAttack(self), 9.0f);
			if (target instanceof LivingEntity living) {
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 1));
			}
			if (!self.isOnGround()) {
				Vec3d look = self.getRotationVec(1.0f);
				self.addVelocity(look.x * 0.25, 0.08, look.z * 0.25);
				self.velocityModified = true;
			}
			cyber$mantisStrikeFx(self, target);
			if (immunoblockerTicks == 0 && cyberpsychoRampageTicks == 0) {
				cyberpsychosis = Math.min(CyberwareState.MAX_CYBERPSYCHOSIS, cyberpsychosis + 25);
			}
		}
		if (sandevistanActiveTicks > 0 && self.getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.spawnParticles(ParticleTypes.SONIC_BOOM,
					target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
					1, 0.0, 0.0, 0.0, 0.0);
			serverWorld.spawnParticles(ParticleTypes.SWEEP_ATTACK,
					target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
					2, 0.2, 0.1, 0.2, 0.0);
		}
	}

	/** One Mantis Blade strike: advances the combo, plays the pitch-stepped cleave and the alternating arc. */
	@Unique
	private void cyber$mantisStrikeFx(PlayerEntity self, Entity target) {
		int step = mantisComboCounter++;
		if (mantisComboCounter > 1_000_000) {
			mantisComboCounter = 0;
		}
		CyberSoundManager.playMantisSlash(self, step);
		cyber$spawnMantisSlashParticles(self, target, step);
	}

	@Unique
	private void cyber$spawnMantisSlashParticles(PlayerEntity self, Entity target, int comboStep) {
		if (!(self.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		Vec3d look = self.getRotationVec(1.0f);
		Vec3d horizontalRight = new Vec3d(-look.z, 0.0, look.x);
		Vec3d right = horizontalRight.lengthSquared() > 1.0E-4 ? horizontalRight.normalize() : new Vec3d(1.0, 0.0, 0.0);
		Vec3d up = right.crossProduct(look).normalize();
		if (up.y < 0.0) {
			up = up.multiply(-1.0);
		}
		Vec3d center = new Vec3d(self.getX(), self.getY() + 1.15, self.getZ()).add(look.multiply(1.25));

		// Even strikes: low-left -> high-right. Odd strikes: high-left -> low-right.
		double diagonal = (comboStep % 2 == 0) ? 1.0 : -1.0;
		Vector3f crimson = new Vector3f(1.0f, 0.0f, 0.24f);
		Vector3f gold = new Vector3f(1.0f, 0.62f, 0.05f);
		Vector3f cyan = new Vector3f(0.0f, 0.94f, 1.0f);

		int segments = 14;
		for (int i = 0; i <= segments; i++) {
			double s = -1.0 + 2.0 * i / segments;         // -1 .. 1 along the blade path
			double bulge = 0.45 * (1.0 - s * s);            // arc bows forward in the middle
			Vec3d main = center
					.add(right.multiply(s * 1.05))
					.add(up.multiply(diagonal * s * 0.65))
					.add(look.multiply(bulge));
			// Second blade trails slightly behind and below for the twin-blade look.
			Vec3d twin = main.add(up.multiply(-0.16)).add(look.multiply(-0.12));

			// Gradient crimson -> orange-gold -> cyan along the arc.
			float g = (float) i / segments;
			Vector3f color = g < 0.5f ? cyber$lerpColor(crimson, gold, g * 2.0f) : cyber$lerpColor(gold, cyan, (g - 0.5f) * 2.0f);
			DustParticleEffect dust = new DustParticleEffect(color, 1.2f);
			DustParticleEffect twinDust = new DustParticleEffect(color, 0.8f);
			serverWorld.spawnParticles(dust, main.x, main.y, main.z, 1, 0.01, 0.01, 0.01, 0.0);
			serverWorld.spawnParticles(twinDust, twin.x, twin.y, twin.z, 1, 0.01, 0.01, 0.01, 0.0);
			if (i % 3 == 0) {
				serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, main.x, main.y, main.z, 1, 0.03, 0.03, 0.03, 0.08);
			}
		}
		serverWorld.spawnParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
		double tx = target.getX();
		double ty = target.getY() + target.getHeight() * 0.5;
		double tz = target.getZ();
		serverWorld.spawnParticles(ParticleTypes.CRIT, tx, ty, tz, 12, 0.25, 0.25, 0.25, 0.15);
		serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, tx, ty, tz, 6, 0.2, 0.25, 0.2, 0.2);
		if (comboStep % 3 == 2) {
			// Combo finisher: plasma flare on the target.
			serverWorld.spawnParticles(new DustParticleEffect(gold, 1.6f), tx, ty, tz, 10, 0.3, 0.3, 0.3, 0.05);
			serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, tx, ty, tz, 6, 0.2, 0.2, 0.2, 0.04);
		}
	}

	@Unique
	private static Vector3f cyber$lerpColor(Vector3f a, Vector3f b, float t) {
		float k = Math.max(0.0f, Math.min(1.0f, t));
		return new Vector3f(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, a.z + (b.z - a.z) * k);
	}

	/** Frame of the expanding Sandevistan activation double-ring shockwave. */
	@Unique
	private void cyber$spawnShockwaveFrame(ServerWorld serverWorld, PlayerEntity self, int frame) {
		double groundRadius = 0.6 + frame * 0.55;
		double waistRadius = 0.4 + frame * 0.35;
		int points = 18 + frame * 4;
		DustParticleEffect emerald = new DustParticleEffect(EDGERUNNERS_COLORS[0], 1.3f);
		DustParticleEffect cyan = new DustParticleEffect(EDGERUNNERS_COLORS[1], 1.3f);
		DustParticleEffect yellow = new DustParticleEffect(EDGERUNNERS_COLORS[2], 1.1f);
		double spin = frame * 0.35;
		for (int i = 0; i < points; i++) {
			double angle = (2.0 * Math.PI * i) / points;
			double cx = Math.cos(angle);
			double cz = Math.sin(angle);
			// Ground ring (emerald / cyan alternating).
			double gx = self.getX() + cx * groundRadius;
			double gz = self.getZ() + cz * groundRadius;
			serverWorld.spawnParticles(i % 2 == 0 ? emerald : cyan, gx, self.getY() + 0.1, gz, 1, 0.0, 0.0, 0.0, 0.0);
			if (i % 4 == 0) {
				serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, gx, self.getY() + 0.15, gz, 0, cx, 0.15, cz, 0.3);
			}
			// Waist ring, counter-rotating and slightly tilted for a 3D gyroscope feel.
			double wAngle = angle - spin;
			double wx = Math.cos(wAngle) * waistRadius;
			double wz = Math.sin(wAngle) * waistRadius;
			double tilt = Math.sin(wAngle + spin) * 0.25;
			serverWorld.spawnParticles(i % 3 == 0 ? yellow : cyan,
					self.getX() + wx, self.getY() + 1.1 + tilt, self.getZ() + wz, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (frame % 2 == 0) {
			serverWorld.spawnParticles(ParticleTypes.END_ROD, self.getX(), self.getY() + 1.0, self.getZ(),
					6, 0.2, 0.4, 0.2, 0.12);
		}
		if (frame == SHOCKWAVE_FRAMES - 1) {
			serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, self.getX(), self.getY() + 0.1, self.getZ(),
					14, groundRadius * 0.5, 0.05, groundRadius * 0.5, 0.01);
		}
	}

	/** Sandevistan movement FX: electric ground trail + 3-strand Edgerunners DNA helix up the player's back. */
	@Unique
	private void cyber$spawnSandevistanMovementFx(ServerWorld serverWorld, PlayerEntity self, int t) {
		Vec3d velocity = self.getVelocity();
		if (velocity.horizontalLengthSquared() > 0.004) {
			serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, self.getX(), self.getY() + 0.05, self.getZ(),
					2, 0.2, 0.02, 0.2, 0.02);
			DustParticleEffect trailDust = new DustParticleEffect(EDGERUNNERS_COLORS[t % 3], 0.9f);
			serverWorld.spawnParticles(trailDust, self.getX(), self.getY() + 0.08, self.getZ(), 2, 0.15, 0.02, 0.15, 0.0);
		}

		float yawRad = self.getYaw() * ((float) Math.PI / 180.0f);
		// Horizontal back vector (opposite of the facing direction).
		double backX = Math.sin(yawRad);
		double backZ = -Math.cos(yawRad);
		double baseX = self.getX() + backX * 0.38;
		double baseZ = self.getZ() + backZ * 0.38;
		double helixRadius = 0.26;
		double scroll = (t * 0.09) % 0.66;
		for (int strand = 0; strand < 3; strand++) {
			DustParticleEffect strandDust = new DustParticleEffect(EDGERUNNERS_COLORS[strand], 0.85f);
			double phase = strand * (2.0 * Math.PI / 3.0);
			for (int k = 0; k < 3; k++) {
				double h = scroll + k * 0.66;
				double angle = h * Math.PI * 2.2 + t * 0.3 + phase;
				double px = baseX + Math.cos(angle) * helixRadius;
				double pz = baseZ + Math.sin(angle) * helixRadius;
				serverWorld.spawnParticles(strandDust, px, self.getY() + 0.1 + h, pz, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		if (t % 4 == 0) {
			serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, baseX, self.getY() + 0.1 + scroll * 3.0, baseZ,
					1, 0.05, 0.05, 0.05, 0.03);
		}
	}

	/** Erratic crimson / magenta lightning burst erupting from the player during a cyberpsychosis rampage. */
	@Unique
	private void cyber$spawnRampageLightning(ServerWorld serverWorld, PlayerEntity self) {
		net.minecraft.util.math.random.Random random = self.getRandom();
		DustParticleEffect crimson = new DustParticleEffect(new Vector3f(1.0f, 0.0f, 0.25f), 1.2f);
		DustParticleEffect magenta = new DustParticleEffect(new Vector3f(0.95f, 0.0f, 0.85f), 1.0f);
		int bolts = 1 + random.nextInt(2);
		for (int b = 0; b < bolts; b++) {
			double yaw = random.nextDouble() * Math.PI * 2.0;
			double pitch = (random.nextDouble() - 0.3) * 1.2;
			double dx = Math.cos(yaw) * Math.cos(pitch);
			double dy = Math.sin(pitch);
			double dz = Math.sin(yaw) * Math.cos(pitch);
			double x = self.getX();
			double y = self.getY() + 1.1;
			double z = self.getZ();
			int segments = 5 + random.nextInt(3);
			for (int s = 0; s < segments; s++) {
				double step = 0.3;
				double nx = x + dx * step + (random.nextDouble() - 0.5) * 0.3;
				double ny = y + dy * step + (random.nextDouble() - 0.5) * 0.3;
				double nz = z + dz * step + (random.nextDouble() - 0.5) * 0.3;
				// Fill the segment so the bolt reads as a continuous jagged line.
				for (int f = 0; f < 3; f++) {
					double k = f / 3.0;
					serverWorld.spawnParticles((s + f) % 2 == 0 ? crimson : magenta,
							x + (nx - x) * k, y + (ny - y) * k, z + (nz - z) * k, 1, 0.0, 0.0, 0.0, 0.0);
				}
				if (s % 2 == 1) {
					serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, nx, ny, nz, 1, 0.02, 0.02, 0.02, 0.05);
				}
				x = nx;
				y = ny;
				z = nz;
			}
		}
		if (random.nextInt(3) == 0) {
			serverWorld.spawnParticles(ParticleTypes.SCULK_SOUL, self.getX(), self.getY() + 1.0, self.getZ(),
					2, 0.4, 0.5, 0.4, 0.03);
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void cyber$tick(CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (self.getWorld().isClient) {
			return;
		}

		boolean equipped = cyber$hasSandevistanEquipped();
		if (!equipped && sandevistanActiveTicks > 0) {
			sandevistanActiveTicks = 0;
			CyberSoundManager.playSandevistanDeactivate(self);
		}

		// Rising edge (activation via keybind packet or forced by a rampage): start the expanding shockwave.
		if (sandevistanActiveTicks > 0 && !sandevistanWasActive) {
			sandevistanShockwaveFrames = SHOCKWAVE_FRAMES;
		}
		if (sandevistanShockwaveFrames > 0) {
			if (self.getWorld() instanceof ServerWorld serverWorld) {
				cyber$spawnShockwaveFrame(serverWorld, self, SHOCKWAVE_FRAMES - sandevistanShockwaveFrames);
			}
			sandevistanShockwaveFrames--;
		}

		if (sandevistanActiveTicks > 0) {
			self.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10, 3, true, false, false));
			self.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 10, 2, true, false, false));
			self.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 10, 1, true, false, false));

			if (self.getWorld() instanceof ServerWorld serverWorld) {
				cyber$spawnSandevistanMovementFx(serverWorld, self, self.age);
			}

			sandevistanActiveTicks--;
			if (sandevistanActiveTicks == 0) {
				sandevistanCooldownTicks = CyberwareState.MAX_COOLDOWN_TICKS;
				CyberSoundManager.playSandevistanDeactivate(self);
				if (self.getWorld() instanceof ServerWorld serverWorld) {
					serverWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, self.getX(), self.getY() + 1.0, self.getZ(),
							24, 0.35, 0.6, 0.35, 0.06);
					serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, self.getX(), self.getY() + 1.0, self.getZ(),
							12, 0.4, 0.5, 0.4, 0.1);
				}
			}
		} else if (sandevistanCooldownTicks > 0) {
			sandevistanCooldownTicks--;
		}

		if (secondHeartCooldown > 0) {
			secondHeartCooldown--;
		}
		if (kerenzikovCooldownTicks > 0) {
			kerenzikovCooldownTicks--;
		}

		boolean kiroshiEquipped = cyber$hasKiroshiEquipped();
		if (kiroshiEquipped) {
			self.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 300, 0, true, false, false));
		}

		boolean tendonsEquipped = cyber$hasReinforcedTendonsEquipped();
		if (tendonsEquipped) {
			if (self.fallDistance > 3.0f) {
				self.fallDistance *= 0.85f;
			}
			if (sandevistanActiveTicks == 0) {
				self.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 20, 0, true, false, false));
			}
		}

		if (cyber$hasImplantInSlot(CyberwareSlotType.SKELETON, Cyber.TITANIUM_BONES)) {
			self.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 0, true, false, false));
			StatusEffectInstance existingHealthBoost = self.getStatusEffect(StatusEffects.HEALTH_BOOST);
			if (existingHealthBoost == null || existingHealthBoost.getDuration() < 40) {
				self.addStatusEffect(new StatusEffectInstance(StatusEffects.HEALTH_BOOST, 100, 0, true, false, false));
			}
		}

		if (cyber$hasImplantInSlot(CyberwareSlotType.NERVOUS, Cyber.KERENZIKOV)
				&& self.isSneaking()
				&& self.getVelocity().horizontalLengthSquared() > 0.008
				&& kerenzikovCooldownTicks == 0) {
			self.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 2, true, false, false));
			self.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 1, true, false, false));
			CyberSoundManager.playKerenzikovSurge(self);
			if (self.getWorld() instanceof ServerWorld serverWorld) {
				DustParticleEffect cyanDust = new DustParticleEffect(EDGERUNNERS_COLORS[1], 0.9f);
				serverWorld.spawnParticles(cyanDust, self.getX(), self.getY() + 1.0, self.getZ(), 10, 0.3, 0.5, 0.3, 0.0);
				serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, self.getX(), self.getY() + 1.6, self.getZ(),
						8, 0.2, 0.15, 0.2, 0.12);
			}
			kerenzikovCooldownTicks = 160;
		}

		cyber$tryTriggerSecondHeart(self);

		int totalCap = cyber$getTotalEquippedCapacity();
		boolean mantisEquipped = cyber$hasMantisBladesEquipped();

		if (immunoblockerTicks > 0) {
			immunoblockerTicks--;
			cyberpsychoRampageTicks = 0;
			if (self.age % 2 == 0 && cyberpsychosis > 0) {
				cyberpsychosis = Math.max(0, cyberpsychosis - 3);
			}
		} else if (cyberpsychoRampageTicks > 0) {
			cyberpsychosis = 550 + (cyberpsychoRampageTicks * 450) / 300;
			if (cyberpsychoRampageTicks > 240) {
				// 60-tick (3-second) Entry Cutscene: freeze horizontal movement, grant Resistance V, and emit neural breakdown FX.
				self.setVelocity(0.0, Math.min(0.0, self.getVelocity().y), 0.0);
				self.velocityModified = true;
				self.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 15, 4, true, false, false));

				if (self.getWorld() instanceof ServerWorld sw) {
					DustParticleEffect crimsonDust = new DustParticleEffect(new Vector3f(1.0f, 0.0f, 0.25f), 1.25f);
					DustParticleEffect magentaDust = new DustParticleEffect(new Vector3f(0.95f, 0.0f, 0.85f), 1.1f);
					DustParticleEffect cyanDust = new DustParticleEffect(EDGERUNNERS_COLORS[1], 1.1f);
					int elapsed = 300 - cyberpsychoRampageTicks;
					double baseAngle = elapsed * 0.35;
					for (int i = 0; i < 3; i++) {
						double angle = baseAngle + i * (2.0 * Math.PI / 3.0);
						double radius = 0.75 + 0.25 * Math.sin(baseAngle * 0.5 + i);
						double px = self.getX() + Math.cos(angle) * radius;
						double pz = self.getZ() + Math.sin(angle) * radius;
						double py = self.getY() + 0.2 + ((elapsed + i * 7) % 24) * 0.08;
						DustParticleEffect dust = i == 0 ? crimsonDust : (i == 1 ? magentaDust : cyanDust);
						sw.spawnParticles(dust, px, py, pz, 1, 0.03, 0.05, 0.03, 0.02);
					}
					if (cyberpsychoRampageTicks % 2 == 0) {
						sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, self.getX(), self.getY() + 0.9, self.getZ(),
								3, 0.45, 0.45, 0.45, 0.08);
					}
					if (cyberpsychoRampageTicks % 4 == 0) {
						sw.spawnParticles(ParticleTypes.SCULK_SOUL, self.getX(), self.getY() + 0.6, self.getZ(),
								2, 0.35, 0.3, 0.35, 0.04);
					}
					if (cyberpsychoRampageTicks % 6 == 0) {
						cyber$spawnRampageLightning(sw, self);
					}
				}

				if (cyberpsychoRampageTicks == 241) {
					if (equipped) {
						sandevistanActiveTicks = Math.max(sandevistanActiveTicks, 180);
						sandevistanCooldownTicks = 0;
						CyberSoundManager.playSandevistanActivate(self);
					}
					if (self.getWorld() instanceof ServerWorld sw) {
						sw.spawnParticles(ParticleTypes.SONIC_BOOM, self.getX(), self.getY() + 1.0, self.getZ(),
								2, 0.2, 0.2, 0.2, 0.0);
						sw.spawnParticles(ParticleTypes.END_ROD, self.getX(), self.getY() + 1.0, self.getZ(),
								28, 0.3, 0.5, 0.3, 0.28);
						sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, self.getX(), self.getY() + 1.0, self.getZ(),
								40, 0.5, 0.6, 0.5, 0.35);

						DustParticleEffect crimsonRing = new DustParticleEffect(new Vector3f(1.0f, 0.0f, 0.25f), 1.6f);
						DustParticleEffect cyanRing = new DustParticleEffect(EDGERUNNERS_COLORS[1], 1.5f);
						DustParticleEffect goldRing = new DustParticleEffect(new Vector3f(0.99f, 0.85f, 0.04f), 1.4f);
						int ringPoints = 32;
						for (int i = 0; i < ringPoints; i++) {
							double angle = (2.0 * Math.PI * i) / ringPoints;
							double rx = Math.cos(angle);
							double rz = Math.sin(angle);
							sw.spawnParticles(i % 2 == 0 ? crimsonRing : cyanRing,
									self.getX() + rx * 1.4, self.getY() + 0.9, self.getZ() + rz * 1.4,
									2, 0.15, 0.15, 0.15, 0.08);
							sw.spawnParticles(goldRing,
									self.getX() + rx * 2.2, self.getY() + 0.3, self.getZ() + rz * 2.2,
									1, 0.1, 0.1, 0.1, 0.05);
							sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
									self.getX() + rx * 1.1, self.getY() + 1.0, self.getZ() + rz * 1.1,
									0, rx, 0.18, rz, 0.45);
						}
						for (int i = 0; i < 4; i++) {
							cyber$spawnRampageLightning(sw, self);
						}
					}
					List<Entity> nearby = self.getWorld().getOtherEntities(self, self.getBoundingBox().expand(5.0),
							e -> e instanceof LivingEntity living && living.isAlive() && !living.isSpectator()
									&& !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())));
					for (Entity entity : nearby) {
						if (self.squaredDistanceTo(entity) <= 25.0) {
							Vec3d push = entity.getPos().subtract(self.getPos());
							Vec3d horiz = new Vec3d(push.x, 0.0, push.z);
							if (horiz.lengthSquared() > 1.0E-4) {
								horiz = horiz.normalize();
							} else {
								horiz = new Vec3d(1.0, 0.0, 0.0);
							}
							entity.addVelocity(horiz.x * 0.55, 0.25, horiz.z * 0.55);
							entity.velocityModified = true;
						}
					}
				}
			} else {
				// Autonomous Cyberpsycho Rampage (cyberpsychoRampageTicks <= 240 && cyberpsychoRampageTicks > 0)
				if (equipped && sandevistanActiveTicks < 20) {
					sandevistanActiveTicks = 80;
				}
				self.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 15, 2, true, false, false));
				self.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 15, 2, true, false, false));
				self.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 15, 1, true, false, false));
				if (cyber$hasImplantInSlot(CyberwareSlotType.NERVOUS, Cyber.KERENZIKOV)) {
					self.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 15, 3, true, false, false));
					self.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 15, 1, true, false, false));
				}

				List<Entity> candidates = self.getWorld().getOtherEntities(self, self.getBoundingBox().expand(20.0),
						e -> e instanceof LivingEntity living && living.isAlive() && !living.isSpectator()
								&& !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())));
				PlayerEntity nearestPlayer = null;
				double nearestPlayerDistSq = Double.MAX_VALUE;
				LivingEntity nearestMob = null;
				double nearestMobDistSq = Double.MAX_VALUE;
				for (Entity candidate : candidates) {
					double distSq = self.squaredDistanceTo(candidate);
					if (candidate instanceof PlayerEntity playerCandidate) {
						if (distSq < nearestPlayerDistSq) {
							nearestPlayerDistSq = distSq;
							nearestPlayer = playerCandidate;
						}
					} else if (candidate instanceof LivingEntity livingCandidate) {
						if (distSq < nearestMobDistSq) {
							nearestMobDistSq = distSq;
							nearestMob = livingCandidate;
						}
					}
				}
				LivingEntity target = nearestPlayer != null ? nearestPlayer : nearestMob;
				if (target != null) {
					Vec3d toTarget = target.getPos().subtract(self.getPos());
					double horizDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
					if (horizDist > 1.8) {
						Vec3d dir = new Vec3d(toTarget.x, 0, toTarget.z).normalize();
						double speed = equipped ? 0.48 : 0.34;
						Vec3d currentVel = self.getVelocity();
						self.setVelocity(dir.x * speed, currentVel.y, dir.z * speed);
						self.velocityModified = true;
						if (tendonsEquipped
								&& (horizDist > 5.0 || target.getY() > self.getY() + 1.2 || self.horizontalCollision)
								&& cyberpsychoRampageTicks % 18 == 0) {
							self.setVelocity(dir.x * 0.75, 0.54, dir.z * 0.75);
							self.velocityModified = true;
							self.fallDistance = 0.0f;
						}
					}
					if (self.squaredDistanceTo(target) <= 4.5 * 4.5 && cyberpsychoRampageTicks % 5 == 0) {
						target.timeUntilRegen = 0;
						self.attack(target);
						self.swingHand(Hand.MAIN_HAND, true);
						if (mantisEquipped && !self.getMainHandStack().isEmpty()) {
							target.damage(self.getDamageSources().playerAttack(self), 9.0f);
							cyber$mantisStrikeFx(self, target);
						}
					}
				}

				if (cyberpsychoRampageTicks % 30 == 0 && self.getHealth() > 2.0f) {
					self.damage(self.getDamageSources().magic(), 1.0f);
				}
				// Rampage heartbeat / glitch audio is driven by CyberSoundManager.tickPlayerSounds.
				if (self.getWorld() instanceof ServerWorld sw) {
					if (cyberpsychoRampageTicks % 3 == 0) {
						sw.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.0f, 0.25f), 1.2f),
								self.getX(), self.getY() + 1.2, self.getZ(), 4, 0.35, 0.45, 0.35, 0.03);
					}
					if (self.getRandom().nextInt(3) == 0) {
						cyber$spawnRampageLightning(sw, self);
					}
				}
			}
			cyberpsychoRampageTicks--;
		} else {
			if (sandevistanActiveTicks > 0) {
				int gain = totalCap > 85 ? 3 : (totalCap > 60 ? 2 : 1);
				cyberpsychosis = Math.min(CyberwareState.MAX_CYBERPSYCHOSIS, cyberpsychosis + gain);
			}
			if (totalCap > CyberwareState.SAFE_CAPACITY_THRESHOLD) {
				int interval = totalCap >= 90 ? 2 : 4;
				if (self.age % interval == 0) {
					cyberpsychosis = Math.min(CyberwareState.MAX_CYBERPSYCHOSIS, cyberpsychosis + 1);
				}
			} else if (sandevistanActiveTicks == 0 && cyberpsychosis > 0) {
				if (self.age % 2 == 0) {
					cyberpsychosis = Math.max(0, cyberpsychosis - 1);
				}
			}
			if (cyberpsychosis >= CyberwareState.MAX_CYBERPSYCHOSIS) {
				cyberpsychoRampageTicks = 300;
				CyberSoundManager.playCyberpsychosisBreak(self);
				if (self.getWorld() instanceof ServerWorld sw) {
					for (int i = 0; i < 4; i++) {
						cyber$spawnRampageLightning(sw, self);
					}
					sw.spawnParticles(ParticleTypes.SCULK_SOUL, self.getX(), self.getY() + 1.0, self.getZ(),
							12, 0.5, 0.6, 0.5, 0.05);
				}
				self.sendMessage(Text.translatable("cyber.msg.cyberpsychosis_triggered"), true);
			}
		}

		boolean cyberpsychoRampage = cyberpsychoRampageTicks > 0;

		CyberSoundManager.tickPlayerSounds(self);
		sandevistanWasActive = sandevistanActiveTicks > 0;

		if (self instanceof ServerPlayerEntity serverPlayer) {
			boolean stateChanged = equipped != lastSyncedEquipped
					|| kiroshiEquipped != lastSyncedKiroshi
					|| tendonsEquipped != lastSyncedTendons
					|| mantisEquipped != lastSyncedMantis
					|| totalCap != lastSyncedTotalCapacity
					|| cyberpsychoRampage != lastSyncedRampage
					|| cyberpsychosis != lastSyncedCyberpsychosis
					|| (sandevistanActiveTicks == 0 && lastSyncedActiveTicks != 0)
					|| (sandevistanCooldownTicks == 0 && lastSyncedCooldownTicks != 0)
					|| (sandevistanActiveTicks > 0 && lastSyncedActiveTicks == 0)
					|| (sandevistanCooldownTicks > 0 && lastSyncedCooldownTicks == 0)
					|| (secondHeartCooldown == 0 && lastSyncedSecondHeartCooldown != 0)
					|| (secondHeartCooldown > 0 && lastSyncedSecondHeartCooldown == 0);
			boolean periodic = (sandevistanActiveTicks > 0 && self.age % 2 == 0)
					|| ((sandevistanCooldownTicks > 0 || secondHeartCooldown > 0) && self.age % 5 == 0)
					|| (cyberpsychosis > 0 && self.age % 4 == 0);

			if (stateChanged || periodic) {
				CyberwareNetworking.syncState(serverPlayer);
				lastSyncedEquipped = equipped;
				lastSyncedActiveTicks = sandevistanActiveTicks;
				lastSyncedCooldownTicks = sandevistanCooldownTicks;
				lastSyncedKiroshi = kiroshiEquipped;
				lastSyncedTendons = tendonsEquipped;
				lastSyncedSecondHeartCooldown = secondHeartCooldown;
				lastSyncedMantis = mantisEquipped;
				lastSyncedTotalCapacity = totalCap;
				lastSyncedRampage = cyberpsychoRampage;
				lastSyncedCyberpsychosis = cyberpsychosis;
			}
		}
	}
}
