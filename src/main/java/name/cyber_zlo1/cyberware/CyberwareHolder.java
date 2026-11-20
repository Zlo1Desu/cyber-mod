package name.cyber_zlo1.cyberware;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;

public interface CyberwareHolder {
	SimpleInventory cyber$getCyberwareInventory();

	int cyber$getSandevistanActiveTicks();

	void cyber$setSandevistanActiveTicks(int ticks);

	int cyber$getSandevistanCooldownTicks();

	void cyber$setSandevistanCooldownTicks(int ticks);

	boolean cyber$hasSandevistanEquipped();

	boolean cyber$hasImplantInSlot(CyberwareSlotType slot, Item item);

	boolean cyber$hasKiroshiEquipped();

	boolean cyber$hasReinforcedTendonsEquipped();

	int cyber$getSecondHeartCooldown();

	void cyber$setSecondHeartCooldown(int ticks);

	int cyber$getTotalEquippedCapacity();

	boolean cyber$hasMantisBladesEquipped();

	int cyber$getCyberpsychosis();

	void cyber$setCyberpsychosis(int value);

	int cyber$getImmunoblockerTicks();

	void cyber$setImmunoblockerTicks(int ticks);

	boolean cyber$isCyberpsychoRampage();
}
