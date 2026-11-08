package io.github.freshfall999.anchoroptimizer.mixin;

import io.github.freshfall999.anchoroptimizer.FastAnchorUse;
import net.minecraft.client.Minecraft;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow
	private int rightClickDelay;

	// Right after startUseItem sets rightClickDelay = 4.
	@Inject(method = "startUseItem", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;rightClickDelay:I",
			opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
	private void anchoroptimizer$fastAnchorUse(CallbackInfo ci) {
		if (FastAnchorUse.appliesNow((Minecraft) (Object) this)) {
			this.rightClickDelay = FastAnchorUse.DELAY_TICKS;
		}
	}
}
