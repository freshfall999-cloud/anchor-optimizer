package io.github.freshfall999.anchoroptimizer.test;

import io.github.freshfall999.anchoroptimizer.AnchorOptimizerConfig;
import io.github.freshfall999.anchoroptimizer.AnticheatDetection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in test against a real, separately running server (for example Paper with an anticheat), optionally
 * behind {@code servertest/latency_proxy.py} to simulate ping. It only runs when {@code ANCHOR_TEST_SERVER}
 * is set; see {@code servertest/README.md}.
 *
 * <p>The client plays like a person would: real hotbar key presses and real right clicks on whatever the
 * crosshair is pointing at, at 20 ticks per second. Every scenario runs with the mod on and then off.
 */
public class ServerAnchorTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("AnchorServerTest");
	private static final int MOUSE_RIGHT = 1; // GLFW_MOUSE_BUTTON_RIGHT
	private static final long TICK_NANOS = 50_000_000L;
	private static final int SLOT_ANCHOR = 0;
	private static final int SLOT_GLOWSTONE = 1;
	private static final int SLOT_TOTEM = 2;
	/** Default superflat world: the floor block is at y = -61, so anchors sit at y = -60. */
	private static final BlockPos ANCHOR = new BlockPos(4, -60, 4);
	private static final double EYE_HEIGHT = 1.62;

	private ClientGameTestContext context;
	private Rcon rcon;
	private String player;
	private long nextTickAt;
	private final StringBuilder report = new StringBuilder();

	private enum Step {
		PLACE, CHARGE, DETONATE
	}

	/** ON: default settings. FORCED: prediction even where an anticheat was detected. OFF: vanilla. */
	private enum Mode {
		ON(true, true), FORCED(true, false), OFF(false, true);

		final boolean enabled;
		final boolean pauseOnAnticheat;

		Mode(boolean enabled, boolean pauseOnAnticheat) {
			this.enabled = enabled;
			this.pauseOnAnticheat = pauseOnAnticheat;
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		String address = System.getenv("ANCHOR_TEST_SERVER");
		if (address == null) {
			return;
		}

		this.context = context;
		String[] rconAddress = env("ANCHOR_TEST_RCON", "127.0.0.1:25575").split(":");
		try (Rcon rcon = new Rcon(rconAddress[0], Integer.parseInt(rconAddress[1]), env("ANCHOR_TEST_RCON_PASSWORD", ""))) {
			this.rcon = rcon;
			connect(address);
			setUpPlayer();

			line("# " + env("ANCHOR_TEST_LABEL", "server " + address));
			int cycles = Integer.parseInt(env("ANCHOR_TEST_CYCLES", "20"));
			line("anticheat that checks client-received state detected: " + context.computeOnClient(c -> AnticheatDetection.detected()));
			for (Mode mode : Mode.values()) {
				detonationLatency(mode, 5);
				combo(mode, cycles);
			}
			line("ping shown in tab list: " + tabListPing() + " ms");

			disconnect();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			AnchorOptimizerConfig.setEnabled(true);
			AnchorOptimizerConfig.setPauseOnAnticheatServers(true);
			writeReport();
		}
	}

	// ---- scenarios ----

	/** How long after the click until the detonated anchor disappears on screen. */
	private void detonationLatency(Mode mode, int repetitions) {
		apply(mode);
		mark("detonation-latency mod=" + mode + " begin");
		List<Long> millis = new ArrayList<>();
		List<Integer> ticks = new ArrayList<>();
		for (int i = 0; i < repetitions; i++) {
			resetArena(4);
			select(SLOT_TOTEM);
			requireCrosshairOn(ANCHOR, Direction.EAST);

			context.getInput().holdMouseFor(MOUSE_RIGHT, 0);
			long clickTick = tick(); // the click is handled during this tick
			int n = 1;
			while (clientBlock().is(Blocks.RESPAWN_ANCHOR)) {
				if (n++ > 100) {
					throw new AssertionError("anchor never disappeared on the client");
				}
				tick();
			}
			millis.add((System.nanoTime() - clickTick) / 1_000_000);
			ticks.add(n);
			for (int t = 0; serverHasAnchor(); t++) {
				if (t > 100) {
					throw new AssertionError("server never exploded the anchor");
				}
				tick();
			}
		}
		mark("detonation-latency mod=" + mode + " end");
		line(String.format(Locale.ROOT, "mod %-6s | detonated anchor disappears on screen after: %s ms (avg %d ms), in client tick #%s",
				mode, millis, Math.round(millis.stream().mapToLong(Long::longValue).average().orElse(0)), ticks));
	}

	/**
	 * A fast anchor loop with the crosshair fixed on the anchor spot: place, charge once, detonate, repeat.
	 * One action every 2 ticks (key press, then click), i.e. one anchor every 300 ms.
	 */
	private void combo(Mode mode, int cycles) {
		apply(mode);
		resetArena(-1);
		mark("combo mod=" + mode + " begin");
		int[] wrongTarget = new int[Step.values().length];
		for (int i = 0; i < cycles; i++) {
			select(SLOT_ANCHOR);
			click(Step.PLACE, wrongTarget);
			select(SLOT_GLOWSTONE);
			click(Step.CHARGE, wrongTarget);
			select(SLOT_TOTEM);
			click(Step.DETONATE, wrongTarget);
		}
		ticks(40); // let everything arrive
		mark("combo mod=" + mode + " end");

		int anchorsLeft = context.computeOnClient(c -> c.player.getInventory().getItem(SLOT_ANCHOR).getCount());
		int glowstoneLeft = context.computeOnClient(c -> c.player.getInventory().getItem(SLOT_GLOWSTONE).getCount());
		int anchorsInArena = countInArena(Items.RESPAWN_ANCHOR);
		int glowstoneBlocks = countInArena(Items.GLOWSTONE);
		boolean clientAgrees = clientBlock().is(Blocks.RESPAWN_ANCHOR) == serverHasAnchor();
		int placed = 64 - anchorsLeft;
		int detonated = placed - anchorsInArena;
		line(String.format(Locale.ROOT,
				"mod %-6s | %d cycles: %d anchors placed, %d detonated, %d glowstone used (%d of it misplaced as blocks) "
						+ "| clicks aimed at the wrong thing: place %d, charge %d, detonate %d | client matches server at the end: %s",
				mode, cycles, placed, detonated, 64 - glowstoneLeft, glowstoneBlocks,
				wrongTarget[Step.PLACE.ordinal()], wrongTarget[Step.CHARGE.ordinal()], wrongTarget[Step.DETONATE.ordinal()],
				clientAgrees));
	}

	// ---- player actions (real input) ----

	private void select(int slot) {
		context.getInput().holdKeyFor(options -> options.keyHotbarSlots[slot], 0);
		tick();
	}

	/** Right-clicks whatever the crosshair points at and notes whether that's what this step should hit. */
	private void click(Step step, int[] wrongTarget) {
		boolean right = context.computeOnClient(c -> {
			if (!(c.hitResult instanceof BlockHitResult hit) || c.hitResult.getType() != HitResult.Type.BLOCK) {
				return false;
			}
			BlockState target = c.level.getBlockState(hit.getBlockPos());
			return switch (step) {
				case PLACE -> hit.getBlockPos().equals(ANCHOR.below()) && hit.getDirection() == Direction.UP;
				case CHARGE -> hit.getBlockPos().equals(ANCHOR) && target.is(Blocks.RESPAWN_ANCHOR) && target.getValue(RespawnAnchorBlock.CHARGE) < 4;
				case DETONATE -> hit.getBlockPos().equals(ANCHOR) && target.is(Blocks.RESPAWN_ANCHOR) && target.getValue(RespawnAnchorBlock.CHARGE) > 0;
			};
		});
		if (!right) {
			wrongTarget[step.ordinal()]++;
		}
		if (System.getenv("ANCHOR_TEST_VERBOSE") != null) {
			LOGGER.info("click {} ok={} crosshair={} anchorSpot={} | server: anchorSpot={} anchorsLeft={}", step, right, context.computeOnClient(c -> c.hitResult instanceof BlockHitResult hit
					? hit.getBlockPos().toShortString() + " " + hit.getDirection() + " " + c.level.getBlockState(hit.getBlockPos())
					: String.valueOf(c.hitResult)), clientBlock(),
					rcon.command(String.format(Locale.ROOT, "execute if block %d %d %d minecraft:respawn_anchor", ANCHOR.getX(), ANCHOR.getY(), ANCHOR.getZ())).contains("passed") ? "anchor" : "no anchor",
					rcon.command("clear " + player + " minecraft:respawn_anchor 0"));
		}
		context.getInput().holdMouseFor(MOUSE_RIGHT, 0);
		tick();
	}

	// ---- setup ----

	private void connect(String address) {
		context.runOnClient(client -> ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
				new ServerData("Anchor Optimizer test", address, ServerData.Type.OTHER), false, null));
		waitUntil("joining " + address, c -> c.player != null && c.level != null && c.screen == null, 1200);
		player = context.computeOnClient(c -> c.getUser().getName());
		LOGGER.info("Joined {} as {}", address, player);
	}

	private void setUpPlayer() {
		cmd("gamemode survival %s", player);
		cmd("clear %s", player);
		// Survive point-blank anchor explosions and don't get knocked off the spot.
		cmd("effect give %s minecraft:resistance infinite 4 true", player);
		cmd("effect give %s minecraft:fire_resistance infinite 0 true", player);
		cmd("effect give %s minecraft:saturation infinite 0 true", player);
		cmd("attribute %s minecraft:explosion_knockback_resistance base set 1", player);

		resetArena(4);
		requireCrosshairOn(ANCHOR, Direction.EAST);
		resetArena(-1);
		requireCrosshairOn(ANCHOR.below(), Direction.UP);
	}

	/** Clears the arena (charge -1: no anchor), puts the player 2 blocks east of the anchor spot and refills the hotbar. */
	private void resetArena(int charge) {
		int x = ANCHOR.getX();
		int y = ANCHOR.getY();
		int z = ANCHOR.getZ();
		cmd("fill %d %d %d %d %d %d minecraft:air", x - 5, y, z - 5, x + 5, y + 4, z + 5);
		cmd("fill %d %d %d %d %d %d minecraft:obsidian", x - 5, y - 1, z - 5, x + 5, y - 1, z + 5);
		if (charge >= 0) {
			cmd("setblock %d %d %d minecraft:respawn_anchor[charges=%d]", x, y, z, charge);
		}

		// Aim at the lower part of the anchor's east face. With no anchor there, the same ray lands on the top of
		// the floor block under the anchor spot, so placing puts the anchor back on the same spot.
		double standX = x + 2.5;
		double standZ = z + 0.5;
		double dx = (x + 1.0) - standX;
		double dy = (y + 0.25) - (y + EYE_HEIGHT);
		double dz = (z + 0.5) - standZ;
		float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		cmd("tp %s %.3f %d %.3f %.3f %.3f", player, standX, y, standZ, yaw, pitch);

		cmd("item replace entity %s hotbar.0 with minecraft:respawn_anchor 64", player);
		cmd("item replace entity %s hotbar.1 with minecraft:glowstone 64", player);
		cmd("item replace entity %s hotbar.2 with minecraft:totem_of_undying 1", player);

		BlockState expected = charge >= 0
				? Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, charge)
				: Blocks.AIR.defaultBlockState();
		waitUntil("arena reset", c -> c.level.getBlockState(ANCHOR) == expected
				&& c.player.position().distanceToSqr(standX, y, standZ) < 1.0E-4
				&& c.player.getInventory().getItem(SLOT_ANCHOR).getCount() == 64
				&& c.player.getInventory().getItem(SLOT_GLOWSTONE).getCount() == 64
				&& c.player.getInventory().getItem(SLOT_TOTEM).is(Items.TOTEM_OF_UNDYING), 200);
		ticks(5);
	}

	private void requireCrosshairOn(BlockPos pos, Direction face) {
		String target = context.computeOnClient(c -> c.hitResult instanceof BlockHitResult hit && c.hitResult.getType() == HitResult.Type.BLOCK
				? hit.getBlockPos().toShortString() + " " + hit.getDirection()
				: String.valueOf(c.hitResult));
		if (!target.equals(pos.toShortString() + " " + face)) {
			throw new AssertionError("crosshair should be on " + pos.toShortString() + " " + face + " but is on " + target);
		}
	}

	private void disconnect() {
		context.runOnClient(client -> {
			client.level.disconnect(Component.literal("Disconnecting"));
			client.disconnectWithProgressScreen();
		});
		context.waitFor(client -> client.level == null);
		context.setScreen(TitleScreen::new);
	}

	// ---- inspection ----

	private BlockState clientBlock() {
		return context.computeOnClient(c -> c.level.getBlockState(ANCHOR));
	}

	private boolean serverHasAnchor() {
		return rcon.command(String.format(Locale.ROOT, "execute if block %d %d %d minecraft:respawn_anchor",
				ANCHOR.getX(), ANCHOR.getY(), ANCHOR.getZ())).contains("passed");
	}

	private int countInArena(Item item) {
		return context.computeOnClient(c -> {
			int count = 0;
			for (BlockPos pos : BlockPos.betweenClosed(ANCHOR.offset(-5, 0, -5), ANCHOR.offset(5, 4, 5))) {
				if (c.level.getBlockState(pos).getBlock().asItem() == item) {
					count++;
				}
			}
			return count;
		});
	}

	private int tabListPing() {
		return context.computeOnClient(c -> {
			PlayerInfo info = c.getConnection().getPlayerInfo(c.player.getUUID());
			return info == null ? -1 : info.getLatency();
		});
	}

	// ---- timing ----

	/** Runs one client tick, paced to real time (20 per second) like a normal client. Returns when the tick started. */
	private long tick() {
		long now = System.nanoTime();
		if (nextTickAt > now) {
			LockSupport.parkNanos(nextTickAt - now);
		}
		long start = System.nanoTime();
		nextTickAt = Math.max(nextTickAt + TICK_NANOS, start);
		context.waitTick();
		if (player != null && context.computeOnClient(c -> c.level == null)) {
			throw new AssertionError("disconnected from the server");
		}
		return start;
	}

	private void ticks(int count) {
		for (int i = 0; i < count; i++) {
			tick();
		}
	}

	private void waitUntil(String what, Predicate<Minecraft> condition, int maxTicks) {
		for (int i = 0; i < maxTicks; i++) {
			if (context.computeOnClient(condition::test)) {
				return;
			}
			tick();
		}
		throw new AssertionError("timed out waiting for " + what);
	}

	// ---- misc ----

	private void cmd(String format, Object... args) {
		rcon.command(String.format(Locale.ROOT, format, args));
	}

	private void mark(String text) {
		cmd("say [anchortest] %s", text);
	}

	private void line(String text) {
		LOGGER.info(text);
		report.append(text).append('\n');
	}

	private void writeReport() {
		String path = System.getenv("ANCHOR_TEST_REPORT");
		if (path != null) {
			try {
				Files.writeString(Path.of(path), report.toString());
			} catch (IOException e) {
				LOGGER.error("Could not write report to {}", path, e);
			}
		}
	}

	private static void apply(Mode mode) {
		AnchorOptimizerConfig.setEnabled(mode.enabled);
		AnchorOptimizerConfig.setPauseOnAnticheatServers(mode.pauseOnAnticheat);
	}

	private static String env(String name, String fallback) {
		String value = System.getenv(name);
		return value == null || value.isBlank() ? fallback : value;
	}
}
