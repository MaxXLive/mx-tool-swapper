package de.ermackov.toolswapper.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Allowlist or blocklist from the config: entries are item ids ("minecraft:diamond_pickaxe")
 * or item tags prefixed with '#' ("#minecraft:axes").
 */
public final class ItemFilter {
	private static Set<Identifier> itemIds = Set.of();
	private static List<TagKey<Item>> itemTags = List.of();

	private ItemFilter() {
	}

	static void rebuild() {
		Set<Identifier> ids = new HashSet<>();
		List<TagKey<Item>> tags = new ArrayList<>();
		for (String entry : ToolSwapperConfig.get().filterItems) {
			String trimmed = entry.trim();
			if (trimmed.startsWith("#")) {
				Identifier id = Identifier.tryParse(trimmed.substring(1));
				if (id != null) {
					tags.add(TagKey.create(Registries.ITEM, id));
				}
			} else if (!trimmed.isEmpty()) {
				Identifier id = Identifier.tryParse(trimmed);
				if (id != null) {
					ids.add(id);
				}
			}
		}
		itemIds = ids;
		itemTags = tags;
	}

	public static boolean matchesFilter(ItemStack stack) {
		boolean listed = isListed(stack);
		return ToolSwapperConfig.get().filterType == FilterType.ALLOWLIST ? listed : !listed;
	}

	private static boolean isListed(ItemStack stack) {
		if (itemIds.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
			return true;
		}
		for (TagKey<Item> tag : itemTags) {
			if (stack.is(tag)) {
				return true;
			}
		}
		return false;
	}
}
