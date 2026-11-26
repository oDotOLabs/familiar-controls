package familiar.controls.client.mixin;

import familiar.controls.client.SprintKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Shift 衝刺「自實作」第一部分：不改 Options 按鍵，也不碰 KeyMapping 的 isDown 狀態，
 * 只在 KeyboardInput 組出 Input 時，用 GLFW 的原始按鍵狀態取代 keySprint.isDown()。
 *
 * 第二部分在 LocalPlayerSprintMixin：原版的 sprint 是閂鎖狀態，放開按鍵不會自動停，
 * 必須另外在 aiStep 結尾主動解除，這才是「鬆開 Shift 就停」的真正修補點。
 */
@Mixin(KeyboardInput.class)
public abstract class SprintMixin {

	@Unique
	private boolean familiar$toggleOn = false;
	@Unique
	private boolean familiar$lastShiftDown = false;

	/** ordinal 6 = KeyboardInput.tick() 的第七個 isDown()，也就是 keySprint。 */
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z", ordinal = 6))
	private boolean familiar$sprintState(KeyMapping mapping) {
		Minecraft mc = Minecraft.getInstance();
		if (!SprintKeys.active(mc)) {
			return mapping.isDown();
		}
		boolean down = SprintKeys.rawShiftDown(mc, SprintKeys.FORCE_TAKEOVER);
		if (SprintKeys.FORCE_TAKEOVER || !mc.options.toggleSprint().get()) {
			return down;
		}
		// toggle sprint 也自己算，不依賴會被切換掉的 key state；只在上緣翻轉。
		if (down && !this.familiar$lastShiftDown) {
			this.familiar$toggleOn = !this.familiar$toggleOn;
		}
		this.familiar$lastShiftDown = down;
		return this.familiar$toggleOn;
	}
}
