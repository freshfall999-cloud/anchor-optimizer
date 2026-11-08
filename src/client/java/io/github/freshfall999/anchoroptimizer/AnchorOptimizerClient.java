package io.github.freshfall999.anchoroptimizer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
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

		// Unbound by default; set it in Options > Controls > Key Binds if you want a toggle.
		KeyMapping toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key." + MOD_ID + ".toggle",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				boolean enabled = !AnchorOptimizerConfig.isEnabled();
				AnchorOptimizerConfig.setEnabled(enabled);
				if (client.player != null) {
					client.player.displayClientMessage(Component.translatable(
							"message." + MOD_ID + (enabled ? ".enabled" : ".disabled")), true);
				}
			}
		});

		LOGGER.info("Anchor Optimizer loaded (prediction {})", AnchorOptimizerConfig.isEnabled() ? "enabled" : "disabled");
	}
}
