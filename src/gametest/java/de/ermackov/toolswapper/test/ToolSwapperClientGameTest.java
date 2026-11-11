package de.ermackov.toolswapper.test;

import de.ermackov.toolswapper.ToolSwapper;
import de.ermackov.toolswapper.config.ToolSwapperConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * Drives the mod through the real client paths (block attack, entity attack, jump key) and checks the result on the
 * server, so a swap only counts if the server agrees with the client about the item in hand.
 */
public class ToolSwapperClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("difficulty easy");
			server.runCommand("effect give @a resistance infinite 255 true");
			server.runCommand("clear @a");

			BlockPos playerPos = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition());
			BlockPos stone = playerPos.offset(2, 0, 0);
			BlockPos dirt = playerPos.offset(2, 0, 1);
			server.runCommand("setblock " + coords(stone) + " stone");
			server.runCommand("setblock " + coords(dirt) + " dirt");

			// Mining: the pickaxe from the main inventory is swapped into the selected hotbar slot.
			server.runCommand("item replace entity @a hotbar.0 with iron_shovel");
			server.runCommand("item replace entity @a inventory.0 with diamond_pickaxe");
			context.waitTicks(3);
			mine(context, stone);
			assertMainHand(context, server, Items.DIAMOND_PICKAXE, "after mining stone");
			assertInventorySlot(server, 9, Items.IRON_SHOVEL, "the shovel should have moved to where the pickaxe was");

			// Mining: a better tool in the hotbar is selected instead of moved. Forget the shovel the player held before,
			// otherwise the mod would go back to it.
			server.runCommand("item replace entity @a hotbar.3 with diamond_shovel");
			context.runOnClient(mc -> ToolSwapper.resetLastMinedBlock());
			context.waitTicks(3);
			mine(context, dirt);
			assertMainHand(context, server, Items.DIAMOND_SHOVEL, "after mining dirt");
			int selected = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getInventory().getSelectedSlot());
			if (selected != 3) {
				throw new AssertionError("Hotbar slot 3 with the diamond shovel should be selected on the server, got " + selected);
			}

			// Attacking: the sword from the main inventory replaces the tool.
			server.runCommand("item replace entity @a inventory.5 with iron_sword");
			server.runCommand("summon zombie " + coords(playerPos.offset(-2, 0, 0)) + " {NoAI:1b,PersistenceRequired:1b}");
			context.waitTicks(5);
			context.runOnClient(mc -> {
				Zombie zombie = mc.level.getEntitiesOfClass(Zombie.class, new AABB(playerPos).inflate(6)).stream().findFirst()
					.orElseThrow(() -> new AssertionError("Zombie not found on the client"));
				mc.gameMode.attack(mc.player, zombie);
			});
			context.waitTicks(3);
			assertMainHand(context, server, Items.IRON_SWORD, "after attacking a zombie");
			server.runCommand("kill @e[type=zombie]");

			// Disabled: nothing is swapped.
			context.runOnClient(mc -> ToolSwapperConfig.get().enabled = false);
			mine(context, stone);
			assertMainHand(context, server, Items.IRON_SWORD, "while the mod is disabled");
			context.runOnClient(mc -> {
				ToolSwapperConfig.get().enabled = true;
				ToolSwapper.resetLastMinedBlock();
			});

			// Elytra: jumping in mid-air puts on the elytra, landing puts the chestplate back.
			server.runCommand("item replace entity @a armor.chest with iron_chestplate");
			server.runCommand("item replace entity @a inventory.10 with elytra");
			server.runCommand("tp @a ~ ~30 ~");
			context.waitFor(mc -> !mc.player.onGround() && mc.player.getDeltaMovement().y < -0.3);
			// held for a few ticks like a real key press; a press within one frame is never seen at the start of a tick
			context.getInput().holdKeyFor(options -> options.keyJump, 3);
			assertChest(server, Items.ELYTRA, "after jumping in mid-air");
			if (!context.computeOnClient(mc -> mc.player.isFallFlying())) {
				throw new AssertionError("The player should glide right after the elytra was put on");
			}
			context.takeScreenshot("tool_swapper_elytra_gliding");
			context.waitFor(mc -> mc.player.onGround(), 600);
			context.waitTicks(5);
			assertChest(server, Items.IRON_CHESTPLATE, "after landing");
			assertInventorySlot(server, 19, Items.ELYTRA, "the elytra should be back in its old slot");
		}
	}

	private static void mine(ClientGameTestContext context, BlockPos pos) {
		context.runOnClient(mc -> {
			mc.gameMode.startDestroyBlock(pos, Direction.UP);
			mc.gameMode.stopDestroyBlock();
		});
		context.waitTicks(3);
	}

	private static void assertMainHand(ClientGameTestContext context, TestServerContext server, Item expected, String situation) {
		Item client = context.computeOnClient(mc -> mc.player.getMainHandItem().getItem());
		Item onServer = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getMainHandItem().getItem());
		if (client != expected || onServer != expected) {
			throw new AssertionError("Expected " + expected + " in the main hand " + situation + ", client has " + client + ", server has " + onServer);
		}
	}

	private static void assertInventorySlot(TestServerContext server, int slot, Item expected, String message) {
		Item actual = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getInventory().getItem(slot).getItem());
		if (actual != expected) {
			throw new AssertionError(message + ": slot " + slot + " has " + actual);
		}
	}

	private static void assertChest(TestServerContext server, Item expected, String situation) {
		Item actual = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getItemBySlot(EquipmentSlot.CHEST).getItem());
		if (actual != expected) {
			throw new AssertionError("Expected " + expected + " in the chest slot " + situation + ", server has " + actual);
		}
	}

	private static String coords(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}
}
