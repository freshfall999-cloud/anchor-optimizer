package io.github.freshfall999.anchoroptimizer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Notices when the server runs an anticheat that checks your actions against what your client had actually
 * received at that moment (GrimAC and similar "latency-compensated" anticheats).
 *
 * <p>Those anticheats track your view of the world by sending ping ("transaction") packets around the block
 * updates they send you. The vanilla server never sends ping packets during play, so receiving one means such
 * a plugin is running. Against those anticheats, acting on a predicted anchor removal before the server's update
 * reaches you is judged impossible: in testing on Paper + GrimAC it was flagged ({@code AirLiquidPlace}) and the
 * anchor actions were cancelled. So by default the prediction pauses itself there and you play exactly like vanilla.
 */
public final class AnticheatDetection {
	private static boolean detected;
	private static boolean announced;

	private AnticheatDetection() {
	}

	/** Prediction should not run on this server. */
	public static boolean shouldPause() {
		return detected && AnchorOptimizerConfig.pauseOnAnticheatServers();
	}

	public static boolean detected() {
		return detected;
	}

	/** Called on the client thread whenever the server sends a ping packet. */
	public static void onServerPing() {
		if (detected) {
			return;
		}
		detected = true;
		AnchorOptimizerClient.LOGGER.info("Server sends transaction pings (latency-compensated anticheat); anchor prediction {}",
				AnchorOptimizerConfig.pauseOnAnticheatServers() ? "paused on this server" : "NOT paused (pauseOnAnticheatServers=false)");
		announce();
	}

	/** Called when joining a server (or being moved to another backend server by a proxy). */
	public static void reset() {
		detected = false;
		announced = false;
	}

	private static void announce() {
		if (announced || !AnchorOptimizerConfig.isEnabled() || !AnchorOptimizerConfig.pauseOnAnticheatServers()) {
			return;
		}
		announced = true;
		Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("message.anchoroptimizer.paused_anticheat")
				.withStyle(ChatFormatting.GRAY));
	}
}
