package name.cyber_zlo1.cyberware;

import net.minecraft.item.Item;

public class CyberwareItem extends Item {
	private final CyberwareSlotType slotType;
	private final int capacityCost;

	public CyberwareItem(CyberwareSlotType slotType, int capacityCost, Settings settings) {
		super(settings);
		this.slotType = slotType;
		this.capacityCost = capacityCost;
	}

	public CyberwareSlotType getSlotType() {
		return slotType;
	}

	public int getCyberCapacityCost() {
		return capacityCost;
	}
}
