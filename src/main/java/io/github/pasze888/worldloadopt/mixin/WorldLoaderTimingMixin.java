package io.github.pasze888.worldloadopt.mixin;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.Commands;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.WorldDataConfiguration;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 探针(dev-only):统计 {@link WorldLoader#load} 各阶段耗时。
 * <p>
 * 目的:分析"选择世界界面点创建世界 → 创建世界界面出现(可选生存/创造)"之间的等待时间构成。
 * 触发链:SelectWorldScreen "创建世界" → CreateWorldScreen.openFresh → WorldLoader.load。
 * <p>
 * 日志前缀 [WLT],跑一次 runClient 后到主菜单 → 单人游戏 → 创建世界 即可看到各阶段毫秒数。
 */
@Mixin(WorldLoader.class)
public abstract class WorldLoaderTimingMixin {
    // 所有辅助成员都标 @Unique,避免与目标类同名成员冲突(WorldLoader 已有 LOGGER)。
    @Unique
    private static final Logger mm$LOGGER = LogUtils.getLogger();
    @Unique
    private static final AtomicInteger LOAD_SEQ = new AtomicInteger();

    @Unique
    private static int mm$currentId;
    @Unique
    private static long mm$loadStart;

    @Unique
    private static long mm$now() {
        return System.nanoTime();
    }

    @Unique
    private static long mm$ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    @Unique
    private static void mm$log(String stage, long startNanos) {
        mm$LOGGER.info("[WLT] #{} {}: {} ms", mm$currentId, stage, mm$ms(startNanos));
    }

    // 注意:load 返回 CompletableFuture,是有返回值的方法,@Inject handler 必须用 CallbackInfoReturnable
    //(即使注入点是 HEAD),否则 mixin 应用时报 'CallbackInfoReturnable is required'。
    @Inject(method = "load", at = @At("HEAD"))
    private static void mm$loadHead(CallbackInfoReturnable<CompletableFuture<?>> cir) {
        mm$currentId = LOAD_SEQ.incrementAndGet();
        mm$loadStart = mm$now();
        mm$LOGGER.info("[WLT] === WorldLoader.load #{} 开始 ===", mm$currentId);
    }

    /**
     * 阶段1:打开所有选中的数据包,建立合并资源索引(纯 I/O + zip 扫描)。
     */
    @Redirect(
        method = "load",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/WorldLoader$PackConfig;createResourceManager()Lcom/mojang/datafixers/util/Pair;"
        )
    )
    private static Pair<WorldDataConfiguration, CloseableResourceManager> mm$timePackConfig(WorldLoader.PackConfig instance) {
        long start = mm$now();
        Pair<WorldDataConfiguration, CloseableResourceManager> result = instance.createResourceManager();
        // 注意:pack 选择(configurePackRepository)是在 createResourceManager 内部完成的,要在它之后读。
        int packCount = instance.packRepository().getSelectedIds().size();
        mm$log("1) 数据包打开+资源索引(启用 " + packCount + " 个数据包)", start);
        return result;
    }

    /**
     * 阶段2:WORLDGEN 层注册表(生物群系/密度函数/噪声设置/结构/维度类型等 worldgen/* JSON)。
     * 直接 redirect loadLayer 里的 RegistryDataLoader.load(WORLDGEN 层),
     * loadLayer 是 private 且不额外调用,避免私有方法访问问题。
     */
    @Redirect(
        method = "loadLayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/resources/RegistryDataLoader;load(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/core/RegistryAccess;Ljava/util/List;)Lnet/minecraft/core/RegistryAccess$Frozen;"
        )
    )
    private static RegistryAccess.Frozen mm$timeWorldgen(
        ResourceManager resourceManager, RegistryAccess registryAccess, List<RegistryDataLoader.RegistryData<?>> registryData
    ) {
        long start = mm$now();
        RegistryAccess.Frozen result = RegistryDataLoader.load(resourceManager, registryAccess, registryData);
        mm$log("2) WORLDGEN 注册表加载", start);
        return result;
    }

    /**
     * 阶段3:DIMENSIONS 层注册表(维度类型 + level_stem 世界预设)。
     */
    @Redirect(
        method = "load",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/resources/RegistryDataLoader;load(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/core/RegistryAccess;Ljava/util/List;)Lnet/minecraft/core/RegistryAccess$Frozen;"
        )
    )
    private static RegistryAccess.Frozen mm$timeDimensions(
        ResourceManager resourceManager, RegistryAccess registryAccess, List<RegistryDataLoader.RegistryData<?>> registryData
    ) {
        long start = mm$now();
        RegistryAccess.Frozen result = RegistryDataLoader.load(resourceManager, registryAccess, registryData);
        mm$log("3) DIMENSIONS 注册表加载", start);
        return result;
    }

    /**
     * 阶段4:生成默认 WorldGenSettings(随机种子 + 普通世界三纬度),很快。
     * 注意:接口方法 get 的运行时描述符返回类型是 DataLoadOutput(不是擦除后的 Object)。
     */
    @Redirect(
        method = "load",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/WorldLoader$WorldDataSupplier;get(Lnet/minecraft/server/WorldLoader$DataLoadContext;)Lnet/minecraft/server/WorldLoader$DataLoadOutput;"
        )
    )
    private static WorldLoader.DataLoadOutput<?> mm$timeWorldGenSettings(WorldLoader.WorldDataSupplier<?> supplier, WorldLoader.DataLoadContext context) {
        long start = mm$now();
        WorldLoader.DataLoadOutput<?> result = supplier.get(context);
        mm$log("4) 默认 WorldGenSettings 烘焙", start);
        return result;
    }

    /**
     * 阶段5(异步,后台线程完成):ReloadableServerResources —— 标签/配方/战利品表/进度/函数库,
     * 加上 NeoForge 与各 mod 通过 AddReloadListenerEvent 挂的 listener。通常是大头。
     */
    @Redirect(
        method = "load",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/ReloadableServerResources;loadResources(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/core/LayeredRegistryAccess;Lnet/minecraft/world/flag/FeatureFlagSet;Lnet/minecraft/commands/Commands$CommandSelection;ILjava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private static CompletableFuture<ReloadableServerResources> mm$timeResources(
        ResourceManager resourceManager,
        LayeredRegistryAccess<RegistryLayer> registries,
        FeatureFlagSet enabledFeatures,
        Commands.CommandSelection commandSelection,
        int functionCompilationLevel,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        long start = mm$now();
        int id = mm$currentId;
        CompletableFuture<ReloadableServerResources> future = ReloadableServerResources.loadResources(
            resourceManager, registries, enabledFeatures, commandSelection, functionCompilationLevel, backgroundExecutor, gameExecutor
        );
        future.whenComplete((res, ex) -> {
            if (ex != null) {
                mm$LOGGER.info("[WLT] #{} 5) ReloadableServerResources(标签/配方/战利品/进度/函数+mod listener): {} ms (失败: {})",
                    id, mm$ms(start), ex.toString());
            } else {
                mm$LOGGER.info("[WLT] #{} 5) ReloadableServerResources(标签/配方/战利品/进度/函数+mod listener): {} ms", id, mm$ms(start));
            }
        });
        return future;
    }

    /**
     * 总耗时:包含阶段5异步完成 + updateRegistryTags + WorldCreationContext 包装。
     * handler 参数必须是目标方法参数的“前缀”,返回值用 cir.getReturnValue() 取。
     */
    @Inject(method = "load", at = @At("RETURN"))
    private static void mm$loadReturn(CallbackInfoReturnable<CompletableFuture<?>> cir) {
        long start = mm$loadStart;
        int id = mm$currentId;
        CompletableFuture<?> returnValue = cir.getReturnValue();
        returnValue.whenComplete((res, ex) -> {
            if (ex != null) {
                mm$LOGGER.info("[WLT] #{} === 总耗时(含异步资源+tags+WorldCreationContext 包装): {} ms (失败: {}) ===",
                    id, mm$ms(start), ex.toString());
            } else {
                mm$LOGGER.info("[WLT] #{} === 总耗时(含异步资源+tags+WorldCreationContext 包装): {} ms ===", id, mm$ms(start));
            }
        });
    }
}
