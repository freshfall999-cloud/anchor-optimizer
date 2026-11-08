package io.github.freshfall999.anchoroptimizer.mixin;

import io.github.freshfall999.anchoroptimizer.AnticheatDetection;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	// A new login means a new server (or a new backend server behind a proxy): detect again from scratch.
	@Inject(method = "handleLogin", at = @At("TAIL"))
	private void anchoroptimizer$resetDetection(ClientboundLoginPacket packet, CallbackInfo ci) {
		AnticheatDetection.reset();
	}
}
