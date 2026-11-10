package de.ermackov.toolswapper;

import de.ermackov.toolswapper.config.ItemFilter;
import de.ermackov.toolswapper.config.ToolSwapperConfig;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

/**
 * The settings that decide which item gets picked, independent of the config file so the choice can be unit tested.
 *
 * @param filter true for items that may be swapped, from the allowlist or blocklist
 */
public record SwapSettings(
		ToolSwapMode toolSwapMode,
		boolean swapWeapon,
		boolean swapFromAnyItem,
		boolean searchHotbar,
		int minRemainingDurability,
		Predicate<ItemStack> filter) {

	public static SwapSettings fromConfig() {
		ToolSwapperConfig config = ToolSwapperConfig.get();
		return new SwapSettings(config.toolSwapMode, config.swapWeapon, config.swapFromAnyItem, config.searchHotbar,
				config.minRemainingDurability, ItemFilter::matchesFilter);
	}
}
