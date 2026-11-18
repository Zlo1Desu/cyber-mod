package name.cyber_zlo1.cyberware;

import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;

import java.util.List;

public class SandevistanItem extends CyberwareItem {
	public SandevistanItem(Settings settings) {
		super(CyberwareSlotType.OPERATING_SYSTEM, 35, settings.maxCount(1).rarity(Rarity.EPIC));
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(Text.translatable("item.cyber.sandevistan.desc").formatted(Formatting.AQUA));
		tooltip.add(Text.translatable(getSlotType().getTranslationKey()).formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("Capacity: " + getCyberCapacityCost()).formatted(Formatting.DARK_AQUA));
		tooltip.add(Text.literal("Duration: 8s | Cooldown: 20s").formatted(Formatting.GOLD));
	}
}
