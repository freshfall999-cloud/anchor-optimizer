package name.modid;

public class AnchorOptimizerConfig {
	public static boolean suppressAnchorInteractionCooldown = true;
	public static boolean suppressSwingDelay = true;

	public static void load() {
		DiolezzAnchorOptimizer.LOGGER.info(
				"[DiolezzAnchorOptimizer] Config loaded — suppressAnchorInteractionCooldown={}, suppressSwingDelay={}",
				suppressAnchorInteractionCooldown,
				suppressSwingDelay
		);
	}
}
