package name.cyber_zlo1.sound;

import name.cyber_zlo1.cyberware.CyberwareHolder;
import name.cyber_zlo1.network.CyberwareNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-authoritative Cyberpunk 2077 audio dispatcher.
 * <p>
 * Dispatches synthesized 44.1 kHz OpenAL DSP sound effects to nearby players via
 * {@link CyberwareNetworking#broadcastSound(PlayerEntity, String, float, float, double)},
 * replacing all vanilla Minecraft beacon / bell / note-block placeholder audio with authentic
 * Cyberpunk 2077 sound design and clean non-overlapping loop choreography.
 */
public final class CyberSoundManager {
	public static final double BROADCAST_RANGE = 24.0;

	/** Per-player loop state; weak keys so logged-out / respawned player objects are collected. */
	private static final Map<PlayerEntity, VoiceState> STATES = Collections.synchronizedMap(new WeakHashMap<>());

	private CyberSoundManager() {
	}

	private static final class VoiceState {
		/** Ticks elapsed since the current Sandevistan activation started. */
		int sandevistanLoopTick = 0;
		/** Next loop tick at which a {@code sandevistan_pulse} heartbeat/sub-drone may fire. */
		int nextPulseTick = 28;
		int lastActiveTicks = 0;
		/** Ticks elapsed since cyberpsychosis rampage started (0..59 = entry cutscene, 60+ = rampage). */
		int rampageTick = 0;
	}

	private static VoiceState state(PlayerEntity player) {
		synchronized (STATES) {
			return STATES.computeIfAbsent(player, p -> new VoiceState());
		}
	}

	private static void broadcast(PlayerEntity player, String soundId, float volume, float pitch) {
		if (player == null || player.getWorld().isClient) {
			return;
		}
		CyberwareNetworking.broadcastSound(player, soundId, volume, pitch, BROADCAST_RANGE);
	}

	private static void sendPrivateUi(PlayerEntity player, String soundId, float volume, float pitch) {
		if (player instanceof ServerPlayerEntity serverPlayer) {
			CyberwareNetworking.sendSound(serverPlayer, soundId, volume, pitch);
		}
	}

	/** Reset sequencer state for the player (e.g. when a sequence is interrupted by Immunoblocker). */
	public static void cancelAll(PlayerEntity player) {
		if (player == null) {
			return;
		}
		VoiceState s = STATES.get(player);
		if (s != null) {
			s.sandevistanLoopTick = 0;
			s.nextPulseTick = 28;
			s.rampageTick = 0;
		}
	}

	/**
	 * Advance per-player cyberware audio loops by one server tick.
	 * Called every tick from {@code PlayerEntityMixin#cyber$tick}.
	 */
	public static void tickPlayerSounds(PlayerEntity player) {
		if (player == null || player.getWorld().isClient || !(player instanceof CyberwareHolder holder)) {
			return;
		}
		VoiceState s = state(player);

		int activeTicks = holder.cyber$getSandevistanActiveTicks();
		if (activeTicks > 0) {
			if (s.lastActiveTicks <= 0) {
				s.sandevistanLoopTick = 0;
				s.nextPulseTick = 28;
			}
			playSandevistanActiveLoop(player, activeTicks);
			s.sandevistanLoopTick++;
		} else {
			s.sandevistanLoopTick = 0;
			s.nextPulseTick = 28;
		}
		s.lastActiveTicks = activeTicks;

		if (holder.cyber$isCyberpsychoRampage()) {
			if (s.rampageTick >= 60) {
				playRampagePulse(player, s.rampageTick - 60);
			}
			s.rampageTick++;
		} else {
			s.rampageTick = 0;
		}
	}

	// ------------------------------------------------------------------------------------------------
	// Sandevistan
	// ------------------------------------------------------------------------------------------------

	/** Iconic Cyberpunk 2077 / Edgerunners Sandevistan activation time-drop (1.65s). */
	public static void playSandevistanActivate(PlayerEntity player) {
		if (player == null) {
			return;
		}
		VoiceState s = state(player);
		s.sandevistanLoopTick = 0;
		s.nextPulseTick = 28;
		broadcast(player, "sandevistan_activate", 1.0f, 1.0f);
	}

	/**
	 * Called every tick while Sandevistan is active (from {@link #tickPlayerSounds}).
	 * Schedules clean, non-overlapping underwater heartbeat pulses ({@code sandevistan_pulse}, 1.10s)
	 * after the activation drop settles, plus crisp Militech telemetry warnings ({@code sandevistan_warn}, 0.32s)
	 * during the final 1.5 seconds.
	 */
	public static void playSandevistanActiveLoop(PlayerEntity player, int activeTicksRemaining) {
		if (player == null || player.getWorld().isClient) {
			return;
		}
		VoiceState s = state(player);
		int t = s.sandevistanLoopTick;

		// Allow the 1.65s activation sweep to breathe before starting the 1.10s underwater pulse,
		// and stop pulsing 16 ticks before deactivation so the snap-back lands cleanly.
		if (t >= s.nextPulseTick && activeTicksRemaining > 16) {
			boolean nearExpiry = activeTicksRemaining <= 50;
			float pitch = nearExpiry ? 1.08f : 1.0f;
			float volume = nearExpiry ? 0.88f : 0.82f;
			broadcast(player, "sandevistan_pulse", volume, pitch);
			s.nextPulseTick = t + (nearExpiry ? 19 : 22);
		}

		// Militech dual-beep expiry telemetry alert (skip during cyberpsycho rampage auto-refill).
		boolean isRampaging = player instanceof CyberwareHolder holder && holder.cyber$isCyberpsychoRampage();
		if (!isRampaging && activeTicksRemaining <= 30 && activeTicksRemaining > 0 && activeTicksRemaining % 10 == 0) {
			float urgency = (30 - activeTicksRemaining) / 20.0f; // 0.0 -> 0.5 -> 1.0
			float pitch = 0.98f + urgency * 0.12f;
			float volume = 0.78f + urgency * 0.12f;
			broadcast(player, "sandevistan_warn", volume, pitch);
		}
	}

	/** Sandevistan reverse speedup wind-down, pneumatic hiss, and relay snap-back (1.05s). */
	public static void playSandevistanDeactivate(PlayerEntity player) {
		if (player == null) {
			return;
		}
		VoiceState s = state(player);
		s.sandevistanLoopTick = 0;
		s.nextPulseTick = 28;
		broadcast(player, "sandevistan_deactivate", 0.95f, 1.0f);
	}

	// ------------------------------------------------------------------------------------------------
	// Combat / mobility implants
	// ------------------------------------------------------------------------------------------------

	/** 3-hit Mantis Blades combo cleave ({@code mantis_slash_1 / 2 / 3}) with subtle organic pitch jitter. */
	public static void playMantisSlash(PlayerEntity player, int comboStep) {
		if (player == null) {
			return;
		}
		int step = Math.floorMod(comboStep, 3);
		String soundId = switch (step) {
			case 0 -> "mantis_slash_1";
			case 1 -> "mantis_slash_2";
			default -> "mantis_slash_3";
		};
		float jitter = 0.97f + player.getRandom().nextFloat() * 0.06f;
		float volume = step == 2 ? 0.98f : 0.88f;
		broadcast(player, soundId, volume, jitter);
	}

	/** Reinforced Tendons spring-servo snap + compressed-air thrust thump. */
	public static void playDoubleJumpJet(PlayerEntity player) {
		if (player == null) {
			return;
		}
		float jitter = 0.97f + player.getRandom().nextFloat() * 0.06f;
		broadcast(player, "double_jump_jet", 0.90f, jitter);
	}

	/** Kerenzikov synaptic reflex time-zip. */
	public static void playKerenzikovSurge(PlayerEntity player) {
		broadcast(player, "kerenzikov_surge", 0.92f, 1.0f);
	}

	/** Second Heart capacitor charge whine -> thunderous cardiac shock -> restarted heartbeat. */
	public static void playSecondHeartDefib(PlayerEntity player) {
		broadcast(player, "second_heart_defib", 1.0f, 1.0f);
	}

	/**
	 * 3.00-second (60-tick) Cyberpsychosis Entry Cutscene neural collapse soundscape:
	 * Relic Malfunction tinnitus, brain-throb bass, bitcrushed static tears, rising horror swell,
	 * and shattering sub-bass distortion boom at 2.75s.
	 */
	public static void playCyberpsychosisBreak(PlayerEntity player) {
		if (player == null) {
			return;
		}
		VoiceState s = state(player);
		s.rampageTick = 0;
		broadcast(player, "cyberpsychosis_break", 1.0f, 1.0f);
	}

	/** Recurring rampage audio (called from {@link #tickPlayerSounds} once the 60-tick cutscene ends). */
	public static void playRampagePulse(PlayerEntity player, int rampageTick) {
		if (player == null || player.getWorld().isClient) {
			return;
		}
		// Periodic Relic Malfunction static tear during autonomous rampage.
		if (rampageTick % 17 == 6) {
			float pitch = 0.86f + player.getRandom().nextFloat() * 0.34f;
			broadcast(player, "cyberpsychosis_glitch", 0.62f, pitch);
		}
		// Frantic heartbeat pulse when Sandevistan isn't already driving its own pulse.
		boolean sandevistanActive = player instanceof CyberwareHolder holder
				&& holder.cyber$getSandevistanActiveTicks() > 0;
		if (!sandevistanActive && rampageTick % 22 == 0) {
			broadcast(player, "sandevistan_pulse", 0.78f, 1.28f);
		}
	}

	/** Pneumatic autoinjector latch click + serum hiss + warm resolving synth pad. */
	public static void playImmunoblockerInject(PlayerEntity player) {
		broadcast(player, "immunoblocker_inject", 0.95f, 1.0f);
	}

	// ------------------------------------------------------------------------------------------------
	// UI helpers (preserved for API compatibility; client screens call CyberOpenALSynth directly)
	// ------------------------------------------------------------------------------------------------

	/** Ultra-subtle Ripperdoc UI hover micro-tick. */
	public static void playUiHover(PlayerEntity player) {
		sendPrivateUi(player, "ui_hover", 0.70f, 1.0f);
	}

	/** Crisp cyberdeck UI confirmation beep. */
	public static void playUiClick(PlayerEntity player) {
		sendPrivateUi(player, "ui_click", 0.80f, 1.0f);
	}

	/** Surgical servo lock-in chord when installing an implant. */
	public static void playUiInstall(PlayerEntity player) {
		sendPrivateUi(player, "ui_install", 0.90f, 1.0f);
	}

	/** Pneumatic unlatch tone when removing an implant. */
	public static void playUiUninstall(PlayerEntity player) {
		sendPrivateUi(player, "ui_uninstall", 0.80f, 1.0f);
	}

	/** Harsh double-buzz error when capacity limit blocks an implant. */
	public static void playUiError(PlayerEntity player) {
		sendPrivateUi(player, "ui_error", 0.90f, 1.0f);
	}

	/** Ripperdoc interface neural link handshake. */
	public static void playUiOpen(PlayerEntity player) {
		sendPrivateUi(player, "ui_open", 0.75f, 1.0f);
	}
}
