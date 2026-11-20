package name.cyber_zlo1.cyberware;

import name.cyber_zlo1.network.CyberwareNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

public class ImmunoblockerItem extends Item {
	public ImmunoblockerItem(Settings settings) {
		super(settings.maxCount(16).rarity(Rarity.RARE));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient && user instanceof CyberwareHolder holder) {
			holder.cyber$setCyberpsychosis(Math.max(0, holder.cyber$getCyberpsychosis() - 450));
			holder.cyber$setImmunoblockerTicks(600);
			world.playSound(null, user.getX(), user.getY(), user.getZ(),
					SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.8f, 1.6f);
			world.playSound(null, user.getX(), user.getY(), user.getZ(),
					SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.7f, 1.8f);
			if (user instanceof ServerPlayerEntity sp) {
				CyberwareNetworking.syncState(sp);
			}
		}
		user.getItemCooldownManager().set(this, 60);
		if (!user.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(Text.translatable("item.cyber.immunoblocker.desc").formatted(Formatting.GREEN));
	}
}
