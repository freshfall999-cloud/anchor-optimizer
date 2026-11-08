package name.modid.test;

import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real client + singleplayer server: the fixed Diolez mod must not crash, and must still do what it did. */
public class DiolezFixGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("DiolezFixGameTest");
	private static final int FLAT_Y = -60; // superflat surface
	private static final int MOUSE_RIGHT = 1;

	@Override
	public void runTest(ClientGameTestContext context) {
		boolean withAnchorOptimizer = FabricLoader.getInstance().isModLoaded("anchoroptimizer");
		LOGGER.info("Running with anchoroptimizer installed: {}", withAnchorOptimizer);
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getServer().runCommand("gamemode creative @a");
			sp.getClientWorld().waitForChunksRender();

			placingBlocksDoesNotCrash(context, sp);
			heldRightClickOnAnchorRepeatsEveryTick(context, sp);
			if (withAnchorOptimizer) {
				anchorOptimizerPredictionStillWorks(context, sp);
			}
		}
		LOGGER.info("All DiolezzAnchorOptimizer fix tests passed");
	}

	/** The 1.21.1 build crashed as soon as the crosshair was on a block and on every block right-click. */
	private static void placingBlocksDoesNotCrash(ClientGameTestContext context, TestSingleplayerContext sp) {
		BlockPos floor = new BlockPos(0, FLAT_Y - 1, 0);
		sp.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:cobblestone 64");
		// Stand next to the spot, looking straight down at the floor.
		sp.getServer().runCommand("tp @a 0.5 " + FLAT_Y + " 1.5 0 70");
		context.waitFor(c -> c.player.getMainHandItem().is(Items.COBBLESTONE)
				&& c.hitResult instanceof BlockHitResult hit && hit.getDirection() == Direction.UP);
		context.waitTicks(20); // crosshair on a block for a full second: the old tick hook crashed here

		BlockPos target = context.computeOnClient(c -> ((BlockHitResult) c.hitResult).getBlockPos().above());
		for (int i = 0; i < 5; i++) {
			context.getInput().pressMouse(MOUSE_RIGHT); // real right-click: places a block
		}
		context.waitTicks(5);
		BlockState placed = sp.getServer().computeOnServer(s -> s.overworld().getBlockState(target));
		check(placed.is(Blocks.COBBLESTONE), "right-click should have placed cobblestone at " + target + ", got " + placed);
		LOGGER.info("PASS placingBlocksDoesNotCrash");
	}

	/** What the mod is for: holding right-click while looking at an anchor repeats every tick instead of every 4 ticks. */
	private static void heldRightClickOnAnchorRepeatsEveryTick(ClientGameTestContext context, TestSingleplayerContext sp) {
		int held = charges(context, sp, new BlockPos(20, FLAT_Y, 0), 3);
		check(held == 3, "holding right-click with glowstone for 3 ticks should charge 3 times, got " + held);
		int single = charges(context, sp, new BlockPos(40, FLAT_Y, 0), 1);
		check(single == 1, "a single click should charge exactly once, got " + single);
		LOGGER.info("PASS heldRightClickOnAnchorRepeatsEveryTick");
	}

	private static int charges(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos pos, int ticks) {
		setUpAnchor(context, sp, pos, 0);
		sp.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:glowstone 64");
		context.waitFor(c -> c.player.getMainHandItem().is(Items.GLOWSTONE)
				&& c.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(pos));
		context.getInput().holdMouseFor(MOUSE_RIGHT, ticks);
		int client = context.computeOnClient(c -> c.level.getBlockState(pos).getValue(RespawnAnchorBlock.CHARGE));
		context.waitTicks(10);
		BlockState server = sp.getServer().computeOnServer(s -> s.overworld().getBlockState(pos));
		check(server.is(Blocks.RESPAWN_ANCHOR) && server.getValue(RespawnAnchorBlock.CHARGE) == client,
				"server should agree with the client's " + client + " charges, got " + server);
		return client;
	}

	/** With both mods installed, Anchor Optimizer's instant anchor removal must still work. */
	private static void anchorOptimizerPredictionStillWorks(ClientGameTestContext context, TestSingleplayerContext sp) {
		BlockPos pos = new BlockPos(60, FLAT_Y, 0);
		setUpAnchor(context, sp, pos, 4);
		sp.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
		context.waitFor(c -> c.player.getMainHandItem().isEmpty());
		BlockState rightAfterClick = context.computeOnClient(c -> {
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
			c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, hit);
			return c.level.getBlockState(pos);
		});
		check(rightAfterClick.isAir(), "Anchor Optimizer should remove the detonated anchor instantly, got " + rightAfterClick);
		context.waitTicks(10);
		BlockState server = sp.getServer().computeOnServer(s -> s.overworld().getBlockState(pos));
		check(!server.is(Blocks.RESPAWN_ANCHOR), "server should have exploded the anchor, got " + server);
		LOGGER.info("PASS anchorOptimizerPredictionStillWorks");
	}

	private static void setUpAnchor(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos pos, int charge) {
		BlockState anchor = Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, charge);
		sp.getServer().runOnServer(server -> {
			var level = server.overworld();
			for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 3, 4))) {
				level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
			}
			for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, -1, 4))) {
				level.setBlockAndUpdate(p, Blocks.OBSIDIAN.defaultBlockState());
			}
			level.setBlockAndUpdate(pos, anchor);
		});
		// 2 blocks east of the anchor, facing it and looking a bit down: the crosshair is on its east face.
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.1f %d %.1f 90 30", pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5));
		context.waitFor(c -> c.player.blockPosition().closerThan(pos, 4) && c.level.getBlockState(pos) == anchor);
		context.waitTicks(5);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
