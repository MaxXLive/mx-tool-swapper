package de.ermackov.toolswapper.test;

import de.ermackov.toolswapper.SwapSettings;
import de.ermackov.toolswapper.ToolPicker;
import de.ermackov.toolswapper.ToolSwapMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Unit tests for the item choice of {@link ToolPicker}, independent of how it is implemented.
 * They run as server game tests only because item components, tags and enchantments are data driven and need a
 * loaded server; no world is touched. Slots 0-8 are the hotbar and 9-35 the main inventory, like {@link Inventory}.
 */
public class ToolPickerGameTest {
	private static final int NONE = ToolPicker.NONE;
	private static final int HAND = 0;

	private static SwapSettings defaults() {
		return new SwapSettings(ToolSwapMode.ANY, true, false, true, 0, stack -> true);
	}

	private static SwapSettings with(ToolSwapMode mode, boolean swapWeapon, boolean swapFromAnyItem, boolean searchHotbar,
			int minRemainingDurability, Predicate<ItemStack> filter) {
		return new SwapSettings(mode, swapWeapon, swapFromAnyItem, searchHotbar, minRemainingDurability, filter);
	}

	private static Predicate<ItemStack> allExcept(Item... items) {
		Set<Item> blocked = Set.of(items);
		return stack -> !blocked.contains(stack.getItem());
	}

	/** A fresh inventory and picker for one test. */
	private static final class Fixture {
		final List<ItemStack> inventory = new ArrayList<>();
		final ToolPicker picker = new ToolPicker();
		final Holder<Enchantment> efficiency;
		private final GameTestHelper helper;

		Fixture(GameTestHelper helper) {
			this.helper = helper;
			for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
				inventory.add(ItemStack.EMPTY);
			}
			efficiency = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
		}

		ItemStack put(int slot, Item item) {
			ItemStack stack = new ItemStack(item);
			inventory.set(slot, stack);
			return stack;
		}

		ItemStack withEfficiency(ItemStack stack, int level) {
			stack.enchant(efficiency, level);
			return stack;
		}

		int mine(Block block) {
			return mine(block, defaults());
		}

		int mine(Block block, SwapSettings settings) {
			return mine(block, settings, efficiency);
		}

		int mine(Block block, SwapSettings settings, @Nullable Holder<Enchantment> efficiencyHolder) {
			BlockState state = block.defaultBlockState();
			float hardness = state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
			return picker.pickTool(inventory, HAND, state, hardness, efficiencyHolder, settings);
		}

		int attack() {
			return attack(defaults());
		}

		int attack(SwapSettings settings) {
			return ToolPicker.pickWeapon(inventory, HAND, settings);
		}

		void assertEquals(int expected, int actual) {
			assertEquals(expected, actual, "slot");
		}

		void assertEquals(int expected, int actual, String what) {
			if (!Objects.equals(expected, actual)) {
				helper.fail(what + ": expected " + expected + ", got " + actual);
			}
		}

		void assertTrue(boolean condition, String message) {
			if (!condition) {
				helper.fail(message);
			}
		}
	}

	@GameTest
	public void mining_picksTheMatchingToolForTheBlock(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		f.put(9, Items.IRON_PICKAXE);
		f.assertEquals(9, f.mine(Blocks.STONE));
		helper.succeed();
	}

	@GameTest
	public void mining_picksTheFastestOfSeveralMatchingTools(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		f.put(9, Items.STONE_PICKAXE);
		f.put(10, Items.DIAMOND_PICKAXE);
		f.put(11, Items.IRON_PICKAXE);
		f.assertEquals(10, f.mine(Blocks.STONE));
		helper.succeed();
	}

	@GameTest
	public void mining_efficiencyMakesALowerTierToolFaster(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		f.put(9, Items.DIAMOND_PICKAXE);
		f.withEfficiency(f.put(10, Items.IRON_PICKAXE), 5);
		f.assertEquals(10, f.mine(Blocks.STONE));
		helper.succeed();
	}

	@GameTest
	public void mining_withoutEfficiencyInTheRegistryOnlyTheBaseSpeedCounts(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		f.put(9, Items.DIAMOND_PICKAXE);
		f.withEfficiency(f.put(10, Items.IRON_PICKAXE), 5);
		f.assertEquals(9, f.mine(Blocks.STONE, defaults(), null));
		helper.succeed();
	}

	@GameTest
	public void mining_picksAToolThatBreaksTheBlockInstantly(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_PICKAXE);
		f.put(9, Items.WOODEN_SHOVEL);
		f.withEfficiency(f.put(10, Items.DIAMOND_SHOVEL), 3);
		f.withEfficiency(f.put(11, Items.NETHERITE_SHOVEL), 5);
		int slot = f.mine(Blocks.DIRT);
		f.assertTrue(slot == 10 || slot == 11, "expected one of the instant-breaking shovels, got " + slot);
		helper.succeed();
	}

	@GameTest
	public void mining_onlyPicksToolsThatCanHarvestBlocksNeedingTheCorrectTool(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		f.put(9, Items.IRON_PICKAXE);
		f.put(10, Items.DIAMOND_PICKAXE);
		f.assertEquals(10, f.mine(Blocks.OBSIDIAN));
		helper.succeed();
	}

	@GameTest
	public void mining_doesNotSwapWhenNoToolCanHarvestTheBlock(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		f.put(9, Items.IRON_PICKAXE);
		f.assertEquals(NONE, f.mine(Blocks.OBSIDIAN));
		helper.succeed();
	}

	@GameTest
	public void mining_ignoresTheSmallSwordBonusOnLeaves(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_PICKAXE);
		f.put(9, Items.DIAMOND_SWORD);
		f.assertEquals(NONE, f.mine(Blocks.OAK_LEAVES));
		helper.succeed();
	}

	@GameTest
	public void mining_picksShearsForLeaves(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_PICKAXE);
		f.put(9, Items.DIAMOND_SWORD);
		f.put(10, Items.SHEARS);
		f.assertEquals(10, f.mine(Blocks.OAK_LEAVES));
		helper.succeed();
	}

	@GameTest
	public void mining_swapsToABetterToolWhenTheHeldToolAlreadyWorks(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.STONE_PICKAXE);
		f.put(9, Items.DIAMOND_PICKAXE);
		f.assertEquals(9, f.mine(Blocks.STONE));
		helper.succeed();
	}

	@GameTest
	public void mining_keepsTheHeldToolWhenNothingIsFaster(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.DIAMOND_PICKAXE);
		f.put(9, Items.IRON_PICKAXE);
		f.put(10, Items.DIAMOND_PICKAXE);
		f.assertEquals(NONE, f.mine(Blocks.STONE));
		helper.succeed();
	}

	@GameTest
	public void mining_doesNotSwapForBlocksThatBreakInstantlyAnyway(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		f.put(9, Items.DIAMOND_PICKAXE);
		f.assertEquals(NONE, f.mine(Blocks.TORCH));
		helper.succeed();
	}

	@GameTest
	public void mining_doesNotCheckAgainWhileMiningTheSameBlockTypeWithAWorkingTool(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.STONE_PICKAXE);
		f.assertEquals(NONE, f.mine(Blocks.STONE));
		f.put(9, Items.DIAMOND_PICKAXE);
		f.assertEquals(NONE, f.mine(Blocks.STONE), "same block type again");
		f.assertEquals(9, f.mine(Blocks.COBBLESTONE), "a different block type is checked again");
		helper.succeed();
	}

	@GameTest
	public void mining_checksAgainAfterReset(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.STONE_PICKAXE);
		f.assertEquals(NONE, f.mine(Blocks.STONE));
		f.put(9, Items.DIAMOND_PICKAXE);
		f.picker.reset();
		f.assertEquals(9, f.mine(Blocks.STONE));
		helper.succeed();
	}

	@GameTest
	public void mining_checksTheSameBlockTypeAgainWhenTheHeldItemDoesNotWork(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		f.assertEquals(NONE, f.mine(Blocks.STONE));
		f.put(9, Items.IRON_PICKAXE);
		f.assertEquals(9, f.mine(Blocks.STONE));
		helper.succeed();
	}

	@GameTest
	public void mining_neverPicksTheSelectedSlotItself(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		f.assertEquals(NONE, f.mine(Blocks.STONE));
		helper.succeed();
	}

	@GameTest
	public void mining_searchesTheHotbar(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		f.put(4, Items.IRON_PICKAXE);
		f.assertEquals(4, f.mine(Blocks.STONE));
		helper.succeed();
	}

	@GameTest
	public void mining_skipsTheHotbarWhenHotbarSearchIsOff(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		f.put(4, Items.DIAMOND_PICKAXE);
		SwapSettings noHotbar = with(ToolSwapMode.ANY, true, false, false, 0, stack -> true);
		f.assertEquals(NONE, f.mine(Blocks.STONE, noHotbar));
		f.put(20, Items.STONE_PICKAXE);
		f.assertEquals(20, f.mine(Blocks.COBBLESTONE, noHotbar));
		helper.succeed();
	}

	@GameTest
	public void mining_searchesTheWholeMainInventory(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		f.put(35, Items.IRON_PICKAXE);
		f.assertEquals(35, f.mine(Blocks.STONE));
		helper.succeed();
	}

	@GameTest
	public void mining_skipsItemsRejectedByTheFilter(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		f.put(9, Items.DIAMOND_PICKAXE);
		f.put(10, Items.IRON_PICKAXE);
		SwapSettings filtered = with(ToolSwapMode.ANY, true, false, true, 0, allExcept(Items.DIAMOND_PICKAXE));
		f.assertEquals(10, f.mine(Blocks.STONE, filtered));
		helper.succeed();
	}

	@GameTest
	public void mining_doesNotSwapAwayFromAHeldItemRejectedByTheFilter(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		f.put(9, Items.IRON_PICKAXE);
		SwapSettings filtered = with(ToolSwapMode.ANY, true, false, true, 0, allExcept(Items.IRON_SHOVEL));
		f.assertEquals(NONE, f.mine(Blocks.STONE, filtered));
		helper.succeed();
	}

	@GameTest
	public void mining_skipsToolsAtOrBelowTheMinimumDurability(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		ItemStack worn = f.put(9, Items.DIAMOND_PICKAXE);
		worn.setDamageValue(worn.getMaxDamage() - 10);
		f.put(10, Items.IRON_PICKAXE);
		SwapSettings minTen = with(ToolSwapMode.ANY, true, false, true, 10, stack -> true);
		f.assertEquals(10, f.mine(Blocks.STONE, minTen), "exactly 10 left is not enough");

		worn.setDamageValue(worn.getMaxDamage() - 11);
		f.picker.reset();
		f.assertEquals(9, f.mine(Blocks.STONE, minTen), "11 left is enough");
		helper.succeed();
	}

	@GameTest
	public void mining_minimumDurabilityZeroAllowsAlmostBrokenTools(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		ItemStack worn = f.put(9, Items.DIAMOND_PICKAXE);
		worn.setDamageValue(worn.getMaxDamage() - 1);
		f.assertEquals(9, f.mine(Blocks.STONE));
		helper.succeed();
	}

	@GameTest
	public void mining_noSwapModeNeverSwaps(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		f.put(9, Items.IRON_PICKAXE);
		f.assertEquals(NONE, f.mine(Blocks.STONE, with(ToolSwapMode.NO_SWAP, true, false, true, 0, stack -> true)));
		helper.succeed();
	}

	@GameTest
	public void mining_doesNothingForAir(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		f.put(9, Items.IRON_PICKAXE);
		f.assertEquals(NONE, f.mine(Blocks.AIR));
		helper.succeed();
	}

	@GameTest
	public void mining_swapsAwayFromASwordInAnyModeWhenWeaponSwappingBringsItBack(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.DIAMOND_SWORD);
		f.put(9, Items.IRON_PICKAXE);
		f.assertEquals(9, f.mine(Blocks.STONE));
		helper.succeed();
	}

	@GameTest
	public void mining_keepsTheSwordInOnlyToolsMode(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.DIAMOND_SWORD);
		f.put(9, Items.IRON_PICKAXE);
		f.assertEquals(NONE, f.mine(Blocks.STONE, with(ToolSwapMode.ONLY_TOOLS, true, false, true, 0, stack -> true)));
		helper.succeed();
	}

	@GameTest
	public void mining_keepsTheSwordWhenWeaponSwappingIsOff(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.DIAMOND_SWORD);
		f.put(9, Items.IRON_PICKAXE);
		f.assertEquals(NONE, f.mine(Blocks.STONE, with(ToolSwapMode.ANY, false, false, true, 0, stack -> true)));
		helper.succeed();
	}

	@GameTest
	public void mining_onlyToolsModeStillSwapsBetweenTools(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_SHOVEL);
		f.put(9, Items.IRON_PICKAXE);
		f.assertEquals(9, f.mine(Blocks.STONE, with(ToolSwapMode.ONLY_TOOLS, true, false, true, 0, stack -> true)));
		helper.succeed();
	}

	@GameTest
	public void mining_doesNotSwapFromAnEmptyHandOrNonToolByDefault(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(9, Items.IRON_PICKAXE);
		f.assertEquals(NONE, f.mine(Blocks.STONE), "empty hand");
		f.put(HAND, Items.DIRT);
		f.assertEquals(NONE, f.mine(Blocks.COBBLESTONE), "block item in hand");
		helper.succeed();
	}

	@GameTest
	public void mining_swapsFromAnEmptyHandOrNonToolWhenEnabled(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		SwapSettings anyItem = with(ToolSwapMode.ANY, true, true, true, 0, stack -> true);
		f.put(9, Items.IRON_PICKAXE);
		f.assertEquals(9, f.mine(Blocks.STONE, anyItem), "empty hand");
		f.put(HAND, Items.DIRT);
		f.assertEquals(9, f.mine(Blocks.COBBLESTONE, anyItem), "block item in hand");
		helper.succeed();
	}

	@GameTest
	public void fighting_picksASwordWhenHoldingATool(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_PICKAXE);
		f.put(9, Items.IRON_SWORD);
		f.assertEquals(9, f.attack());
		helper.succeed();
	}

	@GameTest
	public void fighting_picksTheSwordWithTheMostDamage(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_PICKAXE);
		f.put(9, Items.IRON_SWORD);
		f.put(10, Items.DIAMOND_SWORD);
		f.put(11, Items.STONE_SWORD);
		f.assertEquals(10, f.attack());
		helper.succeed();
	}

	@GameTest
	public void fighting_prefersAnySwordOverAStrongerAxe(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_PICKAXE);
		f.put(9, Items.NETHERITE_AXE);
		f.put(10, Items.WOODEN_SWORD);
		f.assertEquals(10, f.attack());
		helper.succeed();
	}

	@GameTest
	public void fighting_picksAnAxeWhenThereIsNoSword(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_PICKAXE);
		f.put(9, Items.STONE_AXE);
		f.assertEquals(9, f.attack());
		helper.succeed();
	}

	@GameTest
	public void fighting_picksAStrongerAxeThanTheHeldAxe(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.WOODEN_AXE);
		f.put(9, Items.DIAMOND_AXE);
		f.assertEquals(9, f.attack());
		helper.succeed();
	}

	@GameTest
	public void fighting_keepsTheHeldAxeWhenItIsTheStrongest(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.DIAMOND_AXE);
		f.put(9, Items.STONE_AXE);
		f.assertEquals(NONE, f.attack());
		helper.succeed();
	}

	@GameTest
	public void fighting_keepsTheHeldAxeWhenAnotherAxeDealsTheSameDamage(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		// stone and diamond axes both deal 9 damage
		f.put(HAND, Items.STONE_AXE);
		f.put(9, Items.DIAMOND_AXE);
		f.assertEquals(NONE, f.attack());
		helper.succeed();
	}

	@GameTest
	public void fighting_keepsAHeldSword(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.STONE_SWORD);
		f.put(9, Items.NETHERITE_SWORD);
		f.assertEquals(NONE, f.attack());
		helper.succeed();
	}

	@GameTest
	public void fighting_ignoresOtherWeapons(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_PICKAXE);
		f.put(9, Items.TRIDENT);
		f.put(10, Items.MACE);
		f.assertEquals(NONE, f.attack());
		helper.succeed();
	}

	@GameTest
	public void fighting_doesNothingWhenWeaponSwappingIsOff(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_PICKAXE);
		f.put(9, Items.IRON_SWORD);
		f.assertEquals(NONE, f.attack(with(ToolSwapMode.ANY, false, false, true, 0, stack -> true)));
		helper.succeed();
	}

	@GameTest
	public void fighting_doesNotSwapFromAnEmptyHandOrNonToolByDefault(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(9, Items.IRON_SWORD);
		f.assertEquals(NONE, f.attack(), "empty hand");
		f.put(HAND, Items.DIRT);
		f.assertEquals(NONE, f.attack(), "block item in hand");
		helper.succeed();
	}

	@GameTest
	public void fighting_swapsFromAnEmptyHandOrNonToolWhenEnabled(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		SwapSettings anyItem = with(ToolSwapMode.ANY, true, true, true, 0, stack -> true);
		f.put(9, Items.IRON_SWORD);
		f.assertEquals(9, f.attack(anyItem), "empty hand");
		f.put(HAND, Items.DIRT);
		f.assertEquals(9, f.attack(anyItem), "block item in hand");
		helper.succeed();
	}

	@GameTest
	public void fighting_respectsTheFilter(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_PICKAXE);
		f.put(9, Items.DIAMOND_SWORD);
		f.put(10, Items.IRON_SWORD);
		f.assertEquals(10, f.attack(with(ToolSwapMode.ANY, true, false, true, 0, allExcept(Items.DIAMOND_SWORD))));
		f.assertEquals(NONE, f.attack(with(ToolSwapMode.ANY, true, false, true, 0, allExcept(Items.IRON_PICKAXE))), "held item filtered");
		helper.succeed();
	}

	@GameTest
	public void fighting_respectsTheMinimumDurability(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_PICKAXE);
		ItemStack worn = f.put(9, Items.DIAMOND_SWORD);
		worn.setDamageValue(worn.getMaxDamage() - 5);
		f.put(10, Items.IRON_SWORD);
		f.assertEquals(10, f.attack(with(ToolSwapMode.ANY, true, false, true, 5, stack -> true)));
		helper.succeed();
	}

	@GameTest
	public void fighting_respectsHotbarSearch(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(HAND, Items.IRON_PICKAXE);
		f.put(3, Items.DIAMOND_SWORD);
		f.put(12, Items.STONE_SWORD);
		f.assertEquals(3, f.attack());
		f.assertEquals(12, f.attack(with(ToolSwapMode.ANY, true, false, false, 0, stack -> true)));
		helper.succeed();
	}

	@GameTest
	public void fighting_worksWithAnotherSelectedSlot(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		f.put(5, Items.IRON_PICKAXE);
		f.put(HAND, Items.IRON_SWORD);
		f.assertEquals(HAND, ToolPicker.pickWeapon(f.inventory, 5, defaults()));
		helper.succeed();
	}
}
