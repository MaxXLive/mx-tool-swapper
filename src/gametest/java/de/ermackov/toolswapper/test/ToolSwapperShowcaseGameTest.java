package de.ermackov.toolswapper.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * Records frames for the animated pictures on the mod page; not a test. Only runs when the environment variable
 * MX_SHOWCASE is set, e.g. {@code MX_SHOWCASE=1 ./gradlew runClientGameTest}. Frames end up in
 * {@code build/run/clientGameTest/showcase/<scene>/}.
 */
public class ToolSwapperShowcaseGameTest implements FabricClientGameTest {
	private static final String[] BLOCKS = {"stone", "dirt", "oak_log", "sand", "cobblestone", "gravel"};

	private int frame;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getenv("MX_SHOWCASE") == null) {
			return;
		}
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			server.runCommand("difficulty peaceful");
			server.runCommand("effect give @a resistance infinite 255 true");
			server.runCommand("effect give @a saturation infinite 255 true");
			server.runCommand("clear @a");
			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.options.bobView().set(false);
			});

			recordMining(context, server);
			recordElytra(context, server);
		}
	}

	private void recordMining(ClientGameTestContext context, TestServerContext server) {
		BlockPos feet = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition());
		int x = feet.getX();
		int y = feet.getY();
		int z = feet.getZ();
		// a clear stage: grass floor, air above, and a row of blocks standing on the floor in front of the player,
		// so sand and gravel don't fall down
		server.runCommand(String.format("fill %d %d %d %d %d %d air", x - 4, y, z - 6, x + 6, y + 5, z + 6));
		server.runCommand(String.format("fill %d %d %d %d %d %d grass_block", x - 4, y - 1, z - 6, x + 6, y - 1, z + 6));
		for (int i = 0; i < BLOCKS.length; i++) {
			server.runCommand(String.format("setblock %d %d %d %s", x + 2, y, z - 2 + i, BLOCKS[i]));
		}
		server.runCommand(String.format("tp @a %d %d %d -90 0", x, y, z));
		server.runCommand("item replace entity @a hotbar.0 with diamond_pickaxe");
		server.runCommand("item replace entity @a inventory.0 with diamond_shovel");
		server.runCommand("item replace entity @a inventory.1 with diamond_axe");
		context.waitTicks(20);

		Path dir = showcaseDir("mining");
		for (int i = 0; i < BLOCKS.length; i++) {
			BlockPos target = new BlockPos(x + 2, y, z - 2 + i);
			context.getInput().lookAt(target);
			capture(context, dir, 4);
			context.runOnClient(mc -> mc.gameMode.startDestroyBlock(target, Direction.WEST));
			for (int tick = 0; tick < 100 && !context.computeOnClient(mc -> mc.level.getBlockState(target).isAir()); tick++) {
				context.runOnClient(mc -> {
					mc.gameMode.continueDestroyBlock(target, Direction.WEST);
					mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				});
				context.waitTick();
				if (tick % 2 == 0) {
					captureFrame(context, dir);
				}
			}
			context.runOnClient(mc -> mc.gameMode.stopDestroyBlock());
			capture(context, dir, 3);
		}
	}

	private void recordElytra(ClientGameTestContext context, TestServerContext server) {
		BlockPos feet = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition());
		int x = feet.getX();
		int y = feet.getY() + 18;
		int z = feet.getZ();
		server.runCommand(String.format("fill %d %d %d %d %d %d stone_bricks", x, feet.getY(), z, x + 1, y - 1, z + 1));
		server.runCommand(String.format("fill %d %d %d %d %d %d air", x - 1, y, z - 1, x + 60, y + 6, z + 2));
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a armor.chest with iron_chestplate");
		server.runCommand("item replace entity @a inventory.4 with elytra");
		server.runCommand(String.format("tp @a %d %d %d -90 10", x, y, z));
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(30);

		Path dir = showcaseDir("elytra");
		TestInput input = context.getInput();
		capture(context, dir, 8);
		// run off the tower, then jump in mid-air
		input.holdKey(options -> options.keyUp);
		for (int tick = 0; tick < 40 && context.computeOnClient(mc -> mc.player.onGround()); tick += 2) {
			context.waitTicks(2);
			captureFrame(context, dir);
		}
		capture(context, dir, 3);
		input.releaseKey(options -> options.keyUp);
		input.holdKey(options -> options.keyJump);
		context.waitTicks(2);
		input.releaseKey(options -> options.keyJump);
		for (int tick = 0; tick < 600 && !context.computeOnClient(mc -> mc.player.onGround()); tick += 2) {
			context.waitTicks(2);
			captureFrame(context, dir);
		}
		capture(context, dir, 15);
	}

	private Path showcaseDir(String scene) {
		frame = 0;
		return FabricLoader.getInstance().getGameDir().resolve("showcase").resolve(scene);
	}

	private void capture(ClientGameTestContext context, Path dir, int frames) {
		for (int i = 0; i < frames; i++) {
			context.waitTicks(2);
			captureFrame(context, dir);
		}
	}

	private void captureFrame(ClientGameTestContext context, Path dir) {
		context.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
		});
		context.takeScreenshot(TestScreenshotOptions.of(String.format("frame_%04d", frame++)).disableCounterPrefix().withDestinationDir(dir));
	}
}
