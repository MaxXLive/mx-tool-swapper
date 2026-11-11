package de.ermackov.toolswapper.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks that should be mined with silk touch, from the config: block ids ("minecraft:ender_chest"), block tags
 * prefixed with '#' ("#minecraft:ice") or ids with '*' as a wildcard ("minecraft:*glass_pane").
 */
public final class SilkTouchBlocks {
	/** Blocks that drop nothing or something else without silk touch, and that are usually wanted as blocks. */
	public static final List<String> DEFAULTS = List.of(
			"minecraft:*glass",
			"minecraft:*glass_pane",
			"minecraft:ender_chest",
			"minecraft:ice",
			"minecraft:packed_ice",
			"minecraft:blue_ice",
			"minecraft:bookshelf",
			"minecraft:turtle_egg",
			"minecraft:bee_nest",
			"minecraft:beehive",
			"minecraft:*sculk*",
			"minecraft:*coral_block",
			"minecraft:*mushroom_block",
			"minecraft:mushroom_stem");

	private static Set<Identifier> blockIds = Set.of();
	private static List<TagKey<Block>> blockTags = List.of();
	private static List<Pattern> blockPatterns = List.of();

	private SilkTouchBlocks() {
	}

	static void rebuild() {
		Set<Identifier> ids = new HashSet<>();
		List<TagKey<Block>> tags = new ArrayList<>();
		List<Pattern> patterns = new ArrayList<>();
		for (String entry : ToolSwapperConfig.get().silkTouchBlocks) {
			String trimmed = entry.trim();
			if (trimmed.startsWith("#")) {
				Identifier id = Identifier.tryParse(trimmed.substring(1));
				if (id != null) {
					tags.add(TagKey.create(Registries.BLOCK, id));
				}
			} else if (trimmed.contains("*")) {
				String withNamespace = trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
				patterns.add(Pattern.compile(Pattern.quote(withNamespace).replace("*", "\\E.*\\Q")));
			} else if (!trimmed.isEmpty()) {
				Identifier id = Identifier.tryParse(trimmed);
				if (id != null) {
					ids.add(id);
				}
			}
		}
		blockIds = ids;
		blockTags = tags;
		blockPatterns = patterns;
	}

	public static boolean matches(BlockState state) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		if (blockIds.contains(id)) {
			return true;
		}
		for (TagKey<Block> tag : blockTags) {
			if (state.is(tag)) {
				return true;
			}
		}
		String idText = id.toString();
		for (Pattern pattern : blockPatterns) {
			if (pattern.matcher(idText).matches()) {
				return true;
			}
		}
		return false;
	}
}
