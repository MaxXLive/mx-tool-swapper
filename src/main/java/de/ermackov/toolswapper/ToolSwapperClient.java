package de.ermackov.toolswapper;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import de.ermackov.toolswapper.config.ToolSwapperConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ToolSwapperClient implements ClientModInitializer {
	public static final String MOD_ID = "mxtoolswapper";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
	private static KeyMapping toggleKey;

	@Override
	public void onInitializeClient() {
		ToolSwapperConfig.load();

		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mxtoolswapper.toggle", InputConstants.UNKNOWN.getValue(), KEY_CATEGORY));
		ClientTickEvents.START_CLIENT_TICK.register(ElytraSwapper::onStartTick);
		ClientTickEvents.END_CLIENT_TICK.register(ToolSwapperClient::onClientTick);

		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (level.isClientSide() && player instanceof LocalPlayer localPlayer) {
				ToolSwapper.onBlockClick(localPlayer, level, pos);
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() && player instanceof LocalPlayer localPlayer) {
				ToolSwapper.onAttackEntity(localPlayer);
			}
			return InteractionResult.PASS;
		});
	}

	private static void onClientTick(Minecraft minecraft) {
		while (toggleKey.consumeClick()) {
			ToolSwapperConfig config = ToolSwapperConfig.get();
			config.enabled = !config.enabled;
			ToolSwapperConfig.save();
			ToolSwapper.resetLastMinedBlock();
			if (config.showToggleMessage && minecraft.player != null) {
				minecraft.player.sendOverlayMessage(Component.translatable(config.enabled ? "message.mxtoolswapper.enabled" : "message.mxtoolswapper.disabled"));
			}
		}
	}
}
