package name.cyber_zlo1.cyberware;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

public final class CyberwareState {
	public static final int MAX_ACTIVE_TICKS = 160;
	public static final int MAX_COOLDOWN_TICKS = 400;
	public static final float TIME_DILATION_FACTOR = 0.04f;
	public static final int MAX_CYBERWARE_CAPACITY = 100;
	public static final int SAFE_CAPACITY_THRESHOLD = 75;
	public static final int MAX_CYBERPSYCHOSIS = 1000;

	private static int activeTicks;
	private static int cooldownTicks;
	private static boolean sandevistanEquipped;
	private static boolean kiroshiEquipped;
	private static boolean reinforcedTendonsEquipped;
	private static int secondHeartCooldown;
	private static boolean mantisBladesEquipped;
	private static int cyberpsychosis;
	private static boolean cyberpsychoRampage;
	private static int totalCapacity;

	private CyberwareState() {
	}

	public static void update(int activeTicks, int cooldownTicks, boolean equipped) {
		CyberwareState.activeTicks = activeTicks;
		CyberwareState.cooldownTicks = cooldownTicks;
		CyberwareState.sandevistanEquipped = equipped;
	}

	public static void updateExtended(
			int activeTicks,
			int cooldownTicks,
			boolean sandevistanEquipped,
			boolean kiroshiEquipped,
			boolean reinforcedTendonsEquipped,
			int secondHeartCooldown
	) {
		CyberwareState.activeTicks = activeTicks;
		CyberwareState.cooldownTicks = cooldownTicks;
		CyberwareState.sandevistanEquipped = sandevistanEquipped;
		CyberwareState.kiroshiEquipped = kiroshiEquipped;
		CyberwareState.reinforcedTendonsEquipped = reinforcedTendonsEquipped;
		CyberwareState.secondHeartCooldown = secondHeartCooldown;
	}

	public static void updateFull(
			int activeTicks,
			int cooldownTicks,
			boolean sandevistanEquipped,
			boolean kiroshiEquipped,
			boolean reinforcedTendonsEquipped,
			int secondHeartCooldown,
			boolean mantisBladesEquipped,
			int cyberpsychosis,
			boolean cyberpsychoRampage,
			int totalCapacity
	) {
		CyberwareState.activeTicks = activeTicks;
		CyberwareState.cooldownTicks = cooldownTicks;
		CyberwareState.sandevistanEquipped = sandevistanEquipped;
		CyberwareState.kiroshiEquipped = kiroshiEquipped;
		CyberwareState.reinforcedTendonsEquipped = reinforcedTendonsEquipped;
		CyberwareState.secondHeartCooldown = secondHeartCooldown;
		CyberwareState.mantisBladesEquipped = mantisBladesEquipped;
		CyberwareState.cyberpsychosis = cyberpsychosis;
		CyberwareState.cyberpsychoRampage = cyberpsychoRampage;
		CyberwareState.totalCapacity = totalCapacity;
	}

	public static int getActiveTicks() {
		return activeTicks;
	}

	public static int getCooldownTicks() {
		return cooldownTicks;
	}

	public static boolean isSandevistanEquipped() {
		return sandevistanEquipped;
	}

	public static boolean isActive() {
		return activeTicks > 0;
	}

	public static boolean isKiroshiEquipped() {
		return kiroshiEquipped;
	}

	public static boolean isReinforcedTendonsEquipped() {
		return reinforcedTendonsEquipped;
	}

	public static int getSecondHeartCooldown() {
		return secondHeartCooldown;
	}

	public static boolean isMantisBladesEquipped() {
		return mantisBladesEquipped;
	}

	public static int getCyberpsychosis() {
		return cyberpsychosis;
	}

	public static boolean isCyberpsychoRampage() {
		return cyberpsychoRampage;
	}

	public static int getTotalCapacity() {
		return totalCapacity;
	}

	public static boolean hasPlayerMantisBlades(PlayerEntity player) {
		return (player instanceof CyberwareHolder h && h.cyber$hasMantisBladesEquipped())
				|| (player != null && player.getWorld().isClient && isMantisBladesEquipped());
	}

	public static boolean isWorldTimeDilated(World world) {
		if (world == null) {
			return false;
		}
		if (world.isClient && isActive()) {
			return true;
		}
		for (PlayerEntity player : world.getPlayers()) {
			if (player instanceof CyberwareHolder holder && holder.cyber$getSandevistanActiveTicks() > 0) {
				return true;
			}
		}
		return false;
	}

	public static boolean isEntityImmuneToDilation(Entity entity) {
		return entity instanceof PlayerEntity p
				&& (((CyberwareHolder) p).cyber$getSandevistanActiveTicks() > 0 || (p.getWorld().isClient && isActive()));
	}
}
