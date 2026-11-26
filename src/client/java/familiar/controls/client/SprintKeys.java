package familiar.controls.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Shift 衝刺自實作的共用狀態：給 KeyboardInput 與 LocalPlayer 兩個 mixin 用。 */
public final class SprintKeys {

	/** 總開關：想廢棄自實作時改成 false。 */
	public static final boolean ENABLED = true;
	/** 除錯用：忽略使用者的 sprint 綁定，強制用左右 Shift 原始狀態接管。正式版保持 false。 */
	public static final boolean FORCE_TAKEOVER = false;

	private SprintKeys() {
	}

	public static boolean active(Minecraft mc) {
		return ENABLED && mc != null && (FORCE_TAKEOVER || isShiftSprint(mc));
	}

	/** 只有使用者自己把 sprint 綁到左/右 Shift 時才接管（FORCE 模式忽略）。 */
	public static boolean isShiftSprint(Minecraft mc) {
		KeyMapping sprint = mc.options.keySprint;
		return sprint.matches(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_SHIFT))
			|| sprint.matches(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_RIGHT_SHIFT));
	}

	/** 直接問 GLFW 目前有沒有壓著 Shift，完全跳過原版的 toggle key state。 */
	public static boolean rawShiftDown(Minecraft mc, boolean ignoreBinding) {
		KeyMapping sprint = mc.options.keySprint;
		boolean down = false;
		if (ignoreBinding || sprint.matches(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_SHIFT))) {
			down |= InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT);
		}
		if (ignoreBinding || sprint.matches(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_RIGHT_SHIFT))) {
			down |= InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
		}
		return down;
	}
}
