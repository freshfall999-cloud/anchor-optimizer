package io.github.freshfall999.anchoroptimizer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AnchorOptimizerClient implements ClientModInitializer {
	public static final String MOD_ID = "anchoroptimizer";
	public static final Logger LOGGER = LoggerFactory.getLogger("Anchor Optimizer");

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	@Override
	public void onInitializeClient() {
		AnchorOptimizerConfig.load();

		// Both unbound by default; set them in Options > Controls > Key Binds if you want toggles.
		KeyMapping togglePrediction = register("toggle");
		KeyMapping toggleFastUse = register("toggle_fast_use");

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (togglePrediction.consumeClick()) {
				boolean enabled = !AnchorOptimizerConfig.isEnabled();
				AnchorOptimizerConfig.setEnabled(enabled);
				announce(client, enabled ? "enabled" : "disabled");
			}
			while (toggleFastUse.consumeClick()) {
				boolean enabled = !AnchorOptimizerConfig.fastAnchorUse();
				AnchorOptimizerConfig.setFastAnchorUse(enabled);
				announce(client, enabled ? "fast_use_enabled" : "fast_use_disabled");
			}
		});

		LOGGER.info("Anchor Optimizer loaded (prediction {}, fast anchor use {})",
				AnchorOptimizerConfig.isEnabled() ? "on" : "off", AnchorOptimizerConfig.fastAnchorUse() ? "on" : "off");
	}

	private static KeyMapping register(String name) {
		return KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key." + MOD_ID + "." + name,
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				CATEGORY
		));
	}

	private static void announce(Minecraft client, String message) {
		if (client.player != null) {
			client.player.displayClientMessage(Component.translatable("message." + MOD_ID + "." + message), true);
		}
	}
}
