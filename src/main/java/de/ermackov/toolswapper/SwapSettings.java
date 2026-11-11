package de.ermackov.toolswapper;

import de.ermackov.toolswapper.config.ItemFilter;
import de.ermackov.toolswapper.config.SilkTouchBlocks;
import de.ermackov.toolswapper.config.ToolSwapperConfig;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The settings that decide which item gets picked, independent of the config file so the choice can be unit tested.
 *
 * @param keepSuitableTool keep a held tool that mines the block properly instead of swapping to a faster one
 * @param returnToPreviousTool when the held tool does not fit, go back to the tool the player used before
 * @param filter true for items that may be swapped, from the allowlist or blocklist
 * @param prefersSilkTouch true for blocks that should be mined with a silk touch tool when there is one
 */
public record SwapSettings(
		ToolSwapMode toolSwapMode,
		boolean swapWeapon,
		boolean swapFromAnyItem,
		boolean searchHotbar,
		int minRemainingDurability,
		boolean keepSuitableTool,
		boolean returnToPreviousTool,
		Predicate<ItemStack> filter,
		Predicate<BlockState> prefersSilkTouch) {

	public static SwapSettings fromConfig() {
		ToolSwapperConfig config = ToolSwapperConfig.get();
		return new SwapSettings(config.toolSwapMode, config.swapWeapon, config.swapFromAnyItem, config.searchHotbar,
				config.minRemainingDurability, config.keepSuitableTool, config.returnToPreviousTool, ItemFilter::matchesFilter,
				config.preferSilkTouch ? SilkTouchBlocks::matches : state -> false);
	}
}
