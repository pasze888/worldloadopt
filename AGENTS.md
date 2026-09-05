# World Load Opt 项目知识手册

本文件每次会话开始时自动加载,是本项目给大模型的工作规范。
**目标:让大模型永远基于真实 API 源码写代码,而不是凭记忆猜 API。**

---

## 1. 项目版本矩阵(改版本时,gradle.properties 与本表必须同步更新)

| 项 | 值 |
|---|---|
| Minecraft | **1.21.1** |
| NeoForge | **21.1.244** |
| loader 版本范围 | `[1,)`(mods.toml 中) |
| Java | **21**(Gradle 工具链,由 foojay 插件自动下载到 `~/.gradle/jdks/`) |
| Gradle | 9.2.1(wrapper) |
| ModDevGradle 插件 | 2.0.143 |
| Parchment(mappings) | minecraft 1.21.1 / mappings 2024.11.17 |
| mod_id / 包名 | `worldloadopt` / `io.github.pasze888.worldloadopt` |

## 2. 铁律:任何 API 调用前必须先查源码

1. **写任何 Minecraft / NeoForge API 调用之前,先查本地反编译源码**(路径见第 3 节),确认方法签名后照抄,禁止凭记忆编造。
2. MC 1.21.1 与旧版本 API 差异巨大(注册、组件、事件、渲染),**不要用旧版本知识直接套用**。
3. 检索源码用全文搜索(类名/方法名),找到后核对参数与返回类型。
4. 文档兜底:https://docs.neoforged.net/ 、 https://maven.neoforged.net/ (javadoc)。

## 3. API 权威来源:反编译源码位置

**首选(工作区共享,路径稳定):`../api-sources/`**
- 内容:Minecraft 1.21.1 + NeoForge 21.1.x 的**反编译源码**(带 Parchment 参数名),共 6000+ 个 `.java` 文件。
- 检索示例(在项目根目录执行):
  - 直接读文件:`../api-sources/net/minecraft/world/item/Item.java`
  - 按类名搜索:`grep -rn "class DeferredRegister" ../api-sources/net/neoforged/`
- 该目录由 `./gradlew build` 缓存产物解压而来,**可随时删除重建**(见下方"重建命令")。

**备选(Gradle 缓存原产物,不依赖项目目录)**
- 完整源码+类 jar:`%USERPROFILE%\.gradle\caches\neoformruntime\intermediate_results\sourcesAndCompiledWithNeoForge_*_output.jar`(取最新一个)
- 纯 Minecraft 反编译:`...\decompile_*_output.jar`
- NeoForge 自身源码 jar:`%USERPROFILE%\.gradle\caches\modules-2\files-2.1\net.neoforged\neoforge\21.1.244\**\neoforge-21.1.244-sources.jar`(仅 NeoForge 补丁部分,不完整)
- 查看 jar 内容:`unzip -l <jar> | grep 类名`;读文件:`unzip -p <jar> <内部路径>`

**重建命令(在工作区根执行,见工作区 AGENTS.md §3)**
```bash
# 在项目根先 build,再解压到工作区共享目录:
./gradlew build
JAR=$(ls -t ~/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_*_output.jar | head -1)
unzip -o -q "$JAR" -d ../api-sources
```

**修改版本后**:更新 gradle.properties → 重新 `./gradlew build` → 按上面命令重建 `api-sources/`。

## 4. 常用构建命令(项目根目录执行)

| 命令 | 用途 |
|---|---|
| `./gradlew build` | 编译 + 打包 mod jar(也是源码下载/刷新的触发命令) |
| `./gradlew runClient` | 启动开发版游戏客户端(首次会下载运行环境,较慢) |
| `./gradlew runServer` | 启动开发版服务器 |
| `./gradlew runData` | 数据生成器(输出到 `src/generated/resources`) |
| `./gradlew runGameTestServer` | 运行 GameTest 后退出 |
| `./gradlew --stop` | 停止 Gradle daemon |

> ⚠️ 跨版本升级前先做工件就绪检查(见 mc-mod-dev 技能"版本升级流程"),不通就报告网络阻塞,不要空等。
> 网络状态(2026-08):`maven.neoforged.net` 曾不可达,2026-08-12 起已恢复(Clash 需开 TUN 模式)。

## 5. 本项目编码约定(1.21.1 已验证的模式)

- **注册**:`DeferredRegister.createBlocks/Items(MODID)` 与 `DeferredRegister.create(Registries.X, MODID)`;`register(...)` 后必须在主类构造函数里 `xxx.register(modEventBus)`。
- **事件总线**:Mod 生命周期事件挂 `modEventBus`(构造函数参数 `IEventBus`);游戏内事件挂 `NeoForge.EVENT_BUS`(`@SubscribeEvent` 或 `addListener`)。
- **客户端类**:`@Mod(value = WorldLoadOpt.MODID, dist = Dist.CLIENT)` + `@EventBusSubscriber(modid = WorldLoadOpt.MODID, value = Dist.CLIENT)`。
- **语言文件**:`src/main/resources/assets/worldloadopt/lang/en_us.json`(键格式 `item.worldloadopt.xxx` / `block.worldloadopt.xxx` / `itemGroup.worldloadopt`)。
- **mod 元数据**:`src/main/templates/META-INF/neoforge.mods.toml`,其中的 `${...}` 占位符由 Gradle 的 `generateModMetadata` 任务展开,直接编辑 gradle.properties 即可,不要手改生成产物。
- **数据生成**:输出目录 `src/generated/resources`,已加入资源源集;新增标签/模型/配方优先走 runData,不手写。

## 6. 关键文件地图

| 文件 | 说明 |
|---|---|
| `gradle.properties` | 版本矩阵 + mod 元数据(改版本/名字只改这里) |
| `build.gradle` | ModDevGradle 配置(runs、parchment、元数据展开) |
| `src/main/java/io/github/pasze888/worldloadopt/WorldLoadOpt.java` | 主类 `@Mod("worldloadopt")`:注册表、事件、入口 |
| `src/main/java/io/github/pasze888/worldloadopt/WorldLoadOptClient.java` | 客户端专属入口(配置界面等) |
| `src/main/java/io/github/pasze888/worldloadopt/Config.java` | NeoForge `ModConfigSpec` 配置 |
| `src/main/templates/META-INF/neoforge.mods.toml` | mod 元数据模板 |
| `../AGENTS.md` | 工作区根级规范(共享 api-sources、构建命令、跨项目规则) |

## 7. 跨会话知识积累(重要)

- 每轮会话结束时,把本会话**验证过**(编译通过/源码确认)的 API 签名、踩过的坑、版本差异,
  **追加**到 `docs/KNOWLEDGE.md`(被清理后首次需要时重新创建;同时可参考工作区共享 `../api-sources/` 查证)。
- 后续会话写码前先读 `docs/KNOWLEDGE.md`,可直接复用已验证的写法,减少重复查证。
