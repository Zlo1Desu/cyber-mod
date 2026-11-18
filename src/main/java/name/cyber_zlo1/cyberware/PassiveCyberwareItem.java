package name.cyber_zlo1.cyberware;

import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;

import java.util.List;

public class PassiveCyberwareItem extends CyberwareItem {
	private final String descKey;

	public PassiveCyberwareItem(CyberwareSlotType slotType, int capacityCost, String descKey, Settings settings) {
		super(slotType, capacityCost, settings.maxCount(1).rarity(Rarity.RARE));
		this.descKey = descKey;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(Text.translatable(descKey).formatted(Formatting.AQUA));
		tooltip.add(Text.translatable(getSlotType().getTranslationKey()).formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("Capacity: " + getCyberCapacityCost()).formatted(Formatting.DARK_AQUA));
	}
}
