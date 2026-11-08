package io.github.freshfall999.anchoroptimizer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Removes vanilla's 4-tick repeat delay on a held right-click after a click that charges a respawn anchor with
 * glowstone. Holding right-click with glowstone on an anchor then charges it every tick (and the use after the last
 * charge detonates it right away) instead of every 4 ticks. Separate clicks were never limited by this delay.
 *
 * <p>After any other use (placing, detonating, anything else) the vanilla delay stays. Applying it more broadly, like
 * "whenever you hold glowstone", keeps firing every tick after the anchor has exploded and places stray glowstone
 * blocks on the anchor spot (seen in testing: 6 in 3 tries on a 100 ms server).
 *
 * <p>Unlike the prediction, this changes how many interaction packets you send while holding right-click, which is
 * what servers and anticheats call "FastPlace". Many servers don't allow that; it can be turned off on its own.
 */
public final class FastAnchorUse {
	/**
	 * The delay vanilla sets is 4. With 1, the next tick's countdown brings it to 0 right before held keys are
	 * checked, so a held right-click fires once per tick, and a single click still fires only once.
	 */
	public static final int DELAY_TICKS = 1;

	private FastAnchorUse() {
	}

	/** Called as startUseItem begins: whether the use about to happen charges the anchor under the crosshair. */
	public static boolean appliesNow(Minecraft client) {
		if (!AnchorOptimizerConfig.fastAnchorUse() || client.player == null || client.level == null) {
			return false;
		}
		if (!(client.hitResult instanceof BlockHitResult hit) || client.hitResult.getType() != HitResult.Type.BLOCK) {
			return false;
		}

		BlockState target = client.level.getBlockState(hit.getBlockPos());
		if (!target.is(Blocks.RESPAWN_ANCHOR) || target.getValue(RespawnAnchorBlock.CHARGE) >= RespawnAnchorBlock.MAX_CHARGES) {
			return false;
		}

		LocalPlayer player = client.player;
		// Sneaking with items in hand skips block interactions, so the glowstone would be placed as a block instead.
		if (player.isSecondaryUseActive()) {
			return false;
		}
		return player.getMainHandItem().is(Items.GLOWSTONE) || player.getOffhandItem().is(Items.GLOWSTONE);
	}
}
