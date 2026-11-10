package de.ermackov.toolswapper;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Decides which inventory slot holds the item that should go into the main hand, for mining a block or for
 * attacking. It only looks at item stacks and settings, so it can be tested without a player.
 * Slots 0-8 are the hotbar and 9-35 the main inventory, like {@link Inventory}.
 */
public final class ToolPicker {
	public static final int NONE = -1;

	/**
	 * Mining speed a tool needs before it counts as made for a block. Swords mine plants and leaves at exactly 1.5,
	 * which is not worth swapping for.
	 */
	private static final float MIN_TOOL_SPEED = 1.5f;
	/** A block breaks in the first tick when speed / hardness reaches this value (see BlockBehaviour#getDestroyProgress). */
	private static final float INSTANT_BREAK_RATIO = 30f;

	/**
	 * The block type the held item was last found good enough for. Mining more of it does not trigger another search,
	 * so a working tool is not swapped back and forth while digging through the same material.
	 */
	private @Nullable Block settledBlock;

	public void reset() {
		settledBlock = null;
	}

	/** The slot with a faster tool for the block than the held item, or {@link #NONE}. */
	public int pickTool(List<ItemStack> inventory, int selectedSlot, BlockState state, float hardness,
			@Nullable Holder<Enchantment> efficiency, SwapSettings settings) {
		if (settings.toolSwapMode() == ToolSwapMode.NO_SWAP || state.isAir()) {
			return NONE;
		}
		ItemStack held = inventory.get(selectedSlot);
		if (!mayReplaceForMining(held, settings)) {
			return NONE;
		}

		MiningJob job = new MiningJob(state, hardness, efficiency);
		float heldSpeed = 0;
		if (job.suits(held)) {
			if (hardness == 0 || state.getBlock() == settledBlock) {
				return NONE;
			}
			heldSpeed = job.speedOf(held);
		}
		settledBlock = state.getBlock();

		float speedToBeat = heldSpeed;
		return bestSlot(inventory, selectedSlot, settings, job::suits, job::speedOf)
			.filter(slot -> job.speedOf(inventory.get(slot)) > speedToBeat)
			.orElse(NONE);
	}

	/** The slot with the best weapon to attack with, or {@link #NONE} if the held item should stay. */
	public static int pickWeapon(List<ItemStack> inventory, int selectedSlot, SwapSettings settings) {
		ItemStack held = inventory.get(selectedSlot);
		if (!settings.swapWeapon() || isSword(held) || !mayLeaveHand(held, settings)) {
			return NONE;
		}

		Optional<Integer> sword = bestSlot(inventory, selectedSlot, settings, ToolPicker::isSword, ToolPicker::attackDamage);
		if (sword.isPresent()) {
			return sword.get();
		}
		// without a sword an axe is the next best weapon, but only if it hits harder than an axe already in hand
		double heldAxeDamage = held.is(ItemTags.AXES) ? attackDamage(held) : 0;
		return bestSlot(inventory, selectedSlot, settings, stack -> stack.is(ItemTags.AXES), ToolPicker::attackDamage)
			.filter(slot -> attackDamage(inventory.get(slot)) > heldAxeDamage)
			.orElse(NONE);
	}

	/**
	 * Among the slots the settings allow, the one whose item fits and scores highest. On equal scores the lower slot
	 * wins, so the result does not depend on how the items are sorted.
	 */
	private static Optional<Integer> bestSlot(List<ItemStack> inventory, int selectedSlot, SwapSettings settings,
			Predicate<ItemStack> fits, ToDoubleFunction<ItemStack> score) {
		int firstSlot = settings.searchHotbar() ? 0 : Inventory.SELECTION_SIZE;
		return IntStream.range(firstSlot, Inventory.INVENTORY_SIZE)
			.filter(slot -> slot != selectedSlot)
			.boxed()
			.filter(slot -> {
				ItemStack stack = inventory.get(slot);
				return !stack.isEmpty() && settings.filter().test(stack) && hasDurabilityLeft(stack, settings) && fits.test(stack);
			})
			.max(Comparator.<Integer>comparingDouble(slot -> score.applyAsDouble(inventory.get(slot)))
				.thenComparing(Comparator.reverseOrder()));
	}

	/** Whether the held item may be put away to mine; swords only if weapon swapping brings them back. */
	private static boolean mayReplaceForMining(ItemStack held, SwapSettings settings) {
		if (isSword(held)) {
			return settings.toolSwapMode() == ToolSwapMode.ANY && settings.swapWeapon() && settings.filter().test(held);
		}
		return mayLeaveHand(held, settings);
	}

	/** Non-tools and an empty hand are only replaced when the settings ask for it; the filter can protect any item. */
	private static boolean mayLeaveHand(ItemStack held, SwapSettings settings) {
		boolean toolOrSword = held.has(DataComponents.TOOL) || isSword(held);
		if (!toolOrSword && !settings.swapFromAnyItem()) {
			return false;
		}
		return held.isEmpty() || settings.filter().test(held);
	}

	private static boolean hasDurabilityLeft(ItemStack stack, SwapSettings settings) {
		int required = settings.minRemainingDurability();
		if (required <= 0 || !stack.isDamageableItem()) {
			return true;
		}
		return stack.getMaxDamage() - stack.getDamageValue() > required;
	}

	private static boolean isSword(ItemStack stack) {
		return stack.is(ItemTags.SWORDS);
	}

	/** Main hand attack damage including the player's base damage, as shown in the item tooltip. */
	private static double attackDamage(ItemStack stack) {
		double base = Attributes.ATTACK_DAMAGE.value().getDefaultValue();
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
			.compute(Attributes.ATTACK_DAMAGE, base, EquipmentSlot.MAINHAND);
	}

	/** How well items do on one particular block. */
	private record MiningJob(BlockState state, float hardness, @Nullable Holder<Enchantment> efficiency) {
		/** Mining speed with efficiency, following the formula of the vanilla mining speed attribute. */
		float speedOf(ItemStack stack) {
			float base = stack.getDestroySpeed(state);
			if (base <= 1 || efficiency == null) {
				return base;
			}
			int level = EnchantmentHelper.getItemEnchantmentLevel(efficiency, stack);
			return level > 0 ? base + level * level + 1 : base;
		}

		/** The item can harvest the block and is either a real tool for it or breaks it at once. */
		boolean suits(ItemStack stack) {
			if (state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state)) {
				return false;
			}
			float speed = speedOf(stack);
			return speed > MIN_TOOL_SPEED || breaksInstantly(speed);
		}

		private boolean breaksInstantly(float speed) {
			return hardness >= 0 && speed >= hardness * INSTANT_BREAK_RATIO;
		}
	}
}
