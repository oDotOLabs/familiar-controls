package familiar.controls.client.mixin;

import familiar.controls.client.SprintKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shift 衝刺「自實作」第二部分：解除原版的 sprint 閂鎖。
 *
 * 原版 shouldStopRunSprinting() 只看「還能不能前進」，不看 sprint 鍵，
 * 所以按住 Shift+W 跑起來後，放開 Shift 仍會一直疾跑。這裡在 aiStep 結尾
 * 依原始 Shift 狀態主動 setSprinting(false)；tick() 後面的 sendIsSprintingIfNeeded()
 * 會在同一 tick 把 STOP_SPRINTING 送到伺服器。
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSprintMixin {

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void familiar$stopSprintOnShiftRelease(CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		Entity player = (Entity) (Object) this;
		if (SprintKeys.active(mc)
			&& !SprintKeys.rawShiftDown(mc, SprintKeys.FORCE_TAKEOVER)
			&& player.isSprinting()) {
			player.setSprinting(false);
		}
	}
}
