package name.cyber_zlo1.screen;

import name.cyber_zlo1.Cyber;
import name.cyber_zlo1.cyberware.CyberwareItem;
import name.cyber_zlo1.cyberware.CyberwareSlotType;
import name.cyber_zlo1.cyberware.CyberwareState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class CyberwareScreenHandler extends ScreenHandler {
	public static final int GUI_WIDTH = 222;
	public static final int GUI_HEIGHT = 240;

	private final Inventory cyberwareInventory;

	public CyberwareScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(10));
	}

	public CyberwareScreenHandler(int syncId, PlayerInventory playerInventory, Inventory cyberwareInventory) {
		super(Cyber.CYBERWARE_SCREEN_HANDLER, syncId);
		checkSize(cyberwareInventory, 10);
		this.cyberwareInventory = cyberwareInventory;
		cyberwareInventory.onOpen(playerInventory.player);

		for (CyberwareSlotType type : CyberwareSlotType.values()) {
			this.addSlot(new Slot(cyberwareInventory, type.getIndex(), type.getSlotX(), type.getSlotY()) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return stack.getItem() instanceof CyberwareItem ci
							&& ci.getSlotType() == type
							&& canFitImplant(type.getIndex(), ci);
				}

				@Override
				public int getMaxItemCount() {
					return 1;
				}
			});
		}

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 31 + col * 18, 158 + row * 18));
			}
		}

		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, 31 + col * 18, 216));
		}
	}

	public Inventory getCyberwareInventory() {
		return cyberwareInventory;
	}

	public int getTotalEquippedCapacity() {
		return getTotalEquippedCapacityExcluding(-1);
	}

	public int getTotalEquippedCapacityExcluding(int excludedSlotIndex) {
		int total = 0;
		for (int i = 0; i < 10; i++) {
			if (i == excludedSlotIndex) {
				continue;
			}
			ItemStack stack = cyberwareInventory.getStack(i);
			if (!stack.isEmpty() && stack.getItem() instanceof CyberwareItem ci) {
				total += ci.getCyberCapacityCost();
			}
		}
		return total;
	}

	public boolean canFitImplant(int slotIndex, CyberwareItem item) {
		return getTotalEquippedCapacityExcluding(slotIndex) + item.getCyberCapacityCost() <= CyberwareState.MAX_CYBERWARE_CAPACITY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return this.cyberwareInventory.canPlayerUse(player);
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		this.cyberwareInventory.onClose(player);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slotIndex) {
		ItemStack newStack = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);
		if (slot != null && slot.hasStack()) {
			ItemStack originalStack = slot.getStack();
			newStack = originalStack.copy();
			if (slotIndex < 10) {
				if (!this.insertItem(originalStack, 10, 46, true)) {
					return ItemStack.EMPTY;
				}
			} else {
				boolean moved = false;
				if (originalStack.getItem() instanceof CyberwareItem ci) {
					int targetIndex = ci.getSlotType().getIndex();
					if (this.insertItem(originalStack, targetIndex, targetIndex + 1, false)) {
						moved = true;
					}
				}
				if (!moved) {
					if (slotIndex < 37) {
						if (!this.insertItem(originalStack, 37, 46, false)) {
							return ItemStack.EMPTY;
						}
					} else if (!this.insertItem(originalStack, 10, 37, false)) {
						return ItemStack.EMPTY;
					}
				}
			}

			if (originalStack.isEmpty()) {
				slot.setStack(ItemStack.EMPTY);
			} else {
				slot.markDirty();
			}

			if (originalStack.getCount() == newStack.getCount()) {
				return ItemStack.EMPTY;
			}

			slot.onTakeItem(player, originalStack);
		}
		return newStack;
	}
}
