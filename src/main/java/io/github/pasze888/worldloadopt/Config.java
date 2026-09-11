package io.github.pasze888.worldloadopt;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    // ===== 客户端专用配置(纯客户端功能,如世界创建预取)=====
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    /**
     * 世界创建数据预取开关。开启:进入选世界列表时后台预载 WorldLoader.load,
     * 点"创建世界"时界面秒开(感知等待≈0);关闭:回退到原版行为(点创建时才加载)。
     * 可用于 A/B 对比效果。改后需重启游戏(或游戏内 Mod 配置界面修改,下次进入选世界列表生效)。
     */
    public static final ModConfigSpec.BooleanValue PREFETCH_WORLD_CREATION = CLIENT_BUILDER
            .comment(
                "Prefetch world creation data in the background when opening the singleplayer world list,\n"
                    + "so the Create World screen appears instantly. Disable to compare against vanilla loading behavior."
            )
            .define("worldCreationPrefetch", true);

    static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
}
