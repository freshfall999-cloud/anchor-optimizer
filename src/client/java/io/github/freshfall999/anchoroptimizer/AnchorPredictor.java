package io.github.freshfall999.anchoroptimizer;

import io.github.freshfall999.anchoroptimizer.mixin.ClientLevelAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;

/**
 * Predicts a respawn anchor detonation on the client.
 *
 * <p>Since 1.19 every block interaction runs inside a numbered "prediction" on the client: any block the
 * client changes during it is remembered together with the last state the server confirmed. When the
 * server acknowledges that interaction it either already sent the real result (which then wins) or the
 * client is rolled back to the confirmed state. Vanilla uses this for placing and breaking blocks; we use
 * the exact same mechanism for the one anchor step vanilla leaves server-only: removing the anchor
 * when it explodes.
 *
 * <p>Consequences:
 * <ul>
 *     <li>The packet sent to the server is the normal vanilla packet; nothing extra is sent and nothing is
 *     sent earlier or more often than without the mod.</li>
 *     <li>The server stays authoritative. If it does not explode the anchor (e.g. the anchor sets spawn in
 *     this dimension, or a plugin cancels the click), the anchor comes back as soon as the server answers.</li>
 *     <li>Only the anchor block itself is predicted. Explosion damage, knockback and terrain destruction
 *     are left entirely to the server.</li>
 * </ul>
 */
public final class AnchorPredictor {
	private AnchorPredictor() {
	}

	/**
	 * Called at the start of {@code RespawnAnchorBlock#useWithoutItem} on the client. This is the exact
	 * point where the server would call {@code explode} for the same click.
	 *
	 * @return whether the anchor was removed client-side
	 */
	public static boolean tryPredictDetonation(BlockState state, Level level, BlockPos pos) {
		if (!AnchorOptimizerConfig.isEnabled()) {
			return false;
		}

		if (!(level instanceof ClientLevel clientLevel)) {
			return false;
		}

		// Only act inside vanilla's own predicted interaction, so the server's acknowledgement is guaranteed
		// to either confirm the removal or restore the anchor. Never touch the world outside of that.
		if (!((ClientLevelAccessor) clientLevel).anchoroptimizer$getBlockStatePredictionHandler().isPredicting()) {
			return false;
		}

		// Uncharged anchors do nothing (vanilla returns PASS before exploding).
		if (!state.is(Blocks.RESPAWN_ANCHOR) || state.getValue(RespawnAnchorBlock.CHARGE) == 0) {
			return false;
		}

		// Where anchors work they set your spawn instead of exploding.
		if (anchorsSetSpawnIn(clientLevel)) {
			return false;
		}

		// Mirror the first thing RespawnAnchorBlock#explode does on the server: serverLevel.removeBlock(pos, false).
		// UPDATE_ALL_IMMEDIATE is what vanilla's client uses for predicted block breaking, so the chunk is
		// re-rendered this frame.
		return clientLevel.setBlock(pos, clientLevel.getFluidState(pos).createLegacyBlock(), Block.UPDATE_ALL_IMMEDIATE);
	}

	/**
	 * In 1.21.11 whether an anchor explodes is the {@code minecraft:gameplay/respawn_anchor_works} environment
	 * attribute, which is not synced to clients. Vanilla only turns it on for the {@code minecraft:the_nether}
	 * dimension type (Paper/Spigot nether worlds use that type too), so that's what we check. If a server
	 * customises this, the worst case is that the anchor briefly disappears and then comes back when the
	 * server answers.
	 */
	public static boolean anchorsSetSpawnIn(ClientLevel level) {
		return level.dimensionTypeRegistration().is(BuiltinDimensionTypes.NETHER);
	}
}
