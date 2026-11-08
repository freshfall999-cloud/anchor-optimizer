package io.github.freshfall999.anchoroptimizer.test;

import io.github.freshfall999.anchoroptimizer.AnchorOptimizerConfig;
import io.github.freshfall999.anchoroptimizer.AnticheatDetection;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Boots a real client + integrated server and checks the anchor prediction end to end.
 * Run with {@code ./gradlew runClientGameTest} (needs a display; on a headless box use {@code xvfb-run}).
 */
public class AnchorOptimizerClientGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("AnchorOptimizerGameTest");
	/** Surface of the default superflat world (grass is at y = -61). */
	private static final int FLAT_Y = -60;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getenv("ANCHOR_TEST_SERVER") != null) {
			return; // running ServerAnchorTest against an external server instead
		}

		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getServer().runCommand("gamemode creative @a");
			sp.getClientWorld().waitForChunksRender();

			detonationIsPredictedInOverworld(context, sp);
			nextAnchorCanBePlacedBeforeServerAnswers(context, sp);
			wrongPredictionIsRolledBack(context, sp);
			disabledBehavesLikeVanilla(context, sp);
			netherAnchorSetsSpawnAndIsNotPredicted(context, sp);
			pausedOnServersWithTransactionAnticheat(context, sp);
		}

		LOGGER.info("All Anchor Optimizer game tests passed");
	}

	/** Clicking a charged anchor in the overworld removes it on the client before the server has even seen the click. */
	private static void detonationIsPredictedInOverworld(ClientGameTestContext context, TestSingleplayerContext sp) {
		BlockPos pos = new BlockPos(0, FLAT_Y, 0);
		setUpAnchor(context, sp, Level.OVERWORLD, pos, 4);
		emptyMainHand(context, sp);

		BlockState clientRightAfterClick = context.computeOnClient(client -> {
			clickTopFace(client, pos);
			return client.level.getBlockState(pos);
		});
		check(clientRightAfterClick.isAir(), "client should show air immediately after clicking, got " + clientRightAfterClick);

		context.waitTicks(10);
		BlockState server = serverState(sp, Level.OVERWORLD, pos);
		check(isGone(server), "server should have exploded the anchor, got " + server);
		BlockState clientAfterAck = context.computeOnClient(client -> client.level.getBlockState(pos));
		check(clientAfterAck == server, "client should match the server after it confirmed, got " + clientAfterAck + " vs " + server);
		LOGGER.info("PASS detonationIsPredictedInOverworld");
	}

	/**
	 * The point of the mod: right after detonating, the next anchor can be placed in the same spot (by clicking
	 * the floor) without waiting for the server. Both clicks are sent back-to-back, before the server has
	 * processed either, and the server must accept the placement.
	 */
	private static void nextAnchorCanBePlacedBeforeServerAnswers(ClientGameTestContext context, TestSingleplayerContext sp) {
		BlockPos pos = new BlockPos(40, FLAT_Y, 0);
		setUpAnchor(context, sp, Level.OVERWORLD, pos, 4);
		sp.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:respawn_anchor 64");
		context.waitFor(client -> client.player.getMainHandItem().is(Blocks.RESPAWN_ANCHOR.asItem()));

		BlockState clientAfterPlace = context.computeOnClient(client -> {
			clickTopFace(client, pos); // detonate (anchor item isn't glowstone, so this explodes)
			check(client.level.getBlockState(pos).isAir(), "anchor should be gone client-side after detonating");
			clickTopFace(client, pos.below()); // place the next anchor on the floor where the old one was
			return client.level.getBlockState(pos);
		});
		check(isAnchor(clientAfterPlace, 0), "client should show the newly placed anchor, got " + clientAfterPlace);

		context.waitTicks(10);
		BlockState server = serverState(sp, Level.OVERWORLD, pos);
		check(isAnchor(server, 0), "server should have exploded the old anchor and accepted the new one, got " + server);
		BlockState client = context.computeOnClient(c -> c.level.getBlockState(pos));
		check(isAnchor(client, 0), "client should agree with the server, got " + client);
		LOGGER.info("PASS nextAnchorCanBePlacedBeforeServerAnswers");
	}

	/** If the server does not explode the anchor, the client must be put back to what the server says. */
	private static void wrongPredictionIsRolledBack(ClientGameTestContext context, TestSingleplayerContext sp) {
		BlockPos pos = new BlockPos(80, FLAT_Y, 0);
		setUpAnchor(context, sp, Level.OVERWORLD, pos, 0); // server: empty anchor, clicking it does nothing
		emptyMainHand(context, sp);

		BlockState clientRightAfterClick = context.computeOnClient(client -> {
			// Make the client wrongly believe the anchor is charged (as if a desync happened).
			client.level.setBlock(pos, anchor(4), 19);
			clickTopFace(client, pos);
			return client.level.getBlockState(pos);
		});
		check(clientRightAfterClick.isAir(), "client should have predicted the detonation, got " + clientRightAfterClick);

		context.waitTicks(10);
		BlockState server = serverState(sp, Level.OVERWORLD, pos);
		check(isAnchor(server, 0), "server anchor must be untouched, got " + server);
		BlockState clientAfterAck = context.computeOnClient(client -> client.level.getBlockState(pos));
		check(isAnchor(clientAfterAck, 0), "client should be rolled back to the server's anchor, got " + clientAfterAck);
		LOGGER.info("PASS wrongPredictionIsRolledBack");
	}

	/** With the toggle off, the anchor stays visible until the server answers (vanilla behaviour). */
	private static void disabledBehavesLikeVanilla(ClientGameTestContext context, TestSingleplayerContext sp) {
		BlockPos pos = new BlockPos(120, FLAT_Y, 0);
		setUpAnchor(context, sp, Level.OVERWORLD, pos, 4);
		emptyMainHand(context, sp);

		AnchorOptimizerConfig.setEnabled(false);
		try {
			BlockState clientRightAfterClick = context.computeOnClient(client -> {
				clickTopFace(client, pos);
				return client.level.getBlockState(pos);
			});
			check(isAnchor(clientRightAfterClick, 4), "disabled: client should still show the anchor, got " + clientRightAfterClick);

			context.waitTicks(10);
			BlockState server = serverState(sp, Level.OVERWORLD, pos);
			check(isGone(server), "disabled: server should still explode the anchor, got " + server);
			BlockState clientAfter = context.computeOnClient(client -> client.level.getBlockState(pos));
			check(clientAfter == server, "disabled: client should match the server once its update arrives, got " + clientAfter + " vs " + server);
		} finally {
			AnchorOptimizerConfig.setEnabled(true);
		}
		LOGGER.info("PASS disabledBehavesLikeVanilla");
	}

	/** In the Nether anchors set your spawn, so nothing must be predicted there. */
	private static void netherAnchorSetsSpawnAndIsNotPredicted(ClientGameTestContext context, TestSingleplayerContext sp) {
		BlockPos pos = new BlockPos(0, 100, 0);
		setUpAnchor(context, sp, Level.NETHER, pos, 4);
		emptyMainHand(context, sp);

		BlockState clientRightAfterClick = context.computeOnClient(client -> {
			clickTopFace(client, pos);
			return client.level.getBlockState(pos);
		});
		check(isAnchor(clientRightAfterClick, 4), "nether: client must not predict, got " + clientRightAfterClick);

		context.waitTicks(10);
		BlockState server = serverState(sp, Level.NETHER, pos);
		check(isAnchor(server, 4), "nether: server should keep the anchor (sets spawn), got " + server);
		BlockState clientAfter = context.computeOnClient(client -> client.level.getBlockState(pos));
		check(isAnchor(clientAfter, 4), "nether: client should still show the anchor, got " + clientAfter);
		LOGGER.info("PASS netherAnchorSetsSpawnAndIsNotPredicted");
	}

	/**
	 * Anticheats like GrimAC send ping ("transaction") packets to track what the client has received; the vanilla
	 * server never does. Once one arrives, prediction must pause and the client must behave exactly like vanilla.
	 */
	private static void pausedOnServersWithTransactionAnticheat(ClientGameTestContext context, TestSingleplayerContext sp) {
		BlockPos pos = new BlockPos(160, FLAT_Y, 0);
		setUpAnchor(context, sp, Level.OVERWORLD, pos, 4);
		emptyMainHand(context, sp);

		check(!context.computeOnClient(client -> AnticheatDetection.detected()), "vanilla server must not look like an anticheat");
		sp.getServer().runOnServer(server -> server.getPlayerList().getPlayers()
				.forEach(player -> player.connection.send(new ClientboundPingPacket(1234))));
		context.waitFor(client -> AnticheatDetection.detected());

		try {
			BlockState clientRightAfterClick = context.computeOnClient(client -> {
				clickTopFace(client, pos);
				return client.level.getBlockState(pos);
			});
			check(isAnchor(clientRightAfterClick, 4), "paused: client should still show the anchor, got " + clientRightAfterClick);

			context.waitTicks(10);
			BlockState server = serverState(sp, Level.OVERWORLD, pos);
			check(isGone(server), "paused: server should still explode the anchor, got " + server);
			BlockState clientAfter = context.computeOnClient(client -> client.level.getBlockState(pos));
			check(clientAfter == server, "paused: client should match the server once its update arrives, got " + clientAfter + " vs " + server);
		} finally {
			context.runOnClient(client -> AnticheatDetection.reset());
		}
		LOGGER.info("PASS pausedOnServersWithTransactionAnticheat");
	}

	// ---- helpers ----

	private static void setUpAnchor(ClientGameTestContext context, TestSingleplayerContext sp, ResourceKey<Level> dimension, BlockPos pos, int charge) {
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.getLevel(dimension);
			for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 3, 4))) {
				level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
			}
			for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, -1, 4))) {
				level.setBlockAndUpdate(p, Blocks.OBSIDIAN.defaultBlockState());
			}
			level.setBlockAndUpdate(pos, anchor(charge));
		});

		sp.getServer().runCommand(String.format(Locale.ROOT, "execute in %s run tp @a %.1f %d %.1f 90 30",
				dimension.identifier(), pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5));
		context.waitFor(client -> client.level != null
				&& client.level.dimension() == dimension
				&& client.player.blockPosition().closerThan(pos, 4)
				&& client.level.getBlockState(pos) == anchor(charge));
		// Give the server a few ticks to receive the teleport confirmation; it ignores clicks until then.
		context.waitTicks(5);
	}

	private static void emptyMainHand(ClientGameTestContext context, TestSingleplayerContext sp) {
		sp.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
		context.waitFor(client -> client.player.getMainHandItem().isEmpty());
	}

	/** Exactly what a real right click on the top face of {@code pos} does. */
	private static void clickTopFace(Minecraft client, BlockPos pos) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
		client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
	}

	private static BlockState serverState(TestSingleplayerContext sp, ResourceKey<Level> dimension, BlockPos pos) {
		return sp.getServer().computeOnServer(server -> server.getLevel(dimension).getBlockState(pos));
	}

	private static BlockState anchor(int charge) {
		return Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, charge);
	}

	/** After an explosion the spot is air, or fire (anchor explosions start fires). */
	private static boolean isGone(BlockState state) {
		return state.isAir() || state.is(Blocks.FIRE);
	}

	private static boolean isAnchor(BlockState state, int charge) {
		return state.is(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) == charge;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
