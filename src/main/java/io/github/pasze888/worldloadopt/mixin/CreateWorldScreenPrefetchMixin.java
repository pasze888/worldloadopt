package io.github.pasze888.worldloadopt.mixin;

import io.github.pasze888.worldloadopt.WorldCreationPrefetcher;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.server.WorldLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 预取接入:把 CreateWorldScreen.openFresh 里真正的 WorldLoader.load 调用,
 * 替换为返回预取的 future(若可用)。其余流程(managedBlock + setScreen)原样走。
 * 无预取时原样调用 load,行为不变。
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenPrefetchMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(
        method = "openFresh",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/WorldLoader;load(Lnet/minecraft/server/WorldLoader$InitConfig;Lnet/minecraft/server/WorldLoader$WorldDataSupplier;Lnet/minecraft/server/WorldLoader$ResultFactory;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private static CompletableFuture mm$usePrefetched(
        WorldLoader.InitConfig initConfig,
        WorldLoader.WorldDataSupplier worldDataSupplier,
        WorldLoader.ResultFactory resultFactory,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        CompletableFuture prefetched = WorldCreationPrefetcher.take();
        if (prefetched != null) {
            // 预取命中:跳过真实加载,直接返回后台已完成的 future
            return prefetched;
        }
        return WorldLoader.load(initConfig, worldDataSupplier, resultFactory, backgroundExecutor, gameExecutor);
    }
}
