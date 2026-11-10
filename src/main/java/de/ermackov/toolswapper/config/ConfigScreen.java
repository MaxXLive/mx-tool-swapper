package de.ermackov.toolswapper.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import de.ermackov.toolswapper.ToolSwapMode;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Locale;

public final class ConfigScreen {
	private static final String PREFIX = "config.mxtoolswapper.";

	private ConfigScreen() {
	}

	public static Screen create(Screen parent) {
		ToolSwapperConfig config = ToolSwapperConfig.get();
		ToolSwapperConfig defaults = new ToolSwapperConfig();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable(PREFIX + "title"))
				.setSavingRunnable(ToolSwapperConfig::save);
		ConfigEntryBuilder entries = builder.entryBuilder();

		ConfigCategory general = builder.getOrCreateCategory(Component.translatable(PREFIX + "category.general"));
		general.addEntry(entries.startBooleanToggle(text("enabled"), config.enabled)
				.setDefaultValue(defaults.enabled)
				.setTooltip(tooltip("enabled"))
				.setSaveConsumer(value -> config.enabled = value)
				.build());
		general.addEntry(entries.startEnumSelector(text("toolSwapMode"), ToolSwapMode.class, config.toolSwapMode)
				.setDefaultValue(defaults.toolSwapMode)
				.setEnumNameProvider(mode -> Component.translatable(PREFIX + "toolSwapMode." + mode.name().toLowerCase(Locale.ROOT)))
				.setTooltip(tooltip("toolSwapMode"))
				.setSaveConsumer(value -> config.toolSwapMode = value)
				.build());
		general.addEntry(entries.startBooleanToggle(text("swapWeapon"), config.swapWeapon)
				.setDefaultValue(defaults.swapWeapon)
				.setTooltip(tooltip("swapWeapon"))
				.setSaveConsumer(value -> config.swapWeapon = value)
				.build());
		general.addEntry(entries.startBooleanToggle(text("swapFromAnyItem"), config.swapFromAnyItem)
				.setDefaultValue(defaults.swapFromAnyItem)
				.setTooltip(tooltip("swapFromAnyItem"))
				.setSaveConsumer(value -> config.swapFromAnyItem = value)
				.build());
		general.addEntry(entries.startBooleanToggle(text("showToggleMessage"), config.showToggleMessage)
				.setDefaultValue(defaults.showToggleMessage)
				.setTooltip(tooltip("showToggleMessage"))
				.setSaveConsumer(value -> config.showToggleMessage = value)
				.build());

		ConfigCategory inventory = builder.getOrCreateCategory(Component.translatable(PREFIX + "category.inventory"));
		inventory.addEntry(entries.startBooleanToggle(text("searchHotbar"), config.searchHotbar)
				.setDefaultValue(defaults.searchHotbar)
				.setTooltip(tooltip("searchHotbar"))
				.setSaveConsumer(value -> config.searchHotbar = value)
				.build());
		inventory.addEntry(entries.startBooleanToggle(text("selectHotbarSlot"), config.selectHotbarSlot)
				.setDefaultValue(defaults.selectHotbarSlot)
				.setTooltip(tooltip("selectHotbarSlot"))
				.setSaveConsumer(value -> config.selectHotbarSlot = value)
				.build());
		inventory.addEntry(entries.startIntField(text("minRemainingDurability"), config.minRemainingDurability)
				.setDefaultValue(defaults.minRemainingDurability)
				.setMin(0)
				.setTooltip(tooltip("minRemainingDurability"))
				.setSaveConsumer(value -> config.minRemainingDurability = value)
				.build());

		ConfigCategory elytra = builder.getOrCreateCategory(Component.translatable(PREFIX + "category.elytra"));
		elytra.addEntry(entries.startBooleanToggle(text("elytraSwap"), config.elytraSwap)
				.setDefaultValue(defaults.elytraSwap)
				.setTooltip(tooltip("elytraSwap"))
				.setSaveConsumer(value -> config.elytraSwap = value)
				.build());
		elytra.addEntry(entries.startBooleanToggle(text("elytraSwapBack"), config.elytraSwapBack)
				.setDefaultValue(defaults.elytraSwapBack)
				.setTooltip(tooltip("elytraSwapBack"))
				.setSaveConsumer(value -> config.elytraSwapBack = value)
				.build());

		ConfigCategory filter = builder.getOrCreateCategory(Component.translatable(PREFIX + "category.filter"));
		filter.addEntry(entries.startEnumSelector(text("filterType"), FilterType.class, config.filterType)
				.setDefaultValue(defaults.filterType)
				.setEnumNameProvider(type -> Component.translatable(PREFIX + "filterType." + type.name().toLowerCase(Locale.ROOT)))
				.setTooltip(tooltip("filterType"))
				.setSaveConsumer(value -> config.filterType = value)
				.build());
		filter.addEntry(entries.startStrList(text("filterItems"), new ArrayList<>(config.filterItems))
				.setDefaultValue(defaults.filterItems)
				.setExpanded(true)
				.setTooltip(tooltip("filterItems"))
				.setSaveConsumer(value -> config.filterItems = new ArrayList<>(value))
				.build());

		return builder.build();
	}

	private static Component text(String key) {
		return Component.translatable(PREFIX + key);
	}

	private static Component tooltip(String key) {
		return Component.translatable(PREFIX + key + ".tooltip");
	}
}
