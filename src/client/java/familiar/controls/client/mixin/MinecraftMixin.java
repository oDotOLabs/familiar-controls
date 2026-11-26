package familiar.controls.client.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 熟悉控制方案：左鍵＝主手＋場景優先，右鍵＝只輸出到副手。
 *
 * 主邏輯：左鍵只輸出到主手，右鍵只輸出到副手，沒有任何跨手 fallback。
 *
 * 程式碼實際流程（不是體感優先級）：
 *   - 主手為可蓄力物品（getUseDuration > 0）：按下時就依序嘗試
 *     實體 interact → 方塊 useItemOn → useItem；有動作就攔下原版攻击。
 *     若因此進入 isUsingItem，改為左鍵蓄力、放開時 releaseUsingItem。
 *   - 其它物品 + 實體／空氣：按下時嘗試 interact → useItem；都 PASS 才讓原版攻击。
 *   - 其它物品 + 非空氣方塊：按下時只記 pending；放開時嘗試
 *     useItemOn（內部先是 blockState.useItemOn 場景交互，再 itemStack.useOn 放置）→ useItem；
 *     都 PASS 就不做事。長按超過門檻則呼叫原版 startAttack 開始挖掘。
 *
 * 設計取捨：
 * 1. 拴繩結被拿起是原版實體交互本身的結果，不是跨手 fallback，刻意保留
 *    （才能同時保留副手放置方塊）。
 * 2. GUI／overlay 開啟時 handleKeybinds 不會執行，必須在 tick 主動清狀態，
 *    否則關掉箱子後會補一次「放開左鍵」把箱子重開。
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

	/** 方塊從短按轉為挖掘的門檻，200ms 約等於 4 tick；用牆鐘時間是為了不受 tick rate 影響。 */
	private static final long HOLD_THRESHOLD_MS = 200;

	/** 左鍵已壓在方塊上，但還沒決定要短按使用還是長按挖掘。 */
	@Unique
	private boolean familiar$pending = false;
	/** pending 的起始時間。 */
	@Unique
	private long familiar$pendingSince = 0L;
	/** 門檻到了要手動呼叫 startAttack() 時，避免被自己的 HEAD 注入再攔一次。 */
	@Unique
	private boolean familiar$bypass = false;
	/** 左鍵正在幫主手物品蓄力；用來讓原版以為右鍵仍按著。 */
	@Unique
	private boolean familiar$charging = false;

	/** 與原版 startUseItem 一致：用過之後延遲連續使用，避免同 tick 重複觸發。 */
	@Shadow
	private int rightClickDelay;

	@Shadow
	protected abstract boolean startAttack();

	/** 只有非空氣方塊才算「方塊目標」；實體和空氣走場景優先／原版攻击。 */
	@Unique
	private static boolean familiar$targetsBlock(Minecraft mc) {
		if (mc.level == null || mc.hitResult == null) {
			return false;
		}
		if (mc.hitResult.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockHitResult hit = (BlockHitResult) mc.hitResult;
		return !mc.level.getBlockState(hit.getBlockPos()).isAir();
	}

	/**
	 * 只檢查主手，副手物品絕不能影響左鍵（副手食物導致鏟子／水桶／劍失效就是這個原因）。
	 * 用 getUseDuration > 0 當通用判據，讓弓、盾、食物、十字弓與模組物品自動適用，不寫物品特例。
	 */
	@Unique
	private static boolean familiar$hasChargeableItem(Minecraft mc) {
		if (mc.player == null) {
			return false;
		}
		ItemStack stack = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
		return !stack.isEmpty() && stack.getUseDuration(mc.player) > 0;
	}

	/**
	 * 主手限定的通用「使用」入口，順序刻意模仿原版 startUseItem 並回傳是否真的產生交互：
	 * 實體 interact → 方塊 useItemOn（內部依序是 blockState.useItemOn 場景交互、itemStack.useOn 放置）
	 * → useItem（物品作用：瞬間使用、蓄力）。
	 * Fail 直接放棄、不落到 useItem；只有 SwingSource.CLIENT 才挥手，避免用戶端+伺服器重複動作。
	 */
	@Unique
	private boolean familiar$tryUseMainHand(Minecraft mc) {
		if (mc.player == null || mc.gameMode == null || mc.level == null
			|| mc.player.isUsingItem() || mc.player.isHandsBusy()) {
			return false;
		}
		ItemStack stack = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
		if (stack.isEmpty() || !stack.isItemEnabled(mc.level.enabledFeatures())) {
			return false;
		}
		if (mc.gameMode.isDestroying()) {
			mc.gameMode.stopDestroyBlock();
		}
		this.rightClickDelay = 4;

		if (mc.hitResult != null) {
			if (mc.hitResult.getType() == HitResult.Type.ENTITY) {
				EntityHitResult entityHit = (EntityHitResult) mc.hitResult;
				Entity entity = entityHit.getEntity();
				if (!mc.level.getWorldBorder().isWithinBounds(entity.blockPosition())) {
					return false;
				}
				InteractionResult result = mc.gameMode.interact(mc.player, entity, entityHit, InteractionHand.MAIN_HAND);
				if (result instanceof InteractionResult.Success success) {
					if (success.swingSource() == InteractionResult.SwingSource.CLIENT) {
						mc.player.swing(InteractionHand.MAIN_HAND);
					}
					return true;
				}
			} else if (familiar$targetsBlock(mc)) {
				BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
				InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockHit);
				if (result instanceof InteractionResult.Success success) {
					if (success.swingSource() == InteractionResult.SwingSource.CLIENT) {
						mc.player.swing(InteractionHand.MAIN_HAND);
					}
					return true;
				}
				if (result instanceof InteractionResult.Fail) {
					return false;
				}
			}
		}
		return mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND).consumesAction();
	}

	/** 右鍵（原版 startUseItem）只遍歷副手，沒有任何跨手 fallback。 */
	@Redirect(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/InteractionHand;values()[Lnet/minecraft/world/InteractionHand;"))
	private InteractionHand[] familiar$selectedHands() {
		return new InteractionHand[]{InteractionHand.OFF_HAND};
	}

	/** 左鍵壓下的分流：蓄力 → 非方塊使用 → 方塊等待判定。 */
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void familiar$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
		if (this.familiar$bypass) {
			return;
		}
		Minecraft mc = (Minecraft) (Object) this;
		if (mc.player == null || mc.gameMode == null || mc.gameMode.isSpectator()) {
			return;
		}

		// triedUse 避免蓄力失敗後，非方塊分支再對同一物品呼叫一次 useItem。
		boolean triedUse = false;
		if (!mc.player.isUsingItem() && familiar$hasChargeableItem(mc)) {
			triedUse = true;
			if (familiar$tryUseMainHand(mc)) {
				// 只有真的進入「使用中」才算蓄力；開箱這類 useItemOn 成功不算。
				if (mc.player.isUsingItem()) {
					this.familiar$charging = true;
				}
				cir.setReturnValue(false);
				return;
			}
		}

		if (!familiar$targetsBlock(mc)) {
			// 空氣／實體：瞬間使用型物品（終界之眼等）在這裡生效，失敗則落到原版攻击。
			// 煙火對空氣原版 use() 回 PASS，所以照樣只是拿著，刻意不寫特例。
			if (!triedUse && familiar$tryUseMainHand(mc)) {
				cir.setReturnValue(false);
			}
			return;
		}
		// 方塊：先記下來，真正的使用延到放開時，才能和長按挖掘共存。
		this.familiar$pending = true;
		this.familiar$pendingSince = System.currentTimeMillis();
		cir.setReturnValue(false);
	}

	/**
	 * ordinal 2 是 26.2 handleKeybinds 裡「!keyUse.isDown() → releaseUsingItem」的檢查。
	 * 左鍵蓄力時回傳 true，避免原版因為右鍵沒按而當 tick 就放開；ordinal 3 的連續使用檢查沒動到。
	 */
	@Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z", ordinal = 2))
	private boolean familiar$keepUseDownWhileCharging(KeyMapping mapping) {
		return this.familiar$charging || mapping.isDown();
	}

	/** GUI／overlay 期間 handleKeybinds 被跳過，所以在 tick 主動清掉殘留狀態。 */
	@Inject(method = "tick", at = @At("HEAD"))
	private void familiar$clearOnScreen(CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		if (mc.gui.screen() != null || mc.gui.overlay() != null) {
			this.familiar$pending = false;
			this.familiar$charging = false;
		}
	}

	/** 每 tick 決定：釋放蓄力、短按使用、取消，或交給原版挖掘。 */
	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void familiar$onContinueAttack(boolean down, CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		if (this.familiar$charging) {
			// 放開左鍵（或失去焦点）才釋放；威力仍由原版依 remaining ticks 計算。
			if (mc.player == null || mc.gameMode == null || !down) {
				this.familiar$charging = false;
				if (mc.player != null && mc.gameMode != null && mc.player.isUsingItem()) {
					mc.gameMode.releaseUsingItem(mc.player);
				}
			}
			return;
		}

		if (!this.familiar$pending) {
			return;
		}
		if (mc.player == null || mc.gameMode == null) {
			this.familiar$pending = false;
			return;
		}

		if (!mc.options.keyAttack.isDown()) {
			// 短按：主手對方塊做一次場景交互，排出副手。
			this.familiar$pending = false;
			if (familiar$targetsBlock(mc)) {
				familiar$tryUseMainHand(mc);
			}
			return;
		}

		if (!down || !familiar$targetsBlock(mc)) {
			// 開 GUI、滑鼠失去抓取、準星離開方塊：取消這次點擊，不補使用動作。
			this.familiar$pending = false;
			return;
		}

		if (System.currentTimeMillis() - this.familiar$pendingSince >= HOLD_THRESHOLD_MS) {
			// 長按：bypass 讓手動呼叫的 startAttack 不被自己攔下，正式開始挖掘。
			this.familiar$pending = false;
			boolean instant;
			this.familiar$bypass = true;
			try {
				instant = this.startAttack();
			} finally {
				this.familiar$bypass = false;
			}
			if (instant) {
				// 方塊已瞬間破壞，和原版 instantAttack 一樣不要再接著挖下一個。
				ci.cancel();
			}
			return;
		}

		// 還在門檻內：擋掉原版挖掘進度。
		ci.cancel();
	}
}
