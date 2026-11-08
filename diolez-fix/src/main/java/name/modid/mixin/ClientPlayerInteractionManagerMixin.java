package name.modid.mixin;

import name.modid.AnchorOptimizerConfig;
import name.modid.DiolezzAnchorOptimizer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class ClientPlayerInteractionManagerMixin {
	// Yarn: blockBreakingCooldown. Note: this is the block *breaking* delay, not the right-click delay.
	@Shadow
	private int destroyDelay;

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void diolezanchoroptimizer$onInteractBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (AnchorOptimizerConfig.suppressAnchorInteractionCooldown) {
			// Fix for 1.21.11: the 1.21.1 build called Entity#getWorld (intermediary method_37908), which no longer
			// exists. That crashed with NoSuchMethodError on every block right-click. Entity#level() replaces it.
			Level world = player.level();
			BlockState state = world.getBlockState(hitResult.getBlockPos());
			if (state.getBlock() instanceof RespawnAnchorBlock && this.destroyDelay > 0) {
				DiolezzAnchorOptimizer.LOGGER.debug("[DiolezzAnchorOptimizer] Clearing interaction cooldown for RespawnAnchor at {}", hitResult.getBlockPos());
				this.destroyDelay = 0;
			}
		}
	}
}
