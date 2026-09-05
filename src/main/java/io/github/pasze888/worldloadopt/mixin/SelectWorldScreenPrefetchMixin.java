package io.github.pasze888.worldloadopt.mixin;

import io.github.pasze888.worldloadopt.Config;
import io.github.pasze888.worldloadopt.WorldCreationPrefetcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 预取触发:进入选世界列表(点"单人游戏")时,后台开始加载世界创建数据,
 * 这样玩家浏览世界列表的几秒足够把 ~2.3s 的加载跑完。
 * 受客户端配置 Config.PREFETCH_WORLD_CREATION 控制(用于 A/B 对比)。
 */
@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenPrefetchMixin {
    @Inject(method = "init", at = @At("HEAD"))
    private void mm$prefetchOnOpen(CallbackInfo ci) {
        if (Config.PREFETCH_WORLD_CREATION.getAsBoolean()) {
            WorldCreationPrefetcher.start(Minecraft.getInstance());
        }
    }
}
