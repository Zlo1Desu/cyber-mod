package name.cyber_zlo1;

import name.cyber_zlo1.cyberware.CyberwareHolder;
import name.cyber_zlo1.cyberware.CyberwareSlotType;
import name.cyber_zlo1.cyberware.ImmunoblockerItem;
import name.cyber_zlo1.cyberware.PassiveCyberwareItem;
import name.cyber_zlo1.cyberware.SandevistanItem;
import name.cyber_zlo1.network.CyberwareNetworking;
import name.cyber_zlo1.screen.CyberwareScreenHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cyber implements ModInitializer {
	public static final String MOD_ID = "cyber";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ScreenHandlerType<CyberwareScreenHandler> CYBERWARE_SCREEN_HANDLER = Registry.register(
			Registries.SCREEN_HANDLER,
			id("cyberware"),
			new ScreenHandlerType<>(CyberwareScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
	);

	public static final Item SANDEVISTAN = Registry.register(
			Registries.ITEM,
			id("sandevistan"),
			new SandevistanItem(new Item.Settings())
	);

	public static final Item KIROSHI_OPTICS = Registry.register(
			Registries.ITEM,
			id("kiroshi_optics"),
			new PassiveCyberwareItem(CyberwareSlotType.OCULAR, 15, "item.cyber.kiroshi_optics.desc", new Item.Settings())
	);

	public static final Item REINFORCED_TENDONS = Registry.register(
			Registries.ITEM,
			id("reinforced_tendons"),
			new PassiveCyberwareItem(CyberwareSlotType.LEGS, 20, "item.cyber.reinforced_tendons.desc", new Item.Settings())
	);

	public static final Item SECOND_HEART = Registry.register(
			Registries.ITEM,
			id("second_heart"),
			new PassiveCyberwareItem(CyberwareSlotType.CIRCULATORY, 30, "item.cyber.second_heart.desc", new Item.Settings())
	);

	public static final Item MANTIS_BLADES = Registry.register(
			Registries.ITEM,
			id("mantis_blades"),
			new PassiveCyberwareItem(CyberwareSlotType.ARMS, 25, "item.cyber.mantis_blades.desc", new Item.Settings())
	);

	public static final Item KERENZIKOV = Registry.register(
			Registries.ITEM,
			id("kerenzikov"),
			new PassiveCyberwareItem(CyberwareSlotType.NERVOUS, 20, "item.cyber.kerenzikov.desc", new Item.Settings())
	);

	public static final Item TITANIUM_BONES = Registry.register(
			Registries.ITEM,
			id("titanium_bones"),
			new PassiveCyberwareItem(CyberwareSlotType.SKELETON, 15, "item.cyber.titanium_bones.desc", new Item.Settings())
	);

	public static final Item IMMUNOBLOCKER = Registry.register(
			Registries.ITEM,
			id("immunoblocker"),
			new ImmunoblockerItem(new Item.Settings())
	);

	public static final ItemGroup CYBER_GROUP = Registry.register(
			Registries.ITEM_GROUP,
			id("cyber_group"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(SANDEVISTAN))
					.displayName(Text.translatable("itemGroup.cyber.cyber_group"))
					.entries((ctx, entries) -> {
						entries.add(SANDEVISTAN);
						entries.add(KIROSHI_OPTICS);
						entries.add(REINFORCED_TENDONS);
						entries.add(SECOND_HEART);
						entries.add(MANTIS_BLADES);
						entries.add(KERENZIKOV);
						entries.add(TITANIUM_BONES);
						entries.add(IMMUNOBLOCKER);
					})
					.build()
	);

	@Override
	public void onInitialize() {
		CyberwareNetworking.registerCommon();

		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			CyberwareHolder oldHolder = (CyberwareHolder) oldPlayer;
			CyberwareHolder newHolder = (CyberwareHolder) newPlayer;
			SimpleInventory oldInv = oldHolder.cyber$getCyberwareInventory();
			SimpleInventory newInv = newHolder.cyber$getCyberwareInventory();
			for (int i = 0; i < oldInv.size(); i++) {
				newInv.setStack(i, oldInv.getStack(i).copy());
			}
			if (alive) {
				newHolder.cyber$setSandevistanActiveTicks(oldHolder.cyber$getSandevistanActiveTicks());
				newHolder.cyber$setSandevistanCooldownTicks(oldHolder.cyber$getSandevistanCooldownTicks());
				newHolder.cyber$setSecondHeartCooldown(oldHolder.cyber$getSecondHeartCooldown());
				newHolder.cyber$setCyberpsychosis(oldHolder.cyber$getCyberpsychosis());
				newHolder.cyber$setImmunoblockerTicks(oldHolder.cyber$getImmunoblockerTicks());
			} else {
				newHolder.cyber$setSandevistanActiveTicks(0);
				newHolder.cyber$setSandevistanCooldownTicks(0);
				newHolder.cyber$setSecondHeartCooldown(0);
				newHolder.cyber$setCyberpsychosis(0);
				newHolder.cyber$setImmunoblockerTicks(0);
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				CyberwareNetworking.syncState(handler.player)
		);
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
