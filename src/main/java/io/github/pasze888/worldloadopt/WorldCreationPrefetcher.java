package io.github.pasze888.worldloadopt;

import com.mojang.logging.LogUtils;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.commands.Commands;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.Util;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.resource.ResourcePackLoader;
import org.slf4j.Logger;

/**
 * 世界创建数据预取(加速"点创建世界"到"出现创建界面"的等待)。
 * <p>
 * 思路:正常流程在 {@code CreateWorldScreen.openFresh} 里才同步加载数据包+注册表+服务端资源
 * (实测 6 mod 环境约 2.3s)。本类在玩家进入选世界列表时就把它在后台跑完并缓存 future,
 * 等点"创建世界"时 {@code CreateWorldScreen.openFresh} 直接 join 已完成的 future → 感知等待≈0。
 * <p>
 * 与 openFresh 的加载完全等价:同样默认数据包 + DEFAULT 配置 + 普通世界预设 + 随机种子。
 */
@OnlyIn(Dist.CLIENT)
public final class WorldCreationPrefetcher {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 后台加载中的 future(只消费一次)。 */
    @Nullable
    private static CompletableFuture<WorldCreationContext> future;
    private static boolean started;

    private WorldCreationPrefetcher() {
    }

    /**
     * 在后台启动与 CreateWorldScreen.openFresh 等价的 WorldLoader.load。
     * 幂等:本会话已启动过则跳过。渲染线程调用,本身不阻塞。
     */
    public static void start(Minecraft minecraft) {
        if (started) {
            return;
        }
        started = true;
        try {
            PackRepository packRepository = new PackRepository(new ServerPacksSource(minecraft.directoryValidator()));
            ResourcePackLoader.populatePackRepository(packRepository, PackType.SERVER_DATA, false);
            WorldLoader.InitConfig initConfig = new WorldLoader.InitConfig(
                new WorldLoader.PackConfig(packRepository, WorldDataConfiguration.DEFAULT, false, true),
                Commands.CommandSelection.INTEGRATED,
                2
            );
            // WorldLoader.load 前 4 个阶段(数据包打开/WORLDGEN/DIMENSIONS/烘焙)是同步执行的,
            // 包一层 supplyAsync 放到后台线程,避免卡渲染线程;后续异步阶段走 loadResources 自己的 executor。
            CompletableFuture<WorldCreationContext> composed = CompletableFuture
                .supplyAsync(
                    () -> WorldLoader.load(initConfig, WorldCreationPrefetcher::mm$load, WorldCreationPrefetcher::mm$finish, Util.backgroundExecutor(), minecraft),
                    Util.backgroundExecutor()
                )
                .thenCompose(f -> f);
            future = composed;
            LOGGER.info("[WLT] 预取启动:WorldLoader.load 在后台加载世界创建数据");
        } catch (Exception e) {
            LOGGER.warn("[WLT] 预取启动失败,回退到原始加载", e);
            started = false;
            future = null;
        }
    }

    private static WorldLoader.DataLoadOutput<PrefetchCookie> mm$load(WorldLoader.DataLoadContext ctx) {
        return new WorldLoader.DataLoadOutput<>(
            new PrefetchCookie(
                new WorldGenSettings(WorldOptions.defaultWithRandomSeed(), WorldPresets.createNormalWorldDimensions(ctx.datapackWorldgen())),
                ctx.dataConfiguration()
            ),
            ctx.datapackDimensions()
        );
    }

    private static WorldCreationContext mm$finish(
        CloseableResourceManager manager, ReloadableServerResources resources, LayeredRegistryAccess<RegistryLayer> registryAccess, PrefetchCookie cookie
    ) {
        manager.close();
        return new WorldCreationContext(cookie.worldGenSettings(), registryAccess, resources, cookie.dataConfiguration());
    }

    /**
     * 取出预取的 future(只取一次并复位)。未启动/失败/已消费返回 null,调用方回退到原始加载。
     */
    @Nullable
    public static CompletableFuture<WorldCreationContext> take() {
        if (!started) {
            return null;
        }
        CompletableFuture<WorldCreationContext> f = future;
        started = false;
        future = null;
        if (f == null || f.isCompletedExceptionally()) {
            LOGGER.warn("[WLT] 预取不可用(失败),回退到原始加载");
            return null;
        }
        return f;
    }

    /** 与 CreateWorldScreen.DataPackReloadCookie 等价的自定义 cookie(那个是包私有的,用不了)。 */
    record PrefetchCookie(WorldGenSettings worldGenSettings, WorldDataConfiguration dataConfiguration) {
    }
}
