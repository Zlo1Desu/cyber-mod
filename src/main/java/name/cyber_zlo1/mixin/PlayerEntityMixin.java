package name.cyber_zlo1.mixin;

import name.cyber_zlo1.Cyber;
import name.cyber_zlo1.cyberware.CyberwareHolder;
import name.cyber_zlo1.cyberware.CyberwareSlotType;
import name.cyber_zlo1.cyberware.CyberwareState;
import name.cyber_zlo1.cyberware.SandevistanItem;
import name.cyber_zlo1.network.CyberwareNetworking;
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
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
			self.getWorld().playSound(null, self.getX(), self.getY(), self.getZ(),
					SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.7f, 1.4f);
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
			if (self.getWorld() instanceof ServerWorld serverWorld) {
				Vec3d look = self.getRotationVec(1.0f);
				Vec3d horizontalRight = new Vec3d(-look.z, 0.0, look.x);
				Vec3d right = horizontalRight.lengthSquared() > 1.0E-4 ? horizontalRight.normalize() : new Vec3d(1.0, 0.0, 0.0);
				Vec3d center = new Vec3d(self.getX(), self.getY() + 1.1, self.getZ()).add(look.multiply(1.2));
				DustParticleEffect crimsonDust = new DustParticleEffect(new Vector3f(1.0f, 0.0f, 0.24f), 1.25f);
				DustParticleEffect cyanDust = new DustParticleEffect(new Vector3f(0.0f, 0.94f, 1.0f), 1.25f);
				for (int i = -4; i <= 4; i++) {
					double t = i * 0.2;
					double yOffset = i * 0.13;
					Vec3d p1 = center.add(right.multiply(t)).add(0.0, yOffset, 0.0);
					Vec3d p2 = center.add(right.multiply(t)).add(0.0, -yOffset, 0.0);
					serverWorld.spawnParticles(crimsonDust, p1.x, p1.y, p1.z, 1, 0.02, 0.02, 0.02, 0.01);
					serverWorld.spawnParticles(cyanDust, p2.x, p2.y, p2.z, 1, 0.02, 0.02, 0.02, 0.01);
				}
				serverWorld.spawnParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 2, 0.15, 0.1, 0.15, 0.0);
				serverWorld.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
						12, 0.25, 0.25, 0.25, 0.15);
			}
			self.getWorld().playSound(null, self.getX(), self.getY(), self.getZ(),
					SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 1.5f);
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

	@Inject(method = "tick", at = @At("TAIL"))
	private void cyber$tick(CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (self.getWorld().isClient) {
			return;
		}

		boolean equipped = cyber$hasSandevistanEquipped();
		if (!equipped && sandevistanActiveTicks > 0) {
			sandevistanActiveTicks = 0;
		}

		if (sandevistanActiveTicks > 0) {
			self.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10, 3, true, false, false));
			self.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 10, 2, true, false, false));
			self.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 10, 1, true, false, false));

			if (self.getWorld() instanceof ServerWorld serverWorld) {
				double angle = sandevistanActiveTicks * 0.45;
				double radius = 0.75;
				double heightOffset = ((sandevistanActiveTicks % 20) / 20.0) * 1.8;
				Vector3f colorA = EDGERUNNERS_COLORS[(sandevistanActiveTicks % 8) / 2];
				Vector3f colorB = EDGERUNNERS_COLORS[((sandevistanActiveTicks + 4) % 8) / 2];
				DustParticleEffect dustA = new DustParticleEffect(colorA, 1.15f);
				DustParticleEffect dustB = new DustParticleEffect(colorB, 1.15f);

				double x1 = self.getX() + Math.cos(angle) * radius;
				double z1 = self.getZ() + Math.sin(angle) * radius;
				double x2 = self.getX() + Math.cos(angle + Math.PI) * radius;
				double z2 = self.getZ() + Math.sin(angle + Math.PI) * radius;

				serverWorld.spawnParticles(dustA, x1, self.getY() + 0.15 + heightOffset, z1, 2, 0.03, 0.05, 0.03, 0.01);
				serverWorld.spawnParticles(dustB, x2, self.getY() + 1.95 - heightOffset, z2, 2, 0.03, 0.05, 0.03, 0.01);
				serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x1, self.getY() + 0.15 + heightOffset, z1, 1, 0.02, 0.02, 0.02, 0.03);
				serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x2, self.getY() + 1.95 - heightOffset, z2, 1, 0.02, 0.02, 0.02, 0.03);
			}

			if (sandevistanActiveTicks % 16 == 0) {
				self.getWorld().playSound(null, self.getX(), self.getY(), self.getZ(),
						SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 0.8f, 1.1f);
				self.getWorld().playSound(null, self.getX(), self.getY(), self.getZ(),
						SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.4f, 0.55f);
			}

			sandevistanActiveTicks--;
			if (sandevistanActiveTicks == 0) {
				sandevistanCooldownTicks = CyberwareState.MAX_COOLDOWN_TICKS;
				self.getWorld().playSound(null, self.getX(), self.getY(), self.getZ(),
						SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.9f, 0.75f);
				self.getWorld().playSound(null, self.getX(), self.getY(), self.getZ(),
						SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.6f, 0.55f);
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
			self.getWorld().playSound(null, self.getX(), self.getY(), self.getZ(),
					SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 0.8f, 1.5f);
			kerenzikovCooldownTicks = 160;
		}

		cyber$tryTriggerSecondHeart(self);

		if (self instanceof ServerPlayerEntity serverPlayer) {
			boolean stateChanged = equipped != lastSyncedEquipped
					|| kiroshiEquipped != lastSyncedKiroshi
					|| tendonsEquipped != lastSyncedTendons
					|| (sandevistanActiveTicks == 0 && lastSyncedActiveTicks != 0)
					|| (sandevistanCooldownTicks == 0 && lastSyncedCooldownTicks != 0)
					|| (sandevistanActiveTicks > 0 && lastSyncedActiveTicks == 0)
					|| (sandevistanCooldownTicks > 0 && lastSyncedCooldownTicks == 0)
					|| (secondHeartCooldown == 0 && lastSyncedSecondHeartCooldown != 0)
					|| (secondHeartCooldown > 0 && lastSyncedSecondHeartCooldown == 0);
			boolean periodic = (sandevistanActiveTicks > 0 && self.age % 2 == 0)
					|| ((sandevistanCooldownTicks > 0 || secondHeartCooldown > 0) && self.age % 5 == 0);

			if (stateChanged || periodic) {
				CyberwareNetworking.syncState(serverPlayer);
				lastSyncedEquipped = equipped;
				lastSyncedActiveTicks = sandevistanActiveTicks;
				lastSyncedCooldownTicks = sandevistanCooldownTicks;
				lastSyncedKiroshi = kiroshiEquipped;
				lastSyncedTendons = tendonsEquipped;
				lastSyncedSecondHeartCooldown = secondHeartCooldown;
			}
		}
	}
}
