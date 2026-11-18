package name.cyber_zlo1.network;

import name.cyber_zlo1.Cyber;
import name.cyber_zlo1.cyberware.CyberwareHolder;
import name.cyber_zlo1.cyberware.CyberwareState;
import name.cyber_zlo1.screen.CyberwareScreenHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
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
			int secondHeartCooldown
	) implements CustomPayload {
		public static final CustomPayload.Id<SyncCyberwareStatePayload> ID = new CustomPayload.Id<>(Cyber.id("sync_cyberware_state"));
		public static final PacketCodec<RegistryByteBuf, SyncCyberwareStatePayload> CODEC = PacketCodec.tuple(
				PacketCodecs.VAR_INT, SyncCyberwareStatePayload::activeTicks,
				PacketCodecs.VAR_INT, SyncCyberwareStatePayload::cooldownTicks,
				PacketCodecs.BOOL, SyncCyberwareStatePayload::equipped,
				PacketCodecs.BOOL, SyncCyberwareStatePayload::kiroshiEquipped,
				PacketCodecs.BOOL, SyncCyberwareStatePayload::reinforcedTendonsEquipped,
				PacketCodecs.VAR_INT, SyncCyberwareStatePayload::secondHeartCooldown,
				SyncCyberwareStatePayload::new
		);

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
					player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.8f);
					player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 0.9f, 1.6f);
					player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.PLAYERS, 0.7f, 1.8f);
					player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 0.9f, 1.2f);
					if (player.getWorld() instanceof ServerWorld serverWorld) {
						DustParticleEffect emeraldDust = new DustParticleEffect(new Vector3f(0.0f, 1.0f, 0.64f), 1.3f);
						DustParticleEffect cyanDust = new DustParticleEffect(new Vector3f(0.0f, 0.94f, 1.0f), 1.3f);
						int shockwavePoints = 24;
						for (int i = 0; i < shockwavePoints; i++) {
							double angle = (2.0 * Math.PI * i) / shockwavePoints;
							double px = player.getX() + Math.cos(angle) * 0.95;
							double py = player.getY() + 1.0;
							double pz = player.getZ() + Math.sin(angle) * 0.95;
							serverWorld.spawnParticles(i % 2 == 0 ? emeraldDust : cyanDust, px, py, pz, 1, 0.05, 0.1, 0.05, 0.08);
							serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 2, 0.05, 0.15, 0.05, 0.15);
						}
						serverWorld.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(),
								18, 0.3, 0.5, 0.3, 0.14);
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
					player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.PLAYERS, 0.6f, 1.8f);
					if (player.getWorld() instanceof ServerWorld serverWorld) {
						DustParticleEffect cyanDust = new DustParticleEffect(new Vector3f(0.0f, 0.94f, 1.0f), 1.1f);
						DustParticleEffect yellowDust = new DustParticleEffect(new Vector3f(0.99f, 0.93f, 0.04f), 1.1f);
						int ringPoints = 16;
						for (int i = 0; i < ringPoints; i++) {
							double angle = (2.0 * Math.PI * i) / ringPoints;
							double rx = player.getX() + Math.cos(angle) * 0.75;
							double rz = player.getZ() + Math.sin(angle) * 0.75;
							serverWorld.spawnParticles(i % 2 == 0 ? cyanDust : yellowDust, rx, player.getY() + 0.08, rz,
									1, 0.0, 0.02, 0.0, 0.02);
							serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, rx, player.getY() + 0.08, rz,
									1, 0.02, 0.05, 0.02, 0.06);
						}
						serverWorld.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.05, player.getZ(),
								8, 0.35, 0.05, 0.35, 0.04);
					}
				}
			});
		});
	}

	public static void syncState(ServerPlayerEntity player) {
		CyberwareHolder holder = (CyberwareHolder) player;
		ServerPlayNetworking.send(player, new SyncCyberwareStatePayload(
				holder.cyber$getSandevistanActiveTicks(),
				holder.cyber$getSandevistanCooldownTicks(),
				holder.cyber$hasSandevistanEquipped(),
				holder.cyber$hasKiroshiEquipped(),
				holder.cyber$hasReinforcedTendonsEquipped(),
				holder.cyber$getSecondHeartCooldown()
		));
	}
}
