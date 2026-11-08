package io.github.freshfall999.anchoroptimizer.mixin;

import io.github.freshfall999.anchoroptimizer.AnchorPredictor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla only runs {@code explode} on the server, so on the client a detonated anchor stays visible
 * until the server's block update arrives (one full ping). We hook the same method on the client and
 * let {@link AnchorPredictor} remove the anchor locally inside vanilla's own block prediction.
 * <p>
 * The original method is not cancelled or changed: it still returns exactly what vanilla returns,
 * and the client still sends exactly the same packet.
 */
@Mixin(RespawnAnchorBlock.class)
public abstract class RespawnAnchorBlockMixin {
	@Inject(method = "useWithoutItem", at = @At("HEAD"))
	private void anchoroptimizer$predictDetonation(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
		if (level.isClientSide()) {
			AnchorPredictor.tryPredictDetonation(state, level, pos);
		}
	}
}
