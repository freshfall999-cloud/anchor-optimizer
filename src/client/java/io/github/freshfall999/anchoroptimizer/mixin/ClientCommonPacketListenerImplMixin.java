package io.github.freshfall999.anchoroptimizer.mixin;

import io.github.freshfall999.anchoroptimizer.AnticheatDetection;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {
	// TAIL is only reached on the client thread (the netty-thread call bails out early to reschedule itself).
	@Inject(method = "handlePing", at = @At("TAIL"))
	private void anchoroptimizer$noticeTransactionPing(ClientboundPingPacket packet, CallbackInfo ci) {
		// Only count pings during gameplay. Fabric API itself pings once during the configuration phase.
		if ((Object) this instanceof ClientPacketListener) {
			AnticheatDetection.onServerPing();
		}
	}
}
