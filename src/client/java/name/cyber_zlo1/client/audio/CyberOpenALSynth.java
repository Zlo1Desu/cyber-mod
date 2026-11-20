package name.cyber_zlo1.client.audio;

import name.cyber_zlo1.Cyber;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.system.MemoryUtil;

import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure-Java 44.1 kHz 16-bit mono PCM OpenAL Digital Signal Processing (DSP) synthesizer
 * producing authentic Cyberpunk 2077 / Edgerunners audio directly inside Minecraft's live OpenAL context.
 * <p>
 * Features:
 * <ul>
 *     <li>Lazy initialization guarded by {@code ALC10.alcGetCurrentContext() != 0L}, with automatic
 *     context-change recovery (e.g. F3+T resource reloads or audio device changes).</li>
 *     <li>16 reusable head-relative OpenAL sources ({@code AL_SOURCE_RELATIVE = AL_TRUE}) with
 *     smart voice stealing and mutual-exclusion rules so contrasting SFX never collide messily.</li>
 *     <li>Real DSP primitives: phase-continuous oscillators, PolyBLEP anti-aliased saws/squares,
 *     2nd-order Audio EQ Cookbook resonant biquad filters, inharmonic metallic FM synthesis,
 *     bitcrushing / sample-and-hold decimation, tanh analog saturation, DC blocking, and
 *     Schroeder comb+allpass reverb tails.</li>
 *     <li>Live Minecraft MASTER * PLAYERS volume scaling and comprehensive {@code Throwable} guards
 *     so audio synthesis can never crash the client in headless or muted environments.</li>
 * </ul>
 */
public final class CyberOpenALSynth {
	private static final int SAMPLE_RATE = 44100;
	private static final double TWO_PI = Math.PI * 2.0;
	private static final int SOURCE_POOL_SIZE = 16;

	private static final Object LOCK = new Object();

	private static boolean initialized = false;
	private static long initializedContext = 0L;
	private static final Map<String, Integer> BUFFERS = new HashMap<>();
	private static final int[] SOURCES = new int[SOURCE_POOL_SIZE];
	private static final String[] SOURCE_SOUND_IDS = new String[SOURCE_POOL_SIZE];
	private static final long[] SOURCE_START_NANOS = new long[SOURCE_POOL_SIZE];

	private CyberOpenALSynth() {
	}

	// ================================================================================================
	// Public API
	// ================================================================================================

	/** Play a synthesized Cyberpunk 2077 sound at default volume (1.0) and pitch (1.0). */
	public static void play(String soundId) {
		play(soundId, 1.0f, 1.0f);
	}

	/**
	 * Play a synthesized Cyberpunk 2077 sound effect through OpenAL.
	 * Respects Minecraft's live MASTER and PLAYERS volume sliders and safely no-ops if OpenAL is unavailable.
	 */
	public static void play(String soundId, float volume, float pitch) {
		if (soundId == null || soundId.isEmpty() || volume <= 0.001f) {
			return;
		}
		try {
			float gain = computeMasterGain(volume);
			if (gain <= 0.001f) {
				return;
			}
			synchronized (LOCK) {
				if (!ensureInitializedLocked()) {
					return;
				}
				Integer bufferId = BUFFERS.get(soundId);
				if (bufferId == null || bufferId == 0) {
					return;
				}

				applyMutualExclusionLocked(soundId);

				int slot = acquireSourceSlotLocked();
				if (slot < 0) {
					return;
				}
				int src = SOURCES[slot];
				float safePitch = Math.max(0.25f, Math.min(4.0f, Float.isFinite(pitch) ? pitch : 1.0f));

				AL10.alSourceStop(src);
				AL10.alSourcei(src, AL10.AL_BUFFER, bufferId);
				AL10.alSourcef(src, AL10.AL_GAIN, gain);
				AL10.alSourcef(src, AL10.AL_PITCH, safePitch);
				AL10.alSourcei(src, AL10.AL_LOOPING, AL10.AL_FALSE);
				AL10.alSourcePlay(src);

				SOURCE_SOUND_IDS[slot] = soundId;
				SOURCE_START_NANOS[slot] = System.nanoTime();
			}
		} catch (Throwable t) {
			Cyber.LOGGER.debug("CyberOpenALSynth.play('{}') failed safely: {}", soundId, t.toString());
		}
	}

	/** Stop every currently playing instance of the given synthesized sound ID. */
	public static void stopSound(String soundId) {
		if (soundId == null || soundId.isEmpty()) {
			return;
		}
		try {
			synchronized (LOCK) {
				if (!initialized) {
					return;
				}
				long ctx = ALC10.alcGetCurrentContext();
				if (ctx == 0L || ctx != initializedContext) {
					return;
				}
				stopSoundLocked(soundId);
			}
		} catch (Throwable t) {
			Cyber.LOGGER.debug("CyberOpenALSynth.stopSound('{}') failed safely: {}", soundId, t.toString());
		}
	}

	/** Delete all OpenAL sources and synthesized PCM buffers if the owning OpenAL context is still active. */
	public static void cleanup() {
		try {
			synchronized (LOCK) {
				if (!initialized) {
					return;
				}
				long ctx = ALC10.alcGetCurrentContext();
				if (ctx != 0L && ctx == initializedContext) {
					for (int i = 0; i < SOURCE_POOL_SIZE; i++) {
						int src = SOURCES[i];
						if (src != 0) {
							AL10.alSourceStop(src);
							AL10.alSourcei(src, AL10.AL_BUFFER, 0);
							AL10.alDeleteSources(src);
						}
					}
					for (int buf : BUFFERS.values()) {
						if (buf != 0) {
							AL10.alDeleteBuffers(buf);
						}
					}
				}
				clearTrackingStateLocked();
			}
		} catch (Throwable t) {
			Cyber.LOGGER.debug("CyberOpenALSynth.cleanup() failed safely: {}", t.toString());
			synchronized (LOCK) {
				clearTrackingStateLocked();
			}
		}
	}

	// ================================================================================================
	// OpenAL lifecycle & source pool management
	// ================================================================================================

	private static float computeMasterGain(float volume) {
		if (!Float.isFinite(volume) || volume <= 0.0f) {
			return 0.0f;
		}
		try {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || client.options == null) {
				return Math.min(volume, 2.0f);
			}
			float master = client.options.getSoundVolume(SoundCategory.MASTER);
			float players = client.options.getSoundVolume(SoundCategory.PLAYERS);
			float gain = volume * master * players;
			return Math.max(0.0f, Math.min(2.0f, gain));
		} catch (Throwable t) {
			return Math.max(0.0f, Math.min(2.0f, volume));
		}
	}

	private static boolean ensureInitializedLocked() {
		long ctx = ALC10.alcGetCurrentContext();
		if (ctx == 0L) {
			return false;
		}
		if (initialized && initializedContext == ctx) {
			return true;
		}
		if (initialized && initializedContext != ctx) {
			// Minecraft recreated its OpenAL context (e.g. F3+T reload or device change); old handles are dead.
			clearTrackingStateLocked();
		}

		// Allocate reusable head-centered OpenAL source pool.
		for (int i = 0; i < SOURCE_POOL_SIZE; i++) {
			int src = AL10.alGenSources();
			if (src == 0) {
				for (int j = 0; j < i; j++) {
					if (SOURCES[j] != 0) {
						AL10.alDeleteSources(SOURCES[j]);
					}
				}
				clearTrackingStateLocked();
				return false;
			}
			AL10.alSourcei(src, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
			AL10.alSource3f(src, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f);
			AL10.alSourcei(src, AL10.AL_LOOPING, AL10.AL_FALSE);
			SOURCES[i] = src;
			SOURCE_SOUND_IDS[i] = null;
			SOURCE_START_NANOS[i] = 0L;
		}

		// Synthesize and upload all Cyberpunk 2077 44.1 kHz 16-bit mono PCM waveforms.
		uploadBufferLocked("sandevistan_activate", synthSandevistanActivate());
		uploadBufferLocked("sandevistan_pulse", synthSandevistanPulse());
		uploadBufferLocked("sandevistan_warn", synthSandevistanWarn());
		uploadBufferLocked("sandevistan_deactivate", synthSandevistanDeactivate());

		uploadBufferLocked("mantis_unsheathe", synthMantisUnsheathe());
		uploadBufferLocked("mantis_retract", synthMantisRetract());
		uploadBufferLocked("mantis_slash_1", synthMantisSlash(0));
		uploadBufferLocked("mantis_slash_2", synthMantisSlash(1));
		uploadBufferLocked("mantis_slash_3", synthMantisSlash(2));

		uploadBufferLocked("kiroshi_lock", synthKiroshiLock());
		uploadBufferLocked("double_jump_jet", synthDoubleJumpJet());
		uploadBufferLocked("kerenzikov_surge", synthKerenzikovSurge());
		uploadBufferLocked("second_heart_defib", synthSecondHeartDefib());
		uploadBufferLocked("immunoblocker_inject", synthImmunoblockerInject());

		uploadBufferLocked("cyberpsychosis_break", synthCyberpsychosisBreak());
		uploadBufferLocked("cyberpsychosis_glitch", synthCyberpsychosisGlitch());

		uploadBufferLocked("ui_hover", synthUiHover());
		uploadBufferLocked("ui_click", synthUiClick());
		uploadBufferLocked("ui_install", synthUiInstall());
		uploadBufferLocked("ui_error", synthUiError());
		uploadBufferLocked("ui_uninstall", synthUiUninstall());
		uploadBufferLocked("ui_open", synthUiOpen());

		initializedContext = ctx;
		initialized = true;
		return true;
	}

	private static void uploadBufferLocked(String soundId, short[] pcm) {
		int bufferId = AL10.alGenBuffers();
		if (bufferId == 0) {
			return;
		}
		ShortBuffer nativeBuf = MemoryUtil.memAllocShort(pcm.length);
		try {
			nativeBuf.put(pcm).flip();
			AL10.alBufferData(bufferId, AL10.AL_FORMAT_MONO16, nativeBuf, SAMPLE_RATE);
			BUFFERS.put(soundId, bufferId);
		} finally {
			MemoryUtil.memFree(nativeBuf);
		}
	}

	private static void clearTrackingStateLocked() {
		initialized = false;
		initializedContext = 0L;
		BUFFERS.clear();
		for (int i = 0; i < SOURCE_POOL_SIZE; i++) {
			SOURCES[i] = 0;
			SOURCE_SOUND_IDS[i] = null;
			SOURCE_START_NANOS[i] = 0L;
		}
	}

	private static void stopSoundLocked(String soundId) {
		for (int i = 0; i < SOURCE_POOL_SIZE; i++) {
			if (soundId.equals(SOURCE_SOUND_IDS[i]) && SOURCES[i] != 0) {
				AL10.alSourceStop(SOURCES[i]);
				SOURCE_SOUND_IDS[i] = null;
				SOURCE_START_NANOS[i] = 0L;
			}
		}
	}

	private static void applyMutualExclusionLocked(String soundId) {
		switch (soundId) {
			case "sandevistan_deactivate" -> {
				stopSoundLocked("sandevistan_activate");
				stopSoundLocked("sandevistan_pulse");
				stopSoundLocked("sandevistan_warn");
			}
			case "sandevistan_activate" -> stopSoundLocked("sandevistan_deactivate");
			case "mantis_unsheathe" -> stopSoundLocked("mantis_retract");
			case "mantis_retract" -> stopSoundLocked("mantis_unsheathe");
			case "immunoblocker_inject" -> {
				stopSoundLocked("cyberpsychosis_break");
				stopSoundLocked("cyberpsychosis_glitch");
			}
			case "cyberpsychosis_break" -> stopSoundLocked("cyberpsychosis_break");
			case "ui_hover" -> stopSoundLocked("ui_hover");
			case "kiroshi_lock" -> stopSoundLocked("kiroshi_lock");
			default -> {
			}
		}
	}

	private static int acquireSourceSlotLocked() {
		int oldestIndex = -1;
		long oldestNanos = Long.MAX_VALUE;
		for (int i = 0; i < SOURCE_POOL_SIZE; i++) {
			int src = SOURCES[i];
			if (src == 0) {
				continue;
			}
			int state = AL10.alGetSourcei(src, AL10.AL_SOURCE_STATE);
			if (state != AL10.AL_PLAYING) {
				SOURCE_SOUND_IDS[i] = null;
				return i;
			}
			if (SOURCE_START_NANOS[i] < oldestNanos) {
				oldestNanos = SOURCE_START_NANOS[i];
				oldestIndex = i;
			}
		}
		return oldestIndex;
	}

	// ================================================================================================
	// Cyberpunk 2077 DSP Sound Synthesizers (44.1 kHz 16-bit mono PCM)
	// ================================================================================================

	/**
	 * 1. {@code sandevistan_activate} (1.65s):
	 * Iconic Cyberpunk 2077 / Edgerunners Sandevistan time-drop —
	 * neural relay snap (0..0.045s) -> massive exponential sub-bass drop (260 -> 32 Hz) +
	 * band-pass filtered cyber-turbine harmonic sweep (440 -> 2200 -> 310 Hz) +
	 * dark underwater low-pass air vacuum whoosh + neural reverb tail.
	 */
	private static short[] synthSandevistanActivate() {
		int n = samples(1.65);
		double[] buf = new double[n];
		Rng rng = new Rng(0x5A4D_0001L);

		Biquad snapHp = new Biquad();
		snapHp.setHighPass(3200.0, 0.9);
		Biquad turbineBp = new Biquad();
		Biquad vacuumLp = new Biquad();

		double snapCarPhase = 0.0;
		double snapModPhase = 0.0;
		double subPhase = 0.0;
		double turbPhaseA = 0.0;
		double turbPhaseB = 0.0;
		double shimmerPhaseA = 0.0;
		double shimmerPhaseB = 0.0;
		double pinkNoise = 0.0;

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double out = 0.0;

			// Layer 1: Sharp neural spine relay snap (0.00..0.055s)
			if (t < 0.06) {
				double env = Math.exp(-t / 0.009) * smoothAttack(t, 0.0008);
				double snapNoise = snapHp.process(rng.nextSigned());
				snapCarPhase += 1950.0 / SAMPLE_RATE;
				snapModPhase += 780.0 / SAMPLE_RATE;
				double fmClick = Math.sin(TWO_PI * snapCarPhase + 2.8 * Math.exp(-t / 0.006) * Math.sin(TWO_PI * snapModPhase));
				double relayKick = Math.sin(TWO_PI * (140.0 * t)) * Math.exp(-t / 0.012);
				out += (snapNoise * 0.65 + fmClick * 0.55 + relayKick * 0.45) * env * 1.15;
			}

			// Layer 2: Massive exponential sub-bass time-drop (260 Hz -> 32 Hz)
			if (t >= 0.015 && t < 1.55) {
				double lt = t - 0.015;
				double subFreq;
				if (lt < 0.34) {
					subFreq = expSweep(260.0, 62.0, lt / 0.34);
				} else {
					subFreq = expSweep(62.0, 32.0, (lt - 0.34) / 1.19);
				}
				subPhase += subFreq / SAMPLE_RATE;
				double subEnv = smoothAttack(lt, 0.014) * Math.exp(-lt / 0.62) * fadeWindow(t, 1.32, 1.55);
				double subWave = Math.sin(TWO_PI * subPhase) + 0.24 * Math.sin(TWO_PI * subPhase * 2.0);
				out += Math.tanh(subWave * 1.65) * subEnv * 0.92;
			}

			// Layer 3: Band-pass filtered cyber-turbine harmonic sweep (440 -> 2200 -> 310 Hz)
			if (t >= 0.025 && t < 1.40) {
				double lt = t - 0.025;
				double turbFreq;
				if (lt < 0.36) {
					turbFreq = expSweep(440.0, 2200.0, lt / 0.36);
				} else {
					turbFreq = expSweep(2200.0, 310.0, (lt - 0.36) / 1.015);
				}
				double dtA = turbFreq / SAMPLE_RATE;
				double dtB = (turbFreq * 1.007) / SAMPLE_RATE;
				turbPhaseA = wrap01(turbPhaseA + dtA);
				turbPhaseB = wrap01(turbPhaseB + dtB);
				double rawTurb = 0.5 * (polySaw(turbPhaseA, dtA) + polySaw(turbPhaseB, dtB))
						+ 0.35 * Math.sin(TWO_PI * turbPhaseA * 0.5);
				turbineBp.setBandPass(clamp(turbFreq * 1.15, 180.0, 6000.0), 2.8);
				double turbEnv = smoothAttack(lt, 0.03) * Math.exp(-Math.max(0.0, lt - 0.32) / 0.44) * fadeWindow(t, 1.20, 1.40);
				out += turbineBp.process(rawTurb) * turbEnv * 0.62;
			}

			// Layer 4: Dark underwater low-pass air vacuum whoosh
			if (t >= 0.04) {
				double lt = t - 0.04;
				pinkNoise = 0.92 * pinkNoise + 0.08 * rng.nextSigned();
				double airSrc = rng.nextSigned() * 0.55 + pinkNoise * 1.4;
				double cutoff = expSweep(4800.0, 155.0, clamp01(lt / 1.05));
				vacuumLp.setLowPass(cutoff, 2.3);
				double vacEnv = smoothAttack(lt, 0.08) * Math.exp(-lt / 0.68) * fadeWindow(t, 1.38, 1.65);
				out += vacuumLp.process(airSrc) * vacEnv * 0.52;
			}

			// Layer 5: Glassy neural harmonic shimmer tail
			if (t >= 0.14) {
				double lt = t - 0.14;
				double fA = expSweep(1760.0, 1240.0, clamp01(lt / 1.45));
				double fB = expSweep(1320.0, 930.0, clamp01(lt / 1.45));
				shimmerPhaseA += fA / SAMPLE_RATE;
				shimmerPhaseB += fB / SAMPLE_RATE;
				double shimEnv = smoothAttack(lt, 0.14) * Math.exp(-lt / 0.58) * fadeWindow(t, 1.35, 1.65);
				out += (Math.sin(TWO_PI * shimmerPhaseA) * 0.55 + Math.sin(TWO_PI * shimmerPhaseB) * 0.45) * shimEnv * 0.14;
			}

			buf[i] = out;
		}

		applySchroederReverb(buf, 0.26, 0.78, 0.38);
		return finalizeToPcm16(buf, 0.94, 1.25, 22, 360);
	}

	/**
	 * 2. {@code sandevistan_pulse} (1.10s):
	 * Deep slowed-time underwater sub-drone (42 Hz + 63 Hz) +
	 * muffled anatomical lub-dub heartbeat (58 -> 26 Hz) + glassy high neural harmonic shimmer.
	 */
	private static short[] synthSandevistanPulse() {
		int n = samples(1.10);
		double[] buf = new double[n];

		Biquad subLp = new Biquad();
		subLp.setLowPass(155.0, 0.85);
		Biquad hbLp = new Biquad();
		hbLp.setLowPass(170.0, 1.1);

		double dronePhase1 = 0.0;
		double dronePhase2 = 0.0;
		double lubPhase = 0.0;
		double dubPhase = 0.0;
		double shimPhase1 = 0.0;
		double shimPhase2 = 0.0;

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double out = 0.0;

			// Deep underwater sub-drone (42 Hz + 63 Hz) with smooth breathing envelope
			double droneEnv = smoothAttack(t, 0.16) * fadeWindow(t, 0.78, 1.10);
			double lfo = 0.82 + 0.18 * Math.sin(TWO_PI * 1.35 * t);
			dronePhase1 += 42.0 / SAMPLE_RATE;
			dronePhase2 += 63.0 / SAMPLE_RATE;
			double drone = Math.sin(TWO_PI * dronePhase1) * 0.72 + Math.sin(TWO_PI * dronePhase2) * 0.38;
			out += subLp.process(Math.tanh(drone * 1.35)) * droneEnv * lfo * 0.56;

			// Anatomical lub-dub heartbeat (lub at 0.12s, dub at 0.38s)
			double hb = 0.0;
			if (t >= 0.12 && t < 0.36) {
				double lt = t - 0.12;
				double f = expSweep(58.0, 26.0, clamp01(lt / 0.19));
				lubPhase += f / SAMPLE_RATE;
				double env = smoothAttack(lt, 0.012) * Math.exp(-lt / 0.068);
				hb += Math.tanh(Math.sin(TWO_PI * lubPhase) * 1.8) * env * 1.05;
			}
			if (t >= 0.38 && t < 0.60) {
				double lt = t - 0.38;
				double f = expSweep(66.0, 30.0, clamp01(lt / 0.15));
				dubPhase += f / SAMPLE_RATE;
				double env = smoothAttack(lt, 0.010) * Math.exp(-lt / 0.052);
				hb += Math.tanh(Math.sin(TWO_PI * dubPhase) * 1.65) * env * 0.82;
			}
			out += hbLp.process(hb) * 0.95;

			// Glassy high neural harmonic shimmer
			double shimEnv = smoothAttack(t, 0.20) * fadeWindow(t, 0.72, 1.10);
			double vib = 1.0 + 0.004 * Math.sin(TWO_PI * 3.8 * t);
			shimPhase1 += (1180.0 * vib) / SAMPLE_RATE;
			shimPhase2 += (1770.0 / vib) / SAMPLE_RATE;
			double shimmer = Math.sin(TWO_PI * shimPhase1 + 0.35 * Math.sin(TWO_PI * shimPhase2))
					+ 0.45 * Math.sin(TWO_PI * shimPhase2);
			out += shimmer * shimEnv * 0.065;

			buf[i] = out;
		}

		applySchroederReverb(buf, 0.24, 0.72, 0.45);
		return finalizeToPcm16(buf, 0.80, 1.15, 160, 440);
	}

	/**
	 * 3. {@code sandevistan_warn} (0.32s):
	 * Crisp Militech dual-beep expiring telemetry alert:
	 * 1760 Hz (65ms) -> 40ms gap -> 1320 Hz (95ms) with clean FM telemetry edge.
	 */
	private static short[] synthSandevistanWarn() {
		int n = samples(0.32);
		double[] buf = new double[n];

		double p1 = 0.0;
		double p2 = 0.0;
		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double out = 0.0;

			// Beep 1: 0.000..0.065s at 1760 Hz
			if (t < 0.065) {
				p1 += 1760.0 / SAMPLE_RATE;
				double env = smoothAttack(t, 0.0025) * fadeWindow(t, 0.050, 0.065);
				double mod = 0.35 * Math.sin(TWO_PI * p1 * 2.0);
				out += (Math.sin(TWO_PI * p1 + mod) + 0.16 * Math.sin(TWO_PI * p1 * 3.0)) * env * 0.85;
			}
			// 40ms silence gap: 0.065..0.105s
			// Beep 2: 0.105..0.200s at 1320 Hz
			if (t >= 0.105 && t < 0.205) {
				double lt = t - 0.105;
				p2 += 1320.0 / SAMPLE_RATE;
				double env = smoothAttack(lt, 0.0025) * fadeWindow(lt, 0.072, 0.100);
				double mod = 0.42 * Math.sin(TWO_PI * p2 * 2.0);
				out += (Math.sin(TWO_PI * p2 + mod) + 0.18 * Math.sin(TWO_PI * p2 * 3.0)) * env * 0.90;
			}

			buf[i] = out;
		}

		applySchroederReverb(buf, 0.14, 0.62, 0.30);
		return finalizeToPcm16(buf, 0.68, 1.05, 44, 240);
	}

	/**
	 * 4. {@code sandevistan_deactivate} (1.05s):
	 * Reverse tape-speedup sub-sweep (34 -> 340 Hz) + pneumatic hiss release + crisp relay lock-in click.
	 */
	private static short[] synthSandevistanDeactivate() {
		int n = samples(1.05);
		double[] buf = new double[n];
		Rng rng = new Rng(0x5A4D_0004L);

		Biquad riseLp = new Biquad();
		Biquad hissHp = new Biquad();
		hissHp.setHighPass(1900.0, 0.8);
		Biquad hissBp = new Biquad();

		double subPhase = 0.0;
		double sawPhaseA = 0.0;
		double sawPhaseB = 0.0;
		double clickCarPhase = 0.0;
		double clickModPhase = 0.0;
		double thudPhase = 0.0;

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double out = 0.0;

			// Reverse tape-speedup sub & harmonic sweep (0.00..0.725s)
			if (t < 0.73) {
				double progress = clamp01(t / 0.72);
				double curved = Math.pow(progress, 1.35);
				double subFreq = expSweep(34.0, 340.0, curved);
				subPhase += subFreq / SAMPLE_RATE;

				double dtA = (subFreq * 2.0) / SAMPLE_RATE;
				double dtB = (subFreq * 2.012) / SAMPLE_RATE;
				sawPhaseA = wrap01(sawPhaseA + dtA);
				sawPhaseB = wrap01(sawPhaseB + dtB);
				double saws = 0.5 * (polySaw(sawPhaseA, dtA) + polySaw(sawPhaseB, dtB));

				riseLp.setLowPass(expSweep(160.0, 3800.0, curved), 2.5);
				double riseEnv = smoothAttack(t, 0.04) * (0.28 + 0.72 * progress) * fadeWindow(t, 0.70, 0.73);
				out += (Math.sin(TWO_PI * subPhase) * 0.72 + riseLp.process(saws) * 0.52) * riseEnv;
			}

			// Crisp relay lock-in click right at time snap-back (0.71s)
			if (t >= 0.71 && t < 0.84) {
				double lt = t - 0.71;
				clickCarPhase += 2450.0 / SAMPLE_RATE;
				clickModPhase += 1120.0 / SAMPLE_RATE;
				double clickEnv = smoothAttack(lt, 0.0008) * Math.exp(-lt / 0.011);
				double metallicClick = Math.sin(TWO_PI * clickCarPhase + 2.4 * Math.exp(-lt / 0.007) * Math.sin(TWO_PI * clickModPhase));

				double thudFreq = expSweep(160.0, 58.0, clamp01(lt / 0.075));
				thudPhase += thudFreq / SAMPLE_RATE;
				double thudEnv = smoothAttack(lt, 0.0015) * Math.exp(-lt / 0.032);
				out += (metallicClick * 0.75 * clickEnv) + (Math.tanh(Math.sin(TWO_PI * thudPhase) * 1.7) * 0.85 * thudEnv);
			}

			// Pneumatic heat-vent hiss release (0.60..1.03s)
			if (t >= 0.60) {
				double lt = t - 0.60;
				double noise = rng.nextSigned();
				hissBp.setBandPass(expSweep(4600.0, 1800.0, clamp01(lt / 0.42)), 1.6);
				double hissEnv = smoothAttack(lt, 0.075) * Math.exp(-Math.max(0.0, lt - 0.07) / 0.115) * fadeWindow(t, 0.90, 1.05);
				out += (hissHp.process(noise) * 0.35 + hissBp.process(noise) * 0.65) * hissEnv * 0.52;
			}

			buf[i] = out;
		}

		applySchroederReverb(buf, 0.20, 0.70, 0.38);
		return finalizeToPcm16(buf, 0.92, 1.20, 60, 320);
	}

	/**
	 * 5. {@code mantis_unsheathe} (0.58s):
	 * Cyberpunk 2077 Mantis Blades deploy —
	 * hydraulic servo hiss (BP noise 900 -> 3600 Hz) + dual-latch metallic CLACK-SHING!
	 * (inharmonic steel partials 1480, 2410, 3890, 5720 Hz) + rising thermal-plasma hum.
	 */
	private static short[] synthMantisUnsheathe() {
		int n = samples(0.58);
		double[] buf = new double[n];
		Rng rng = new Rng(0x4D41_0005L);

		Biquad servoBp = new Biquad();
		Biquad scrapeBp = new Biquad();
		Biquad plasmaLp = new Biquad();

		double servoPhase = 0.0;
		double latchPhaseA = 0.0;
		double latchPhaseB = 0.0;
		double[] steelFreqs = {1480.0, 2410.0, 3890.0, 5720.0, 7180.0};
		double[] steelAmps = {0.42, 0.36, 0.28, 0.19, 0.11};
		double[] steelDecays = {0.145, 0.125, 0.105, 0.082, 0.062};
		double[] steelPhases = new double[steelFreqs.length];
		double steelModPhase = 0.0;
		double plasmaPhase = 0.0;

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double out = 0.0;

			// Hydraulic forearm hatch servo hiss (0.00..0.25s)
			if (t < 0.26) {
				double p = clamp01(t / 0.24);
				servoBp.setBandPass(expSweep(900.0, 3600.0, p), 2.2);
				double servoEnv = smoothAttack(t, 0.012) * Math.exp(-Math.max(0.0, t - 0.06) / 0.085);
				double whineFreq = expSweep(520.0, 1480.0, p);
				servoPhase += whineFreq / SAMPLE_RATE;
				out += (servoBp.process(rng.nextSigned()) * 0.62 + Math.sin(TWO_PI * servoPhase) * 0.20) * servoEnv;
			}

			// Latch 1 CLACK! (0.105s)
			if (t >= 0.105 && t < 0.19) {
				double lt = t - 0.105;
				latchPhaseA += 1120.0 / SAMPLE_RATE;
				latchPhaseB += 2340.0 / SAMPLE_RATE;
				double env = smoothAttack(lt, 0.0008) * Math.exp(-lt / 0.014);
				double clack = Math.sin(TWO_PI * latchPhaseA) * 0.6 + Math.sin(TWO_PI * latchPhaseB) * 0.5
						+ rng.nextSigned() * Math.exp(-lt / 0.005) * 0.7;
				out += clack * env * 0.90;
			}

			// Blade Lock SHING! (0.195..0.56s) — inharmonic steel partials + scrape shimmer
			if (t >= 0.195) {
				double lt = t - 0.195;
				steelModPhase += 2093.0 / SAMPLE_RATE;
				double fmDepth = 1.6 * Math.exp(-lt / 0.025);
				double steelSum = 0.0;
				for (int k = 0; k < steelFreqs.length; k++) {
					double f = steelFreqs[k] * (1.0 + 0.018 * Math.exp(-lt / 0.04));
					steelPhases[k] += f / SAMPLE_RATE;
					double pEnv = smoothAttack(lt, 0.0015) * Math.exp(-lt / steelDecays[k]);
					steelSum += Math.sin(TWO_PI * steelPhases[k] + fmDepth * Math.sin(TWO_PI * steelModPhase))
							* steelAmps[k] * pEnv;
				}
				scrapeBp.setBandPass(expSweep(4400.0, 2500.0, clamp01(lt / 0.18)), 3.2);
				double scrapeEnv = smoothAttack(lt, 0.002) * Math.exp(-lt / 0.055);
				out += steelSum * 0.85 + scrapeBp.process(rng.nextSigned()) * scrapeEnv * 0.50;
			}

			// Rising thermal-plasma edge hum (0.24..0.58s)
			if (t >= 0.24) {
				double lt = t - 0.24;
				double pFreq = expSweep(175.0, 350.0, clamp01(lt / 0.28));
				double dt = pFreq / SAMPLE_RATE;
				plasmaPhase = wrap01(plasmaPhase + dt);
				plasmaLp.setLowPass(expSweep(600.0, 1900.0, clamp01(lt / 0.25)), 1.8);
				double pEnv = smoothAttack(lt, 0.04) * fadeWindow(t, 0.42, 0.58);
				out += plasmaLp.process(polySaw(plasmaPhase, dt)) * pEnv * 0.24;
			}

			buf[i] = out;
		}

		applySchroederReverb(buf, 0.22, 0.72, 0.30);
		return finalizeToPcm16(buf, 0.92, 1.15, 22, 260);
	}

	/**
	 * 6. {@code mantis_retract} (0.42s):
	 * Reverse metallic blade slide-in + damped pneumatic housing latch lock.
	 */
	private static short[] synthMantisRetract() {
		int n = samples(0.42);
		double[] buf = new double[n];
		Rng rng = new Rng(0x4D41_0006L);

		Biquad slideBp = new Biquad();
		Biquad ventHp = new Biquad();
		ventHp.setHighPass(2400.0, 0.85);

		double steelA = 0.0;
		double steelB = 0.0;
		double humPhase = 0.0;
		double clunkPhase = 0.0;
		double lockPhase = 0.0;

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double out = 0.0;

			// Descending metallic slide + plasma power-down (0.00..0.24s)
			if (t < 0.25) {
				double p = clamp01(t / 0.24);
				slideBp.setBandPass(expSweep(3800.0, 1100.0, p), 2.8);
				steelA += expSweep(3600.0, 1850.0, p) / SAMPLE_RATE;
				steelB += expSweep(2200.0, 1180.0, p) / SAMPLE_RATE;
				humPhase += expSweep(340.0, 92.0, p) / SAMPLE_RATE;
				double env = smoothAttack(t, 0.008) * fadeWindow(t, 0.19, 0.25);
				out += (slideBp.process(rng.nextSigned()) * 0.52
						+ (Math.sin(TWO_PI * steelA) * 0.22 + Math.sin(TWO_PI * steelB) * 0.20) * Math.exp(-t / 0.12)
						+ Math.sin(TWO_PI * humPhase) * 0.24) * env;
			}

			// Damped pneumatic housing latch lock (0.215..0.41s)
			if (t >= 0.215) {
				double lt = t - 0.215;
				clunkPhase += expSweep(225.0, 82.0, clamp01(lt / 0.055)) / SAMPLE_RATE;
				lockPhase += 1650.0 / SAMPLE_RATE;
				double clunkEnv = smoothAttack(lt, 0.001) * Math.exp(-lt / 0.026);
				double snapEnv = smoothAttack(lt, 0.0008) * Math.exp(-lt / 0.011);
				double puffEnv = smoothAttack(lt, 0.008) * Math.exp(-lt / 0.048);
				out += Math.tanh(Math.sin(TWO_PI * clunkPhase) * 1.6) * clunkEnv * 0.85
						+ Math.sin(TWO_PI * lockPhase) * snapEnv * 0.45
						+ ventHp.process(rng.nextSigned()) * puffEnv * 0.28;
			}

			buf[i] = out;
		}

		applySchroederReverb(buf, 0.16, 0.62, 0.38);
		return finalizeToPcm16(buf, 0.88, 1.15, 22, 220);
	}

	/**
	 * 7. {@code mantis_slash_1}, {@code mantis_slash_2}, {@code mantis_slash_3} (0.34s each):
	 * Razor-sharp air cleave (swept BP noise 3400 -> 520 Hz), steel blade ring,
	 * and high-voltage thermal-plasma crackle stepping up across the 3-hit combo.
	 */
	private static short[] synthMantisSlash(int comboIndex) {
		int n = samples(0.34);
		double[] buf = new double[n];
		Rng rng = new Rng(0x4D41_0100L + comboIndex * 997L);

		double pitchMul = 1.0 + comboIndex * 0.14; // 1.00, 1.14, 1.28
		double plasmaBoost = 0.36 + comboIndex * 0.18;

		Biquad cleaveBp = new Biquad();
		Biquad sparkHp = new Biquad();
		sparkHp.setHighPass(4600.0, 0.8);

		double[] ringBase = {1650.0, 2780.0, 4320.0, 6150.0};
		double[] ringAmps = {0.34, 0.28, 0.22, 0.14};
		double[] ringPhases = new double[ringBase.length];
		double bodyPhase = 0.0;
		double arcPhase = 0.0;

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double p = clamp01(t / 0.26);
			double out = 0.0;

			// Razor-sharp air cleave (swept band-pass noise 3400 -> 520 Hz)
			double cleaveFreq = expSweep(3400.0 * pitchMul, 520.0 * pitchMul, Math.pow(p, 0.72));
			cleaveBp.setBandPass(clamp(cleaveFreq, 200.0, 9500.0), 2.1);
			double cleaveEnv = smoothAttack(t, 0.012) * Math.exp(-Math.max(0.0, t - 0.025) / 0.068);
			out += cleaveBp.process(rng.nextSigned()) * cleaveEnv * 1.10;

			// Titanium steel blade ring with Doppler pitch fall
			double doppler = 1.0 - 0.18 * p;
			double ringEnv = smoothAttack(t, 0.004) * Math.exp(-t / (0.095 + comboIndex * 0.012));
			double ring = 0.0;
			for (int k = 0; k < ringBase.length; k++) {
				ringPhases[k] += (ringBase[k] * pitchMul * doppler) / SAMPLE_RATE;
				ring += Math.sin(TWO_PI * ringPhases[k]) * ringAmps[k];
			}
			out += ring * ringEnv * 0.62;

			// Impact body thump
			bodyPhase += expSweep(215.0 * pitchMul, 62.0, clamp01(t / 0.11)) / SAMPLE_RATE;
			double bodyEnv = smoothAttack(t, 0.003) * Math.exp(-t / 0.042);
			out += Math.tanh(Math.sin(TWO_PI * bodyPhase) * 1.8) * bodyEnv * (0.45 + comboIndex * 0.10);

			// High-voltage thermal-plasma crackle (extra fierce on combo finisher!)
			if (t >= 0.015 && t < 0.28) {
				double lt = t - 0.015;
				double dt = (180.0 * pitchMul) / SAMPLE_RATE;
				arcPhase = wrap01(arcPhase + dt);
				double crackleGate = rng.nextUnit() < (0.055 + comboIndex * 0.03) ? 1.0 : 0.18;
				double spark = sparkHp.process(rng.nextSigned() * crackleGate);
				double arcBuzz = Math.tanh(polySaw(arcPhase, dt) * 2.4) * 0.25;
				double plasmaEnv = smoothAttack(lt, 0.006) * Math.exp(-lt / 0.085);
				out += (spark * 0.95 + arcBuzz) * plasmaEnv * plasmaBoost;
			}

			buf[i] = out;
		}

		applySchroederReverb(buf, 0.15, 0.60, 0.34);
		return finalizeToPcm16(buf, comboIndex == 2 ? 0.94 : 0.90, 1.25, 22, 220);
	}

	/**
	 * 8. {@code kiroshi_lock} (0.18s):
	 * Exact Cyberpunk 2077 Kiroshi Optics 3-step rapid digital scanner chirp:
	 * 2100 Hz -> 2850 Hz -> 3520 Hz (22ms steps, ultra-clean glassy FM tone).
	 */
	private static short[] synthKiroshiLock() {
		int n = samples(0.18);
		double[] buf = new double[n];

		double[] stepStarts = {0.000, 0.028, 0.056};
		double[] stepDurs = {0.022, 0.022, 0.095};
		double[] stepFreqs = {2100.0, 2850.0, 3520.0};
		double[] stepPhases = new double[3];

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double out = 0.0;

			for (int s = 0; s < 3; s++) {
				double lt = t - stepStarts[s];
				if (lt >= 0.0 && lt < stepDurs[s] + 0.025) {
					double f = stepFreqs[s];
					stepPhases[s] += f / SAMPLE_RATE;
					double env = smoothAttack(lt, 0.0018)
							* (s < 2
							? fadeWindow(lt, stepDurs[s] - 0.005, stepDurs[s] + 0.004)
							: Math.exp(-lt / 0.036) * fadeWindow(lt, 0.085, 0.120));
					double fmIndex = (0.22 + s * 0.04) * Math.exp(-lt / 0.016);
					double tone = Math.sin(TWO_PI * stepPhases[s] + fmIndex * Math.sin(TWO_PI * stepPhases[s] * 2.0));
					out += tone * env * (s == 2 ? 0.92 : 0.72);
				}
			}

			buf[i] = out;
		}

		applySchroederReverb(buf, 0.12, 0.55, 0.25);
		return finalizeToPcm16(buf, 0.56, 1.0, 32, 180);
	}

	/**
	 * 9. {@code double_jump_jet} (0.42s):
	 * Reinforced Tendons mechanical spring-servo snap + punchy compressed-air thrust thump (170 -> 42 Hz).
	 */
	private static short[] synthDoubleJumpJet() {
		int n = samples(0.42);
		double[] buf = new double[n];
		Rng rng = new Rng(0x4A45_0009L);

		Biquad jetHp = new Biquad();
		jetHp.setHighPass(1750.0, 0.85);
		Biquad jetBp = new Biquad();

		double servoPhase = 0.0;
		double springPhase = 0.0;
		double thumpPhase = 0.0;

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double out = 0.0;

			// Mechanical spring-servo snap (0.00..0.07s)
			if (t < 0.075) {
				servoPhase += expSweep(1350.0, 2650.0, clamp01(t / 0.032)) / SAMPLE_RATE;
				springPhase += 860.0 / SAMPLE_RATE;
				double env = smoothAttack(t, 0.001) * Math.exp(-t / 0.015);
				out += (Math.sin(TWO_PI * servoPhase) * 0.55 + Math.sin(TWO_PI * springPhase) * 0.45) * env * 0.72;
			}

			// Punchy compressed-air thrust thump (170 -> 42 Hz)
			if (t >= 0.008 && t < 0.30) {
				double lt = t - 0.008;
				thumpPhase += expSweep(170.0, 42.0, clamp01(lt / 0.14)) / SAMPLE_RATE;
				double thumpEnv = smoothAttack(lt, 0.003) * Math.exp(-lt / 0.072);
				out += Math.tanh(Math.sin(TWO_PI * thumpPhase) * 1.9) * thumpEnv * 0.95;
			}

			// High-pressure pneumatic air burst
			if (t >= 0.012) {
				double lt = t - 0.012;
				double noise = rng.nextSigned();
				jetBp.setBandPass(expSweep(3400.0, 880.0, clamp01(lt / 0.32)), 1.5);
				double jetEnv = smoothAttack(lt, 0.006) * Math.exp(-lt / 0.105) * fadeWindow(t, 0.34, 0.42);
				out += (jetHp.process(noise) * 0.45 + jetBp.process(noise) * 0.75) * jetEnv * 0.78;
			}

			buf[i] = out;
		}

		applySchroederReverb(buf, 0.14, 0.60, 0.40);
		return finalizeToPcm16(buf, 0.90, 1.20, 22, 220);
	}

	/**
	 * 10. {@code kerenzikov_surge} (0.48s):
	 * Quick neural reflex time-zip (180 -> 52 Hz mini time-bend + electric high-frequency zip).
	 */
	private static short[] synthKerenzikovSurge() {
		int n = samples(0.48);
		double[] buf = new double[n];
		Rng rng = new Rng(0x4B45_0010L);

		Biquad zipBp = new Biquad();
		double subPhase = 0.0;
		double fmCarPhase = 0.0;
		double fmModPhase = 0.0;

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double out = 0.0;

			// Mini time-bend sub-dip (180 -> 52 -> 82 Hz)
			double subFreq = t < 0.22
					? expSweep(180.0, 52.0, t / 0.22)
					: expSweep(52.0, 82.0, clamp01((t - 0.22) / 0.24));
			subPhase += subFreq / SAMPLE_RATE;
			double subEnv = smoothAttack(t, 0.006) * Math.exp(-t / 0.15) * fadeWindow(t, 0.36, 0.48);
			out += Math.tanh(Math.sin(TWO_PI * subPhase) * 1.55) * subEnv * 0.85;

			// Electric high-frequency reflex zip (620 -> 3400 -> 1150 Hz)
			double zipFreq = t < 0.09
					? expSweep(620.0, 3400.0, t / 0.09)
					: expSweep(3400.0, 1150.0, clamp01((t - 0.09) / 0.26));
			fmCarPhase += zipFreq / SAMPLE_RATE;
			fmModPhase += (zipFreq * 1.5) / SAMPLE_RATE;
			double fmEnv = smoothAttack(t, 0.003) * Math.exp(-t / 0.105);
			double zipTone = Math.sin(TWO_PI * fmCarPhase + 1.35 * Math.exp(-t / 0.08) * Math.sin(TWO_PI * fmModPhase));
			out += zipTone * fmEnv * 0.58;

			// Airy electric noise streak
			zipBp.setBandPass(expSweep(5200.0, 1450.0, clamp01(t / 0.28)), 2.4);
			double nEnv = smoothAttack(t, 0.004) * Math.exp(-t / 0.078);
			out += zipBp.process(rng.nextSigned()) * nEnv * 0.48;

			buf[i] = out;
		}

		applySchroederReverb(buf, 0.20, 0.68, 0.35);
		return finalizeToPcm16(buf, 0.90, 1.15, 22, 240);
	}

	/**
	 * 11. {@code second_heart_defib} (1.25s):
	 * Defibrillator capacitor charge whine (380 -> 3400 Hz over 0.45s) ->
	 * thunderous cardiac shock BOOM-BZZT! (95 -> 26 Hz + distorted 60 Hz electric arc) ->
	 * crisp steady restarted heartbeat.
	 */
	private static short[] synthSecondHeartDefib() {
		int n = samples(1.25);
		double[] buf = new double[n];
		Rng rng = new Rng(0x5348_0011L);

		Biquad arcBp = new Biquad();
		arcBp.setBandPass(1250.0, 1.8);
		Biquad sparkHp = new Biquad();
		sparkHp.setHighPass(3600.0, 0.8);
		Biquad hbLp = new Biquad();
		hbLp.setLowPass(185.0, 1.0);

		double chargePhase = 0.0;
		double boomPhase = 0.0;
		double arcPhase = 0.0;
		double lubPhase = 0.0;
		double dubPhase = 0.0;
		double chimePhase = 0.0;

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double out = 0.0;

			// Stage 1: Capacitor charge whine (0.00..0.448s), followed by dramatic 17ms silence gap
			if (t < 0.448) {
				double p = clamp01(t / 0.445);
				double whineFreq = expSweep(380.0, 3400.0, Math.pow(p, 1.12));
				chargePhase += whineFreq / SAMPLE_RATE;
				double trem = 0.88 + 0.12 * Math.sin(TWO_PI * 22.0 * t);
				double env = smoothAttack(t, 0.025) * (0.25 + 0.75 * p) * fadeWindow(t, 0.438, 0.448);
				double wave = Math.sin(TWO_PI * chargePhase) + 0.22 * Math.sin(TWO_PI * chargePhase * 3.0);
				out += wave * env * trem * 0.56;
			}

			// Stage 2: Thunderous cardiac shock BOOM-BZZT! (0.465..0.85s)
			if (t >= 0.465 && t < 0.86) {
				double lt = t - 0.465;
				// Massive sub-bass defibrillator shock punch
				boomPhase += expSweep(95.0, 26.0, clamp01(lt / 0.24)) / SAMPLE_RATE;
				double boomEnv = smoothAttack(lt, 0.0015) * Math.exp(-lt / 0.115);
				double boom = Math.tanh(Math.sin(TWO_PI * boomPhase) * 2.5) * boomEnv * 1.15;

				// Distorted 60 Hz / 120 Hz high-voltage electric arc
				double dt = 60.0 / SAMPLE_RATE;
				arcPhase = wrap01(arcPhase + dt);
				double rawArc = Math.tanh((polySaw(arcPhase, dt) + 0.65 * Math.sin(TWO_PI * arcPhase * 2.0)) * 3.2);
				double arcEnv = smoothAttack(lt, 0.001) * Math.exp(-lt / 0.095);
				double spark = sparkHp.process(rng.nextSigned()) * Math.exp(-lt / 0.055);
				out += boom + (arcBp.process(rawArc) * 0.72 + rawArc * 0.24 + spark * 0.85) * arcEnv;
			}

			// Stage 3: Steady restarted heartbeat (lub at 0.84s, dub at 1.02s) + recovery ping
			double hb = 0.0;
			if (t >= 0.84 && t < 1.05) {
				double lt = t - 0.84;
				lubPhase += expSweep(62.0, 28.0, clamp01(lt / 0.15)) / SAMPLE_RATE;
				double env = smoothAttack(lt, 0.008) * Math.exp(-lt / 0.058);
				hb += Math.tanh(Math.sin(TWO_PI * lubPhase) * 1.8) * env * 1.05;
			}
			if (t >= 1.02 && t < 1.24) {
				double lt = t - 1.02;
				dubPhase += expSweep(72.0, 32.0, clamp01(lt / 0.13)) / SAMPLE_RATE;
				double env = smoothAttack(lt, 0.008) * Math.exp(-lt / 0.048);
				hb += Math.tanh(Math.sin(TWO_PI * dubPhase) * 1.7) * env * 0.85;
			}
			out += hbLp.process(hb);

			if (t >= 0.84) {
				double lt = t - 0.84;
				chimePhase += 880.0 / SAMPLE_RATE;
				double chimeEnv = smoothAttack(lt, 0.006) * Math.exp(-lt / 0.16);
				out += Math.sin(TWO_PI * chimePhase) * chimeEnv * 0.14;
			}

			buf[i] = out;
		}

		applySchroederReverb(buf, 0.20, 0.70, 0.36);
		return finalizeToPcm16(buf, 0.94, 1.25, 32, 320);
	}

	/**
	 * 12. {@code immunoblocker_inject} (0.82s):
	 * Pneumatic autoinjector CLICK-PSSSSSHHH! (latch click -> high-pass serum hiss ->
	 * soothing warm resolving C-major-9 synth pad chord).
	 */
	private static short[] synthImmunoblockerInject() {
		int n = samples(0.82);
		double[] buf = new double[n];
		Rng rng = new Rng(0x494D_0012L);

		Biquad hissHp = new Biquad();
		hissHp.setHighPass(2700.0, 0.85);
		Biquad hissBp = new Biquad();
		Biquad padLp = new Biquad();

		double clickA = 0.0;
		double clickB = 0.0;
		double springPhase = 0.0;
		// Resolving C-major-9 chord (C4, E4, G4, B4, D5)
		double[] chordFreqs = {261.63, 329.63, 392.00, 493.88, 587.33};
		double[] chordPhasesA = new double[chordFreqs.length];
		double[] chordPhasesB = new double[chordFreqs.length];

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double out = 0.0;

			// Autoinjector CLICK! (0.00..0.045s)
			if (t < 0.05) {
				clickA += 2150.0 / SAMPLE_RATE;
				clickB += 960.0 / SAMPLE_RATE;
				springPhase += expSweep(210.0, 85.0, clamp01(t / 0.035)) / SAMPLE_RATE;
				double env = smoothAttack(t, 0.0008) * Math.exp(-t / 0.011);
				out += (Math.sin(TWO_PI * clickA) * 0.50 + Math.sin(TWO_PI * clickB) * 0.45
						+ Math.sin(TWO_PI * springPhase) * 0.65) * env * 0.95;
			}

			// Pressurized serum PSSSSSHHH! (0.025..0.35s)
			if (t >= 0.025 && t < 0.37) {
				double lt = t - 0.025;
				double noise = rng.nextSigned();
				hissBp.setBandPass(expSweep(4900.0, 2100.0, clamp01(lt / 0.30)), 1.9);
				double hissEnv = smoothAttack(lt, 0.015) * Math.exp(-Math.max(0.0, lt - 0.04) / 0.092);
				out += (hissHp.process(noise) * 0.48 + hissBp.process(noise) * 0.68) * hissEnv * 0.62;
			}

			// Soothing warm resolving major-9th analog synth pad (0.16..0.82s)
			if (t >= 0.16) {
				double lt = t - 0.16;
				double padEnv = smoothAttack(lt, 0.12) * Math.exp(-Math.max(0.0, lt - 0.22) / 0.28) * fadeWindow(t, 0.64, 0.82);
				double sum = 0.0;
				for (int k = 0; k < chordFreqs.length; k++) {
					double dtA = chordFreqs[k] / SAMPLE_RATE;
					double dtB = (chordFreqs[k] * 1.0045) / SAMPLE_RATE;
					chordPhasesA[k] = wrap01(chordPhasesA[k] + dtA);
					chordPhasesB[k] = wrap01(chordPhasesB[k] + dtB);
					double warmVoice = 0.55 * Math.sin(TWO_PI * chordPhasesA[k])
							+ 0.25 * polySaw(chordPhasesA[k], dtA)
							+ 0.20 * polySaw(chordPhasesB[k], dtB);
					sum += warmVoice;
				}
				double cutoff = lt < 0.22
						? expSweep(900.0, 2400.0, lt / 0.22)
						: expSweep(2400.0, 880.0, clamp01((lt - 0.22) / 0.44));
				padLp.setLowPass(cutoff, 0.9);
				out += padLp.process(sum * 0.24) * padEnv * 0.95;
			}

			buf[i] = out;
		}

		applySchroederReverb(buf, 0.34, 0.80, 0.42);
		return finalizeToPcm16(buf, 0.90, 1.10, 22, 360);
	}

	/**
	 * 13. {@code cyberpsychosis_break} (3.00s — exact 60-tick Cyberpsychosis Entry Cutscene!):
	 * Signature Cyberpunk 2077 Relic Malfunction piercing tinnitus (4050 Hz) +
	 * distorted accelerating brain-throb bass (38 Hz) + chaotic bitcrushed glitch rips & stuttering static ->
	 * rising dissonant horror synth swell -> shattering sub-bass distortion boom at 2.75s!
	 */
	private static short[] synthCyberpsychosisBreak() {
		int n = samples(3.00);
		double[] buf = new double[n];
		Rng rng = new Rng(0xC7B3_0013L);

		Biquad horrorLp = new Biquad();
		Biquad glitchLp = new Biquad();
		glitchLp.setLowPass(8200.0, 0.9);
		Biquad shatterHp = new Biquad();
		shatterHp.setHighPass(2200.0, 0.9);

		// Glitch burst windows across Act I & Act II (increasing density and aggression)
		double[][] glitchWindows = {
				{0.24, 0.34}, {0.58, 0.67}, {0.92, 1.04}, {1.22, 1.33},
				{1.48, 1.60}, {1.72, 1.85}, {1.94, 2.08}, {2.16, 2.31},
				{2.38, 2.52}, {2.56, 2.72}
		};

		double tinPhaseA = 0.0;
		double tinPhaseB = 0.0;
		double bassPhase = 0.0;
		double throbLfoPhase = 0.0;
		double[] horrorBase = {110.0, 116.54, 155.56, 233.08}; // A2, Bb2 (m2), Eb3 (tritone), Bb3
		double[] horrorPhases = new double[horrorBase.length];
		double glitchCarPhase = 0.0;
		double glitchModPhase = 0.0;
		double heldGlitch = 0.0;
		int holdCounter = 0;

		double boomPhase = 0.0;
		double shatterHold = 0.0;
		int shatterCounter = 0;

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double out = 0.0;

			// Pre-climax (0.00..2.725s), followed by a brief 25ms dread vacuum before the 2.75s boom
			if (t < 2.728) {
				double cutFade = fadeWindow(t, 2.710, 2.728);

				// 1) Signature Cyberpunk 2077 Relic Malfunction piercing tinnitus (4050 Hz + slow beating twin)
				tinPhaseA += 4050.0 / SAMPLE_RATE;
				tinPhaseB += 4057.0 / SAMPLE_RATE;
				double tinEnv = smoothAttack(t, 0.14) * (0.72 + 0.28 * Math.sin(TWO_PI * 2.1 * t)) * cutFade;
				double tinnitus = (Math.sin(TWO_PI * tinPhaseA) * 0.65 + Math.sin(TWO_PI * tinPhaseB) * 0.35) * tinEnv * 0.15;
				out += tinnitus;

				// 2) Distorted brain-throb bass (38 Hz + 57 Hz) accelerating from 1.8 Hz -> 5.6 Hz
				double throbRate = 1.8 + 3.8 * Math.pow(clamp01(t / 2.72), 1.4);
				throbLfoPhase += throbRate / SAMPLE_RATE;
				double pulseShape = Math.pow(0.5 + 0.5 * Math.sin(TWO_PI * throbLfoPhase), 2.2);
				bassPhase += 38.0 / SAMPLE_RATE;
				double rawBass = Math.sin(TWO_PI * bassPhase) + 0.55 * Math.sin(TWO_PI * bassPhase * 1.5);
				double bassEnv = smoothAttack(t, 0.06) * (0.35 + 0.65 * pulseShape) * cutFade;
				out += Math.tanh(rawBass * (1.8 + 0.9 * clamp01(t / 2.72))) * bassEnv * 0.62;

				// 3) Rising dissonant industrial horror synth swell (1.00..2.725s)
				if (t >= 1.00) {
					double hp = clamp01((t - 1.00) / 1.72);
					double pitchRise = 1.0 + 0.58 * Math.pow(hp, 1.5);
					double sum = 0.0;
					for (int k = 0; k < horrorBase.length; k++) {
						double dt = (horrorBase[k] * pitchRise) / SAMPLE_RATE;
						horrorPhases[k] = wrap01(horrorPhases[k] + dt);
						sum += polySaw(horrorPhases[k], dt);
					}
					horrorLp.setLowPass(expSweep(320.0, 4400.0, hp), 3.0);
					double swellEnv = smoothAttack(t - 1.00, 0.18) * (0.18 + 0.82 * Math.pow(hp, 1.35)) * cutFade;
					out += Math.tanh(horrorLp.process(sum * 0.32) * 1.9) * swellEnv * 0.68;
				}

				// 4) Chaotic bitcrushed Relic glitch rips & stuttering static
				int activeWindow = -1;
				for (int w = 0; w < glitchWindows.length; w++) {
					if (t >= glitchWindows[w][0] && t <= glitchWindows[w][1]) {
						activeWindow = w;
						break;
					}
				}
				if (activeWindow >= 0) {
					double wt = t - glitchWindows[activeWindow][0];
					double wDur = glitchWindows[activeWindow][1] - glitchWindows[activeWindow][0];
					double intensity = 0.45 + 0.55 * ((double) activeWindow / (glitchWindows.length - 1));
					int decimate = Math.max(6, 19 - activeWindow);
					if (--holdCounter <= 0) {
						holdCounter = decimate;
						double gFreq = 420.0 + ((activeWindow * 317 + (int) (wt * 900.0)) % 1450);
						glitchCarPhase = wrap01(glitchCarPhase + (gFreq * decimate) / SAMPLE_RATE);
						glitchModPhase = wrap01(glitchModPhase + (137.0 * decimate) / SAMPLE_RATE);
						double fmSq = Math.sin(TWO_PI * glitchCarPhase + 3.4 * Math.sin(TWO_PI * glitchModPhase)) >= 0.0 ? 0.85 : -0.85;
						double raw = fmSq * 0.55 + rng.nextSigned() * 0.70;
						// 4-bit quantization crush
						heldGlitch = Math.round(raw * 7.0) / 7.0;
					}
					double stutter = ((int) (wt * 36.0) % 2 == 0) ? 1.0 : 0.25;
					double wEnv = smoothAttack(wt, 0.002) * fadeWindow(wt, wDur - 0.006, wDur);
					out += glitchLp.process(heldGlitch) * wEnv * stutter * intensity * 0.62;
				}
			}

			// Climax (2.75..3.00s): Shattering sub-bass distortion boom!
			if (t >= 2.75) {
				double lt = t - 2.75;
				boomPhase += expSweep(138.0, 24.0, clamp01(lt / 0.20)) / SAMPLE_RATE;
				double boomEnv = smoothAttack(lt, 0.0012) * Math.exp(-lt / 0.13) * fadeWindow(t, 2.94, 3.00);
				double subBoom = Math.tanh(Math.sin(TWO_PI * boomPhase) * 3.4) * boomEnv * 1.25;

				if (--shatterCounter <= 0) {
					shatterCounter = 7;
					shatterHold = Math.round(rng.nextSigned() * 5.0) / 5.0;
				}
				double shatterEnv = smoothAttack(lt, 0.001) * Math.exp(-lt / 0.095) * fadeWindow(t, 2.94, 3.00);
				double shatter = (shatterHp.process(rng.nextSigned()) * 0.65 + shatterHold * 0.55) * shatterEnv * 0.95;
				out += subBoom + shatter;
			}

			buf[i] = out;
		}

		applySchroederReverb(buf, 0.22, 0.74, 0.36);
		return finalizeToPcm16(buf, 0.95, 1.35, 32, 360);
	}

	/**
	 * 14. {@code cyberpsychosis_glitch} (0.22s):
	 * Sharp Relic Malfunction digital static tear (bitcrushed noise burst + FM square glitch rip).
	 */
	private static short[] synthCyberpsychosisGlitch() {
		int n = samples(0.22);
		double[] buf = new double[n];
		Rng rng = new Rng(0xC7B3_0014L);

		Biquad antiHarshLp = new Biquad();
		antiHarshLp.setLowPass(8600.0, 0.9);

		double carPhase = 0.0;
		double modPhase = 0.0;
		double held = 0.0;
		int holdCount = 0;

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			// 3 jagged micro-stutter slices
			boolean slice1 = t < 0.062;
			boolean slice2 = t >= 0.076 && t < 0.142;
			boolean slice3 = t >= 0.156 && t < 0.216;
			if (!slice1 && !slice2 && !slice3) {
				buf[i] = 0.0;
				continue;
			}

			int decimate = slice1 ? 11 : (slice2 ? 15 : 9);
			double fCar = slice1 ? 920.0 : (slice2 ? 1680.0 : 460.0);
			if (--holdCount <= 0) {
				holdCount = decimate;
				carPhase = wrap01(carPhase + (fCar * decimate) / SAMPLE_RATE);
				modPhase = wrap01(modPhase + (137.0 * decimate) / SAMPLE_RATE);
				double fmSq = Math.sin(TWO_PI * carPhase + 3.8 * Math.sin(TWO_PI * modPhase)) >= 0.0 ? 0.9 : -0.9;
				double raw = fmSq * 0.58 + rng.nextSigned() * 0.72;
				held = Math.round(raw * 6.0) / 6.0;
			}

			double env = slice1 ? fadeWindow(t, 0.056, 0.062)
					: (slice2 ? smoothAttack(t - 0.076, 0.0015) * fadeWindow(t, 0.136, 0.142)
					: smoothAttack(t - 0.156, 0.0015) * fadeWindow(t, 0.206, 0.216));
			buf[i] = antiHarshLp.process(held) * env;
		}

		return finalizeToPcm16(buf, 0.74, 1.30, 16, 120);
	}

	/**
	 * 15. {@code ui_hover} (0.04s):
	 * Ultra-subtle Cyberpunk 2077 Ripperdoc red-UI micro-tick (2600 -> 1900 Hz, 35ms).
	 */
	private static short[] synthUiHover() {
		int n = samples(0.040);
		double[] buf = new double[n];
		double phase = 0.0;

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double f = expSweep(2600.0, 1900.0, clamp01(t / 0.035));
			phase += f / SAMPLE_RATE;
			double env = smoothAttack(t, 0.0014) * Math.exp(-t / 0.0085) * fadeWindow(t, 0.032, 0.040);
			buf[i] = (Math.sin(TWO_PI * phase) + 0.14 * Math.sin(TWO_PI * phase * 2.0)) * env;
		}

		// Normalized softly so caller volume 0.7f feels crisp & unobtrusive like CP2077 menus.
		return finalizeToPcm16(buf, 0.38, 1.0, 24, 120);
	}

	/**
	 * 16. {@code ui_click} (0.075s):
	 * Crisp cyberdeck confirmation beep (1400 -> 2100 Hz).
	 */
	private static short[] synthUiClick() {
		int n = samples(0.075);
		double[] buf = new double[n];
		double phase = 0.0;

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double f = t < 0.018 ? 1400.0 : expSweep(1400.0, 2100.0, clamp01((t - 0.018) / 0.012));
			phase += f / SAMPLE_RATE;
			double env = smoothAttack(t, 0.0015) * Math.exp(-Math.max(0.0, t - 0.014) / 0.018) * fadeWindow(t, 0.062, 0.075);
			buf[i] = (Math.sin(TWO_PI * phase) + 0.18 * Math.sin(TWO_PI * phase * 2.0)) * env;
		}

		return finalizeToPcm16(buf, 0.55, 1.0, 24, 140);
	}

	/**
	 * 17a. {@code ui_install} (0.48s):
	 * Surgical servo lock-in chord when installing an implant (320 Hz thump + rising cyber-chime).
	 */
	private static short[] synthUiInstall() {
		int n = samples(0.48);
		double[] buf = new double[n];

		double thumpPhase = 0.0;
		double latchPhase = 0.0;
		double[] notes = {523.25, 659.25, 783.99, 1046.50};
		double[] noteStarts = {0.055, 0.095, 0.135, 0.175};
		double[] notePhases = new double[notes.length];

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double out = 0.0;

			// Surgical socket lock thump (320 -> 88 Hz) + micro latch snap
			if (t < 0.16) {
				thumpPhase += expSweep(320.0, 88.0, clamp01(t / 0.085)) / SAMPLE_RATE;
				latchPhase += 2400.0 / SAMPLE_RATE;
				double thumpEnv = smoothAttack(t, 0.0015) * Math.exp(-t / 0.036);
				double latchEnv = smoothAttack(t, 0.0008) * Math.exp(-t / 0.008);
				out += Math.tanh(Math.sin(TWO_PI * thumpPhase) * 1.55) * thumpEnv * 0.78
						+ Math.sin(TWO_PI * latchPhase) * latchEnv * 0.35;
			}

			// Rising cybernetic calibration chime
			for (int k = 0; k < notes.length; k++) {
				double lt = t - noteStarts[k];
				if (lt >= 0.0) {
					notePhases[k] += notes[k] / SAMPLE_RATE;
					double nEnv = smoothAttack(lt, 0.003) * Math.exp(-lt / 0.115) * fadeWindow(t, 0.40, 0.48);
					double fm = 0.25 * Math.exp(-lt / 0.04) * Math.sin(TWO_PI * notePhases[k] * 2.0);
					out += Math.sin(TWO_PI * notePhases[k] + fm) * nEnv * 0.34;
				}
			}

			buf[i] = out;
		}

		applySchroederReverb(buf, 0.22, 0.68, 0.32);
		return finalizeToPcm16(buf, 0.78, 1.10, 22, 240);
	}

	/**
	 * 17b. {@code ui_error} (0.26s):
	 * Harsh double-buzz error (185 Hz sawtooth x2) when capacity limit blocks an implant.
	 */
	private static short[] synthUiError() {
		int n = samples(0.26);
		double[] buf = new double[n];
		Biquad lp = new Biquad();
		lp.setLowPass(1550.0, 1.2);

		double pA = 0.0;
		double pB = 0.0;

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double env = 0.0;
			double fBase = 185.0;

			if (t < 0.095) {
				env = smoothAttack(t, 0.0025) * fadeWindow(t, 0.082, 0.095);
			} else if (t >= 0.130 && t < 0.255) {
				double lt = t - 0.130;
				fBase = 172.0;
				env = smoothAttack(lt, 0.0025) * fadeWindow(t, 0.232, 0.255);
			}

			if (env > 0.0) {
				double dtA = fBase / SAMPLE_RATE;
				double dtB = (fBase * 1.06) / SAMPLE_RATE; // harsh dissonant detune
				pA = wrap01(pA + dtA);
				pB = wrap01(pB + dtB);
				double raw = 0.55 * polySaw(pA, dtA) + 0.45 * polySaw(pB, dtB);
				buf[i] = Math.tanh(lp.process(raw) * 1.9) * env;
			}
		}

		return finalizeToPcm16(buf, 0.74, 1.15, 22, 160);
	}

	/**
	 * Bonus {@code ui_uninstall} (0.32s):
	 * Pneumatic socket unlatch + descending cyber-beep when removing an implant.
	 */
	private static short[] synthUiUninstall() {
		int n = samples(0.32);
		double[] buf = new double[n];
		Rng rng = new Rng(0x554E_0018L);

		Biquad ventHp = new Biquad();
		ventHp.setHighPass(2100.0, 0.85);
		double tonePhase = 0.0;
		double clackPhase = 0.0;

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double f = t < 0.09 ? 880.0 : (t < 0.18 ? 580.0 : 390.0);
			tonePhase += f / SAMPLE_RATE;
			clackPhase += expSweep(240.0, 95.0, clamp01(t / 0.06)) / SAMPLE_RATE;

			double toneEnv = smoothAttack(t, 0.003) * Math.exp(-t / 0.105) * fadeWindow(t, 0.26, 0.32);
			double clackEnv = smoothAttack(t, 0.001) * Math.exp(-t / 0.022);
			double puffEnv = smoothAttack(t, 0.004) * Math.exp(-t / 0.032);

			buf[i] = Math.sin(TWO_PI * tonePhase) * toneEnv * 0.58
					+ Math.sin(TWO_PI * clackPhase) * clackEnv * 0.52
					+ ventHp.process(rng.nextSigned()) * puffEnv * 0.26;
		}

		applySchroederReverb(buf, 0.16, 0.60, 0.35);
		return finalizeToPcm16(buf, 0.72, 1.05, 22, 180);
	}

	/**
	 * Bonus {@code ui_open} (0.38s):
	 * Ripperdoc neural interface handshake boot sweep.
	 */
	private static short[] synthUiOpen() {
		int n = samples(0.38);
		double[] buf = new double[n];

		double[] notes = {440.0, 660.0, 880.0, 1320.0};
		double[] phases = new double[notes.length];
		double humPhase = 0.0;

		for (int i = 0; i < n; i++) {
			double t = (double) i / SAMPLE_RATE;
			double out = 0.0;

			humPhase += expSweep(110.0, 220.0, clamp01(t / 0.25)) / SAMPLE_RATE;
			double humEnv = smoothAttack(t, 0.03) * Math.exp(-t / 0.14) * fadeWindow(t, 0.30, 0.38);
			out += Math.sin(TWO_PI * humPhase) * humEnv * 0.26;

			for (int k = 0; k < notes.length; k++) {
				double lt = t - k * 0.038;
				if (lt >= 0.0) {
					phases[k] += notes[k] / SAMPLE_RATE;
					double nEnv = smoothAttack(lt, 0.003) * Math.exp(-lt / 0.095) * fadeWindow(t, 0.31, 0.38);
					out += Math.sin(TWO_PI * phases[k] + 0.2 * Math.sin(TWO_PI * phases[k] * 2.0)) * nEnv * 0.32;
				}
			}

			buf[i] = out;
		}

		applySchroederReverb(buf, 0.22, 0.68, 0.32);
		return finalizeToPcm16(buf, 0.70, 1.05, 32, 220);
	}

	// ================================================================================================
	// Core DSP building blocks (filters, PolyBLEP oscillators, Schroeder reverb, mastering)
	// ================================================================================================

	private static int samples(double seconds) {
		return Math.max(1, (int) Math.round(seconds * SAMPLE_RATE));
	}

	private static double clamp(double v, double min, double max) {
		return v < min ? min : Math.min(v, max);
	}

	private static double clamp01(double v) {
		return v < 0.0 ? 0.0 : Math.min(v, 1.0);
	}

	private static double wrap01(double phase) {
		phase -= Math.floor(phase);
		return phase < 0.0 ? phase + 1.0 : phase;
	}

	private static double expSweep(double fStart, double fEnd, double normT) {
		return fStart * Math.pow(fEnd / fStart, clamp01(normT));
	}

	private static double smoothAttack(double t, double attackSeconds) {
		if (attackSeconds <= 0.0 || t >= attackSeconds) {
			return t < 0.0 ? 0.0 : 1.0;
		}
		if (t <= 0.0) {
			return 0.0;
		}
		double x = t / attackSeconds;
		return 0.5 - 0.5 * Math.cos(Math.PI * x);
	}

	private static double fadeWindow(double t, double fadeStart, double fadeEnd) {
		if (t <= fadeStart) {
			return 1.0;
		}
		if (t >= fadeEnd) {
			return 0.0;
		}
		double x = (t - fadeStart) / (fadeEnd - fadeStart);
		return 0.5 + 0.5 * Math.cos(Math.PI * x);
	}

	/** PolyBLEP correction for anti-aliased sawtooth / square oscillators. */
	private static double polyBlep(double t, double dt) {
		if (dt <= 0.0) {
			return 0.0;
		}
		if (t < dt) {
			double u = t / dt;
			return u + u - u * u - 1.0;
		}
		if (t > 1.0 - dt) {
			double u = (t - 1.0) / dt;
			return u * u + u + u + 1.0;
		}
		return 0.0;
	}

	/** Band-limited anti-aliased sawtooth in [-1, 1]. */
	private static double polySaw(double phase01, double dt) {
		return (2.0 * phase01 - 1.0) - polyBlep(phase01, dt);
	}

	/**
	 * Schroeder algorithmic reverb: 4 parallel low-pass-damped comb filters at prime delay lengths
	 * feeding 2 series allpass diffusers. Mixes smoothly in-place at {@code wet} ratio.
	 */
	private static void applySchroederReverb(double[] buf, double wet, double feedback, double damping) {
		int[] combLengths = {1559, 1699, 1847, 2017};
		double[][] combBufs = new double[combLengths.length][];
		int[] combIdx = new int[combLengths.length];
		double[] combLp = new double[combLengths.length];
		for (int c = 0; c < combLengths.length; c++) {
			combBufs[c] = new double[combLengths[c]];
		}

		int[] apLengths = {241, 347};
		double[][] apBufs = new double[apLengths.length][];
		int[] apIdx = new int[apLengths.length];
		for (int a = 0; a < apLengths.length; a++) {
			apBufs[a] = new double[apLengths[a]];
		}

		double dry = 1.0 - wet * 0.45;
		double dampInv = 1.0 - damping;

		for (int i = 0; i < buf.length; i++) {
			double in = buf[i];
			double sum = 0.0;
			for (int c = 0; c < combLengths.length; c++) {
				int idx = combIdx[c];
				double delayed = combBufs[c][idx];
				combLp[c] = delayed * dampInv + combLp[c] * damping;
				combBufs[c][idx] = in + combLp[c] * feedback;
				combIdx[c] = (idx + 1 == combLengths[c]) ? 0 : idx + 1;
				sum += delayed;
			}
			double diffused = sum * 0.25;
			for (int a = 0; a < apLengths.length; a++) {
				int idx = apIdx[a];
				double delayed = apBufs[a][idx];
				double out = -diffused + delayed;
				apBufs[a][idx] = diffused + delayed * 0.5;
				apIdx[a] = (idx + 1 == apLengths[a]) ? 0 : idx + 1;
				diffused = out;
			}
			buf[i] = in * dry + diffused * wet;
		}
	}

	/**
	 * Final mastering pass: DC offset removal, optional tanh analog saturation, anti-click edge fades,
	 * peak normalization to {@code targetPeak}, and 16-bit signed PCM quantization.
	 */
	private static short[] finalizeToPcm16(double[] buf, double targetPeak, double satDrive, int fadeInSamples, int fadeOutSamples) {
		int n = buf.length;

		// 1) 1st-order DC blocker
		double prevIn = 0.0;
		double prevOut = 0.0;
		double maxAbs = 0.0;
		for (int i = 0; i < n; i++) {
			double x = Double.isFinite(buf[i]) ? buf[i] : 0.0;
			double y = x - prevIn + 0.996 * prevOut;
			prevIn = x;
			prevOut = y;
			buf[i] = y;
			double abs = Math.abs(y);
			if (abs > maxAbs) {
				maxAbs = abs;
			}
		}

		// 2) Normalize & apply gentle mastering saturation + cosine edge fades
		short[] pcm = new short[n];
		if (maxAbs < 1.0e-9) {
			return pcm;
		}
		double normScale = 1.0 / maxAbs;
		double tanhDenom = satDrive > 1.0 ? Math.tanh(satDrive) : 1.0;

		for (int i = 0; i < n; i++) {
			double v = buf[i] * normScale;
			if (satDrive > 1.0) {
				v = Math.tanh(v * satDrive) / tanhDenom;
			}
			if (fadeInSamples > 0 && i < fadeInSamples) {
				double x = (double) i / fadeInSamples;
				v *= 0.5 - 0.5 * Math.cos(Math.PI * x);
			}
			if (fadeOutSamples > 0 && i >= n - fadeOutSamples) {
				double x = (double) (n - 1 - i) / fadeOutSamples;
				v *= 0.5 - 0.5 * Math.cos(Math.PI * x);
			}
			v = clamp(v * targetPeak, -1.0, 1.0);
			pcm[i] = (short) Math.round(v * 32767.0);
		}
		return pcm;
	}

	/** 2nd-order Audio EQ Cookbook Biquad filter (Direct Form II Transposed, modulation-stable). */
	private static final class Biquad {
		private double b0 = 1.0;
		private double b1 = 0.0;
		private double b2 = 0.0;
		private double a1 = 0.0;
		private double a2 = 0.0;
		private double z1 = 0.0;
		private double z2 = 0.0;

		void setLowPass(double freq, double q) {
			double f = clamp(freq, 20.0, SAMPLE_RATE * 0.45);
			double safeQ = clamp(q, 0.5, 16.0);
			double w0 = TWO_PI * f / SAMPLE_RATE;
			double cosW0 = Math.cos(w0);
			double alpha = Math.sin(w0) / (2.0 * safeQ);
			double invA0 = 1.0 / (1.0 + alpha);
			this.b0 = ((1.0 - cosW0) * 0.5) * invA0;
			this.b1 = (1.0 - cosW0) * invA0;
			this.b2 = ((1.0 - cosW0) * 0.5) * invA0;
			this.a1 = (-2.0 * cosW0) * invA0;
			this.a2 = (1.0 - alpha) * invA0;
		}

		void setHighPass(double freq, double q) {
			double f = clamp(freq, 20.0, SAMPLE_RATE * 0.45);
			double safeQ = clamp(q, 0.5, 16.0);
			double w0 = TWO_PI * f / SAMPLE_RATE;
			double cosW0 = Math.cos(w0);
			double alpha = Math.sin(w0) / (2.0 * safeQ);
			double invA0 = 1.0 / (1.0 + alpha);
			this.b0 = ((1.0 + cosW0) * 0.5) * invA0;
			this.b1 = -(1.0 + cosW0) * invA0;
			this.b2 = ((1.0 + cosW0) * 0.5) * invA0;
			this.a1 = (-2.0 * cosW0) * invA0;
			this.a2 = (1.0 - alpha) * invA0;
		}

		void setBandPass(double freq, double q) {
			double f = clamp(freq, 20.0, SAMPLE_RATE * 0.45);
			double safeQ = clamp(q, 0.5, 16.0);
			double w0 = TWO_PI * f / SAMPLE_RATE;
			double cosW0 = Math.cos(w0);
			double alpha = Math.sin(w0) / (2.0 * safeQ);
			double invA0 = 1.0 / (1.0 + alpha);
			this.b0 = alpha * invA0;
			this.b1 = 0.0;
			this.b2 = -alpha * invA0;
			this.a1 = (-2.0 * cosW0) * invA0;
			this.a2 = (1.0 - alpha) * invA0;
		}

		double process(double x) {
			double y = b0 * x + z1;
			z1 = b1 * x - a1 * y + z2;
			z2 = b2 * x - a2 * y;
			if (!Double.isFinite(y)) {
				z1 = 0.0;
				z2 = 0.0;
				return 0.0;
			}
			return y;
		}
	}

	/** Deterministic XorShift64 PRNG for repeatable, clean DSP noise & crackle synthesis. */
	private static final class Rng {
		private long state;

		Rng(long seed) {
			this.state = seed == 0L ? 0x9E3779B97F4A7C15L : seed;
		}

		private long nextLong() {
			long x = state;
			x ^= (x << 13);
			x ^= (x >>> 7);
			x ^= (x << 17);
			state = x;
			return x;
		}

		/** Uniform random double in [0.0, 1.0). */
		double nextUnit() {
			return (nextLong() >>> 11) * (1.0 / (1L << 53));
		}

		/** Uniform random double in [-1.0, 1.0]. */
		double nextSigned() {
			return nextUnit() * 2.0 - 1.0;
		}
	}
}
