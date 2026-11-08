package name.modid.mixin;

import name.modid.AnchorOptimizerConfig;
import name.modid.DiolezzAnchorOptimizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
	@Shadow
	public LocalPlayer player;
	@Shadow
	public HitResult hitResult;
	// Yarn: itemUseCooldown. Vanilla sets it to 4 on every right-click; it only limits how often a HELD right-click repeats.
	@Shadow
	private int rightClickDelay;

	@Inject(method = "tick", at = @At("HEAD"))
	private void diolezanchoroptimizer$onTick(CallbackInfo ci) {
		if (AnchorOptimizerConfig.suppressSwingDelay) {
			if (this.player != null) {
				if (this.hitResult != null) {
					if (this.hitResult.getType() == HitResult.Type.BLOCK) {
						BlockHitResult blockHit = (BlockHitResult) this.hitResult;
						// Fix for 1.21.11: was Entity#getWorld (method_37908), which no longer exists. This crashed with
						// NoSuchMethodError on every tick the crosshair was on a block.
						if (this.player.level().getBlockState(blockHit.getBlockPos()).getBlock() instanceof RespawnAnchorBlock) {
							if (this.rightClickDelay > 0) {
								DiolezzAnchorOptimizer.LOGGER.debug("[DiolezzAnchorOptimizer] Suppressing itemUseCooldown for RespawnAnchor target");
								this.rightClickDelay = 0;
							}
						}
					}
				}
			}
		}
	}
}
