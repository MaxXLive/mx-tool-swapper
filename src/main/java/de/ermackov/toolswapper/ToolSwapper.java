package de.ermackov.toolswapper;

import de.ermackov.toolswapper.config.ToolSwapperConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Connects {@link ToolPicker} to the local player: decides when a swap may happen, hands the inventory to the picker
 * and moves the picked item into the main hand with regular inventory clicks, so it also works on servers without
 * the mod.
 */
public final class ToolSwapper {
	private static final ToolPicker PICKER = new ToolPicker();

	private ToolSwapper() {
	}

	public static void onBlockClick(LocalPlayer player, Level level, BlockPos pos) {
		if (!ToolSwapperConfig.get().enabled || player.isCreative() || player.isSpectator() || !canClickInventory(player)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		Inventory inventory = player.getInventory();
		int slot = PICKER.pickTool(contents(inventory), inventory.getSelectedSlot(), state, state.getDestroySpeed(level, pos),
				getEfficiency(level), SwapSettings.fromConfig());
		if (slot != ToolPicker.NONE) {
			moveToMainHand(player, slot);
		}
	}

	public static void onAttackEntity(LocalPlayer player) {
		if (!ToolSwapperConfig.get().enabled || player.isSpectator() || !canClickInventory(player)) {
			return;
		}
		Inventory inventory = player.getInventory();
		int slot = ToolPicker.pickWeapon(contents(inventory), inventory.getSelectedSlot(), SwapSettings.fromConfig());
		if (slot != ToolPicker.NONE) {
			moveToMainHand(player, slot);
		}
	}

	public static void resetLastMinedBlock() {
		PICKER.reset();
	}

	/** Hotbar (0-8) and main inventory (9-35), without armor and offhand. */
	private static List<ItemStack> contents(Inventory inventory) {
		List<ItemStack> items = new ArrayList<>(Inventory.INVENTORY_SIZE);
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			items.add(inventory.getItem(slot));
		}
		return items;
	}

	private static boolean canClickInventory(LocalPlayer player) {
		return player.containerMenu == player.inventoryMenu && player.containerMenu.getCarried().isEmpty();
	}

	private static void moveToMainHand(LocalPlayer player, int inventorySlot) {
		MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
		if (gameMode == null) {
			return;
		}

		Inventory inventory = player.getInventory();
		if (Inventory.isHotbarSlot(inventorySlot) && ToolSwapperConfig.get().selectHotbarSlot) {
			inventory.setSelectedSlot(inventorySlot);
			// startDestroyBlock does not sync the selected slot by itself, so the server would still see the old item
			gameMode.ensureHasSentCarriedItem();
			return;
		}

		int menuSlot = Inventory.isHotbarSlot(inventorySlot) ? InventoryMenu.USE_ROW_SLOT_START + inventorySlot : inventorySlot;
		gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot, inventory.getSelectedSlot(), ContainerInput.SWAP, player);
	}

	private static @Nullable Holder<Enchantment> getEfficiency(Level level) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.EFFICIENCY).orElse(null);
	}
}
