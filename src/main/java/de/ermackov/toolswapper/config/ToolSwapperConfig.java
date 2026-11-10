package de.ermackov.toolswapper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import de.ermackov.toolswapper.ToolSwapMode;
import de.ermackov.toolswapper.ToolSwapperClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ToolSwapperConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(ToolSwapperClient.MOD_ID + ".json");
	/** File name used before the mod id got the "mx" prefix. */
	private static final Path LEGACY_PATH = FabricLoader.getInstance().getConfigDir().resolve("toolswapper.json");

	private static ToolSwapperConfig instance = new ToolSwapperConfig();

	public boolean enabled = true;
	public ToolSwapMode toolSwapMode = ToolSwapMode.ANY;
	public boolean swapWeapon = true;
	public boolean swapFromAnyItem = false;
	public boolean searchHotbar = true;
	public boolean selectHotbarSlot = true;
	public int minRemainingDurability = 0;
	public FilterType filterType = FilterType.BLOCKLIST;
	public List<String> filterItems = new ArrayList<>();
	public boolean showToggleMessage = true;
	public boolean elytraSwap = true;
	public boolean elytraSwapBack = true;

	public static ToolSwapperConfig get() {
		return instance;
	}

	public static void load() {
		migrateLegacyFile();
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				ToolSwapperConfig loaded = GSON.fromJson(reader, ToolSwapperConfig.class);
				if (loaded != null) {
					instance = loaded.sanitize();
				}
			} catch (IOException | RuntimeException e) {
				ToolSwapperClient.LOGGER.error("Failed to load config from {}, using defaults", PATH, e);
			}
		}
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			ToolSwapperClient.LOGGER.error("Failed to save config to {}", PATH, e);
		}
		ItemFilter.rebuild();
	}

	/** Renames the config file of versions before the "mx" prefix once. */
	private static void migrateLegacyFile() {
		if (Files.exists(PATH) || !Files.exists(LEGACY_PATH)) {
			return;
		}
		try {
			Files.move(LEGACY_PATH, PATH);
			ToolSwapperClient.LOGGER.info("Moved {} to {}", LEGACY_PATH.getFileName(), PATH.getFileName());
		} catch (IOException e) {
			ToolSwapperClient.LOGGER.error("Failed to move {} to {}", LEGACY_PATH, PATH, e);
		}
	}

	private ToolSwapperConfig sanitize() {
		if (toolSwapMode == null) {
			toolSwapMode = ToolSwapMode.ANY;
		}
		if (filterType == null) {
			filterType = FilterType.BLOCKLIST;
		}
		if (filterItems == null) {
			filterItems = new ArrayList<>();
		}
		minRemainingDurability = Math.max(0, minRemainingDurability);
		return this;
	}
}
