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

	private static volatile boolean enabled = true;

	private AnchorOptimizerConfig() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
		save();
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static void load() {
		Path path = path();
		if (!Files.exists(path)) {
			save();
			return;
		}

		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(path)) {
			properties.load(reader);
			enabled = Boolean.parseBoolean(properties.getProperty(KEY_ENABLED, "true").trim());
		} catch (IOException | IllegalArgumentException e) {
			AnchorOptimizerClient.LOGGER.warn("Could not read {}, using defaults", path, e);
		}
	}

	public static void save() {
		Path path = path();
		Properties properties = new Properties();
		properties.setProperty(KEY_ENABLED, Boolean.toString(enabled));
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				properties.store(writer, "Anchor Optimizer - set enabled=false to turn the anchor prediction off");
			}
		} catch (IOException e) {
			AnchorOptimizerClient.LOGGER.warn("Could not write {}", path, e);
		}
	}
}
