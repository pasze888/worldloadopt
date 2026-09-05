package io.github.pasze888.worldloadopt.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 探针(dev-only):标记"点击创建世界 → 开始加载"的瞬间,
 * 方便把日志里 WorldLoaderTimingMixin 的 #N 与"创建世界界面打开"对上号。
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenProbeMixin {
    @Inject(method = "openFresh", at = @At("HEAD"))
    private static void mm$markOpenFresh(CallbackInfo ci) {
        LogUtils.getLogger().info("[WLT] >>> 点击'创建世界',开始 WorldLoader.load(下一条 #N 日志即本次测量)");
    }
}
