package io.github.freshfall999.anchoroptimizer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Tiny properties-file config: {@code config/anchoroptimizer.properties}.
 */
public final class AnchorOptimizerConfig {
	private static final String FILE_NAME = AnchorOptimizerClient.MOD_ID + ".properties";
	private static final String KEY_ENABLED = "enabled";
	private static final String KEY_PAUSE_ON_ANTICHEAT = "pauseOnAnticheatServers";
	private static final String KEY_FAST_ANCHOR_USE = "fastAnchorUse";
	private static final String COMMENT = """
			Anchor Optimizer
			enabled: turn the anchor prediction on or off (the toggle key changes this too).
			pauseOnAnticheatServers: on servers whose anticheat checks what your client has already received
			(GrimAC and similar), acting on a predicted anchor removal gets flagged and cancelled, so the
			prediction pauses itself there. Only set this to false if you accept getting flagged.
			fastAnchorUse: holding right-click with an anchor or glowstone, or while looking at an anchor, repeats
			every tick instead of every 4 ticks. This sends more packets than vanilla ("FastPlace"), which many
			servers don't allow.""";

	private static volatile boolean enabled = true;
	private static volatile boolean pauseOnAnticheatServers = true;
	private static volatile boolean fastAnchorUse = true;

	private AnchorOptimizerConfig() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
		save();
	}

	public static boolean pauseOnAnticheatServers() {
		return pauseOnAnticheatServers;
	}

	public static void setPauseOnAnticheatServers(boolean value) {
		pauseOnAnticheatServers = value;
		save();
	}

	public static boolean fastAnchorUse() {
		return fastAnchorUse;
	}

	public static void setFastAnchorUse(boolean value) {
		fastAnchorUse = value;
		save();
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			Properties properties = new Properties();
			try (Reader reader = Files.newBufferedReader(path)) {
				properties.load(reader);
				enabled = Boolean.parseBoolean(properties.getProperty(KEY_ENABLED, "true").trim());
				pauseOnAnticheatServers = Boolean.parseBoolean(properties.getProperty(KEY_PAUSE_ON_ANTICHEAT, "true").trim());
				fastAnchorUse = Boolean.parseBoolean(properties.getProperty(KEY_FAST_ANCHOR_USE, "true").trim());
			} catch (IOException | IllegalArgumentException e) {
				AnchorOptimizerClient.LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}
		save(); // writes defaults for missing keys
	}

	public static void save() {
		Path path = path();
		Properties properties = new Properties();
		properties.setProperty(KEY_ENABLED, Boolean.toString(enabled));
		properties.setProperty(KEY_PAUSE_ON_ANTICHEAT, Boolean.toString(pauseOnAnticheatServers));
		properties.setProperty(KEY_FAST_ANCHOR_USE, Boolean.toString(fastAnchorUse));
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				properties.store(writer, COMMENT);
			}
		} catch (IOException e) {
			AnchorOptimizerClient.LOGGER.warn("Could not write {}", path, e);
		}
	}
}
