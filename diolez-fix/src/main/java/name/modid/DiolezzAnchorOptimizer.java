package name.modid;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class DiolezzAnchorOptimizer implements ClientModInitializer {
	public static final String MOD_ID = "diolezanchoroptimizer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("[DiolezzAnchorOptimizer] Initialized. Anchor packet optimization active.");
		AnchorOptimizerConfig.load();
	}
}
