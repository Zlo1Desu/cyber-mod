package name.cyber_zlo1.network;

import name.cyber_zlo1.Cyber;
import name.cyber_zlo1.cyberware.CyberwareHolder;
import name.cyber_zlo1.cyberware.CyberwareState;
import name.cyber_zlo1.screen.CyberwareScreenHandler;
import name.cyber_zlo1.sound.CyberSoundManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

public final class CyberwareNetworking {
	private CyberwareNetworking() {
	}

	public record OpenCyberwarePayload() implements CustomPayload {
		public static final CustomPayload.Id<OpenCyberwarePayload> ID = new CustomPayload.Id<>(Cyber.id("open_cyberware"));
		public static final OpenCyberwarePayload INSTANCE = new OpenCyberwarePayload();
		public static final PacketCodec<RegistryByteBuf, OpenCyberwarePayload> CODEC = PacketCodec.unit(INSTANCE);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record ActivateSandevistanPayload() implements CustomPayload {
		public static final CustomPayload.Id<ActivateSandevistanPayload> ID = new CustomPayload.Id<>(Cyber.id("activate_sandevistan"));
		public static final ActivateSandevistanPayload INSTANCE = new ActivateSandevistanPayload();
		public static final PacketCodec<RegistryByteBuf, ActivateSandevistanPayload> CODEC = PacketCodec.unit(INSTANCE);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record DoubleJumpPayload() implements CustomPayload {
		public static final CustomPayload.Id<DoubleJumpPayload> ID = new CustomPayload.Id<>(Cyber.id("double_jump"));
		public static final DoubleJumpPayload INSTANCE = new DoubleJumpPayload();
		public static final PacketCodec<RegistryByteBuf, DoubleJumpPayload> CODEC = PacketCodec.unit(INSTANCE);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record SyncCyberwareStatePayload(
			int activeTicks,
			int cooldownTicks,
			boolean equipped,
			boolean kiroshiEquipped,
			boolean reinforcedTendonsEquipped,
			int secondHeartCooldown,
			boolean mantisBladesEquipped,
			int cyberpsychosis,
			boolean cyberpsychoRampage,
			int totalCapacity
	) implements CustomPayload {
		public static final CustomPayload.Id<SyncCyberwareStatePayload> ID = new CustomPayload.Id<>(Cyber.id("sync_cyberware_state"));
		public static final PacketCodec<RegistryByteBuf, SyncCyberwareStatePayload> CODEC =
				PacketCodec.of(SyncCyberwareStatePayload::write, SyncCyberwareStatePayload::read);

		public void write(RegistryByteBuf buf) {
			buf.writeVarInt(activeTicks);
			buf.writeVarInt(cooldownTicks);
			buf.writeBoolean(equipped);
			buf.writeBoolean(kiroshiEquipped);
			buf.writeBoolean(reinforcedTendonsEquipped);
			buf.writeVarInt(secondHeartCooldown);
			buf.writeBoolean(mantisBladesEquipped);
			buf.writeVarInt(cyberpsychosis);
			buf.writeBoolean(cyberpsychoRampage);
			buf.writeVarInt(totalCapacity);
		}

		public static SyncCyberwareStatePayload read(RegistryByteBuf buf) {
			return new SyncCyberwareStatePayload(
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readBoolean(),
					buf.readBoolean(),
					buf.readBoolean(),
					buf.readVarInt(),
					buf.readBoolean(),
					buf.readVarInt(),
					buf.readBoolean(),
					buf.readVarInt()
			);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public static void registerCommon() {
		PayloadTypeRegistry.playC2S().register(OpenCyberwarePayload.ID, OpenCyberwarePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ActivateSandevistanPayload.ID, ActivateSandevistanPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DoubleJumpPayload.ID, DoubleJumpPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SyncCyberwareStatePayload.ID, SyncCyberwareStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CyberSoundPayload.ID, CyberSoundPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(OpenCyberwarePayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> {
				player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
						(syncId, inv, p) -> new CyberwareScreenHandler(syncId, inv, ((CyberwareHolder) p).cyber$getCyberwareInventory()),
						Text.translatable("cyber.ui.title")
				));
				syncState(player);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(ActivateSandevistanPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> {
				CyberwareHolder holder = (CyberwareHolder) player;
				if (holder.cyber$hasSandevistanEquipped()
						&& holder.cyber$getSandevistanActiveTicks() == 0
						&& holder.cyber$getSandevistanCooldownTicks() == 0) {
					holder.cyber$setSandevistanActiveTicks(CyberwareState.MAX_ACTIVE_TICKS);
					CyberSoundManager.playSandevistanActivate(player);
					if (player.getWorld() instanceof ServerWorld serverWorld) {
						spawnSandevistanActivationBurst(serverWorld, player);
					}
					syncState(player);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(DoubleJumpPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> {
				if (((CyberwareHolder) player).cyber$hasReinforcedTendonsEquipped() && !player.isOnGround()) {
					player.setVelocity(player.getVelocity().x * 1.15, 0.56, player.getVelocity().z * 1.15);
					player.velocityModified = true;
					player.fallDistance = 0.0f;
					CyberSoundManager.playDoubleJumpJet(player);
					if (player.getWorld() instanceof ServerWorld serverWorld) {
						spawnDoubleJumpJet(serverWorld, player);
					}
				}
			});
		});
	}

	/**
	 * Tick-0 Sandevistan activation burst: a 3D spherical spray of emerald / cyan / yellow dust, electric sparks,
	 * END_ROD streaks and a FLASH core. The expanding double-ring shockwave over the next ticks is choreographed
	 * by {@code PlayerEntityMixin} (rising-edge detection), so it also plays for rampage-forced activations.
	 */
	public static void spawnSandevistanActivationBurst(ServerWorld serverWorld, PlayerEntity player) {
		double cx = player.getX();
		double cy = player.getY() + 1.0;
		double cz = player.getZ();
		DustParticleEffect emeraldDust = new DustParticleEffect(new Vector3f(0.0f, 1.0f, 0.64f), 1.3f);
		DustParticleEffect cyanDust = new DustParticleEffect(new Vector3f(0.0f, 0.94f, 1.0f), 1.3f);
		DustParticleEffect yellowDust = new DustParticleEffect(new Vector3f(0.99f, 0.93f, 0.04f), 1.1f);
		DustParticleEffect[] palette = {emeraldDust, cyanDust, yellowDust};

		serverWorld.spawnParticles(ParticleTypes.FLASH, cx, cy, cz, 1, 0.0, 0.0, 0.0, 0.0);

		// Fibonacci sphere: evenly distributed outward rays.
		int rays = 42;
		double golden = Math.PI * (3.0 - Math.sqrt(5.0));
		for (int i = 0; i < rays; i++) {
			double y = 1.0 - (i / (double) (rays - 1)) * 2.0;
			double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
			double theta = golden * i;
			double dx = Math.cos(theta) * r;
			double dz = Math.sin(theta) * r;
			double px = cx + dx * 0.9;
			double py = cy + y * 0.9;
			double pz = cz + dz * 0.9;
			serverWorld.spawnParticles(palette[i % 3], px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
			if (i % 2 == 0) {
				// count = 0 -> offsets are used as a velocity direction, giving a true outward streak.
				serverWorld.spawnParticles(ParticleTypes.END_ROD, px, py, pz, 0, dx, y, dz, 0.22);
			} else {
				serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 0, dx, y, dz, 0.45);
			}
		}
		serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, cx, player.getY() + 0.1, cz, 10, 0.4, 0.02, 0.4, 0.02);
	}

	/** Hydraulic jet: downward thrust cone of cloud + sparks and a cyan / yellow pressure ring. */
	public static void spawnDoubleJumpJet(ServerWorld serverWorld, PlayerEntity player) {
		DustParticleEffect cyanDust = new DustParticleEffect(new Vector3f(0.0f, 0.94f, 1.0f), 1.1f);
		DustParticleEffect yellowDust = new DustParticleEffect(new Vector3f(0.99f, 0.93f, 0.04f), 1.1f);
		int ringPoints = 16;
		for (int i = 0; i < ringPoints; i++) {
			double angle = (2.0 * Math.PI * i) / ringPoints;
			double ox = Math.cos(angle);
			double oz = Math.sin(angle);
			double rx = player.getX() + ox * 0.75;
			double rz = player.getZ() + oz * 0.75;
			serverWorld.spawnParticles(i % 2 == 0 ? cyanDust : yellowDust, rx, player.getY() + 0.08, rz,
					1, 0.0, 0.02, 0.0, 0.02);
			serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, rx, player.getY() + 0.08, rz,
					0, ox, -0.2, oz, 0.18);
		}
		// Downward thrust cone from each foot.
		for (int i = 0; i < 8; i++) {
			double angle = (2.0 * Math.PI * i) / 8;
			double ox = Math.cos(angle) * 0.25;
			double oz = Math.sin(angle) * 0.25;
			serverWorld.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(),
					0, ox, -1.0, oz, 0.25);
		}
		serverWorld.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.05, player.getZ(),
				6, 0.3, 0.05, 0.3, 0.03);
	}

	public static void syncState(ServerPlayerEntity player) {
		CyberwareHolder holder = (CyberwareHolder) player;
		ServerPlayNetworking.send(player, new SyncCyberwareStatePayload(
				holder.cyber$getSandevistanActiveTicks(),
				holder.cyber$getSandevistanCooldownTicks(),
				holder.cyber$hasSandevistanEquipped(),
				holder.cyber$hasKiroshiEquipped(),
				holder.cyber$hasReinforcedTendonsEquipped(),
				holder.cyber$getSecondHeartCooldown(),
				holder.cyber$hasMantisBladesEquipped(),
				holder.cyber$getCyberpsychosis(),
				holder.cyber$isCyberpsychoRampage(),
				holder.cyber$getTotalEquippedCapacity()
		));
	}

	/**
	 * Send a synthesized Cyberpunk 2077 OpenAL sound effect to a single player.
	 */
	public static void sendSound(ServerPlayerEntity player, String soundId, float volume, float pitch) {
		if (player == null || soundId == null || soundId.isEmpty() || volume <= 0.001f) {
			return;
		}
		float clampedVolume = MathHelper.clamp(volume, 0.0f, 2.0f);
		float clampedPitch = MathHelper.clamp(pitch, 0.25f, 4.0f);
		ServerPlayNetworking.send(player, new CyberSoundPayload(soundId, clampedVolume, clampedPitch));
	}

	/**
	 * Broadcast a synthesized Cyberpunk 2077 OpenAL sound effect to every {@link ServerPlayerEntity}
	 * within {@code range} blocks of {@code source}, attenuating volume linearly by distance.
	 */
	public static void broadcastSound(PlayerEntity source, String soundId, float volume, float pitch, double range) {
		if (source == null || soundId == null || soundId.isEmpty() || volume <= 0.001f || range <= 0.0) {
			return;
		}
		if (!(source.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		double rangeSq = range * range;
		for (ServerPlayerEntity listener : serverWorld.getPlayers()) {
			double distSq = listener.squaredDistanceTo(source);
			if (distSq > rangeSq) {
				continue;
			}
			double dist = Math.sqrt(distSq);
			float attenuation = (float) Math.max(0.0, 1.0 - (dist / range));
			float attenuatedVolume = volume * attenuation;
			if (attenuatedVolume > 0.005f) {
				sendSound(listener, soundId, attenuatedVolume, pitch);
			}
		}
	}
}
