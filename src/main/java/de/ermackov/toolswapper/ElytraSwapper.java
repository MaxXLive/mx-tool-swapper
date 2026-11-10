package de.ermackov.toolswapper;

import de.ermackov.toolswapper.config.ToolSwapperConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Puts an elytra into the chest slot when jumping in mid-air and swaps the previously worn chest item back after landing.
 * The elytra is equipped at the start of the tick, so vanilla LocalPlayer#aiStep starts gliding in the same tick.
 */
public final class ElytraSwapper {
	private static final int NO_SLOT = -1;
	private static final int CHEST_MENU_SLOT = InventoryMenu.ARMOR_SLOT_START + 1;

	private static boolean wasJumpDown = false;
	private static boolean autoEquipped = false;
	private static boolean chestWasEmpty = false;
	private static int returnSlot = NO_SLOT;

	private ElytraSwapper() {
	}

	public static void onStartTick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean jumpDown = minecraft.options.keyJump.isDown();
		boolean jumpPressed = jumpDown && !wasJumpDown;
		wasJumpDown = jumpDown;

		if (player == null) {
			reset();
			return;
		}

		ToolSwapperConfig config = ToolSwapperConfig.get();
		if (!config.enabled || player.isCreative() || player.isSpectator() || !canClickInventory(player)) {
			return;
		}

		if (jumpPressed && config.elytraSwap && canStartGliding(player)) {
			equipElytra(player);
		} else if (autoEquipped && !player.isFallFlying() && (player.onGround() || player.isInWater())) {
			if (config.elytraSwapBack) {
				unequipElytra(player);
			}
			autoEquipped = false;
		}
	}

	public static void reset() {
		autoEquipped = false;
		returnSlot = NO_SLOT;
	}

	private static boolean canStartGliding(LocalPlayer player) {
		// mirrors the checks vanilla does before Player#tryToStartFallFlying
		return !player.onGround() && !player.isFallFlying() && !player.isInWater() && !player.isPassenger() && !player.onClimbable()
				&& !player.getAbilities().flying && !player.hasEffect(MobEffects.LEVITATION)
				&& !LivingEntity.canGlideUsing(player.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST);
	}

	private static void equipElytra(LocalPlayer player) {
		ItemStack chestItem = player.getItemBySlot(EquipmentSlot.CHEST);
		if (EnchantmentHelper.has(chestItem, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
			return;
		}

		int elytraSlot = findElytraSlot(player.getInventory());
		if (elytraSlot == NO_SLOT) {
			return;
		}

		chestWasEmpty = chestItem.isEmpty();
		swapWithChest(player, elytraSlot);
		returnSlot = elytraSlot;
		autoEquipped = true;
	}

	private static void unequipElytra(LocalPlayer player) {
		ItemStack chestItem = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!chestItem.has(DataComponents.GLIDER) || EnchantmentHelper.has(chestItem, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
			return;
		}

		Inventory inventory = player.getInventory();
		int armorSlot = returnSlot != NO_SLOT && isChestArmor(inventory.getItem(returnSlot)) ? returnSlot : findChestArmorSlot(inventory);
		if (armorSlot != NO_SLOT) {
			swapWithChest(player, armorSlot);
		} else if (chestWasEmpty) {
			MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
			if (gameMode != null) {
				gameMode.handleContainerInput(player.inventoryMenu.containerId, CHEST_MENU_SLOT, 0, ContainerInput.QUICK_MOVE, player);
			}
		}
		returnSlot = NO_SLOT;
	}

	private static int findElytraSlot(Inventory inventory) {
		int bestSlot = NO_SLOT;
		int bestDurability = -1;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!LivingEntity.canGlideUsing(stack, EquipmentSlot.CHEST)) {
				continue;
			}
			int durability = stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
			if (durability > bestDurability) {
				bestDurability = durability;
				bestSlot = slot;
			}
		}
		return bestSlot;
	}

	private static int findChestArmorSlot(Inventory inventory) {
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			if (isChestArmor(inventory.getItem(slot))) {
				return slot;
			}
		}
		return NO_SLOT;
	}

	private static boolean isChestArmor(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot() == EquipmentSlot.CHEST && !stack.has(DataComponents.GLIDER);
	}

	private static void swapWithChest(LocalPlayer player, int inventorySlot) {
		MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
		if (gameMode == null) {
			return;
		}
		int containerId = player.inventoryMenu.containerId;
		int menuSlot = Inventory.isHotbarSlot(inventorySlot) ? InventoryMenu.USE_ROW_SLOT_START + inventorySlot : inventorySlot;
		// pick up the item, swap it with the chest slot item and put that one down where the first item was
		gameMode.handleContainerInput(containerId, menuSlot, 0, ContainerInput.PICKUP, player);
		gameMode.handleContainerInput(containerId, CHEST_MENU_SLOT, 0, ContainerInput.PICKUP, player);
		gameMode.handleContainerInput(containerId, menuSlot, 0, ContainerInput.PICKUP, player);
	}

	private static boolean canClickInventory(LocalPlayer player) {
		return player.containerMenu == player.inventoryMenu && player.containerMenu.getCarried().isEmpty();
	}
}
