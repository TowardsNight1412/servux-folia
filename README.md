# Servux — Folia / Paper 移植版

把 Fabric 服务端模组 **Servux 0.12.2（MC 26.3）** 移植成 **Folia 26.3 / Paper 26.3** 插件，并向下适配到 1.21.8。

- 源码基线：[`sakura-ryoko/servux` 分支 `26.3`](https://github.com/sakura-ryoko/servux/tree/26.3)
- 目标平台：Folia 26.3（`26.3.build.6-alpha`），同样可在 Paper 26.3 运行
- 插件入口：`fi.dy.masa.servux.plugin.ServuxPlugin`
- 产物：`dist/servux-paper-26.3-0.12.2.jar`

如果你在服务器粘贴投影的时候遇到服务器仍然使用指令粘贴，一定要查看投影设置菜单里面有没有打开entityDataSync！！！
111主包大学实习比较忙，有什么问题晚上8点后才能回复，建议加QQ回复较快：3032626316
AAA关注塔菲谢谢喵~终末地危机合约镀层批发喵~
B站账号：风车镇社区官方号
抖音账号：风车镇社区频道
---

## 多版本构建（Folia）

26.3 是基础实现。同一套移植方案已产出以下 MC 版本的独立 jar（各版本协议与 NMS API 不同，不能混用）：

| MC 版本 | 插件文件 | 测试的 Folia 构建 | 状态 |
|---|---|---|---|
| 26.2 | `Servux-Folia26.2.jar` | `folia-26.2-7` | ✅ 握手验证 |
| 26.1.2 | `Servux-Folia26.1.jar` | `folia-26.1.2-8` | ✅ 握手验证 |
| 1.21.11 | `Servux-Folia1.21.11.jar` | `folia-1.21.11-14` | ✅ 握手验证 |
| 1.21.8 | `Servux-Folia1.21.8.jar` | `folia-1.21.8-6` | ✅ 握手验证 |

> `1.21.10` 没有 Folia 构建（PaperMC 只发布了 Paper 版），暂无对应 jar。

每个 jar 的客户端元数据为 `servux-fabric-<MC版本>-0.12.2`，客户端按自身 MC 版本校验。
各版本的 NMS 适配差异见文末「更新记录」。

---

## 1. 功能支持矩阵

| 功能 | 频道 | 状态 | 说明 |
|---|---|---|---|
| 结构边界框（Litematica 结构投影） | `servux:structures` | ✅ 完整 | 含分包（PacketSplitter）大包发送 |
| HUD 元数据（MiniHUD：出生点/种子/天气/TPS） | `servux:hud_metadata` | ✅ 完整 | 元数据、出生点、天气、TPS/MobCap 日志器 |
| 实体 / 方块实体 NBT 查询 | `servux:entity_data` | ✅ 完整 | 支持批量、玩家背包/末影箱权限 |
| Litematica 实体数据（InfoOverlay） | `servux:litematics` | ✅ 完整 | 实体/方块实体 NBT |
| Tweakeroo 数据 | `servux:tweaks` | ⚠️ 部分 | 元数据/握手正常；`stackable_shulkers` 依赖 Mixin，未启用 |
| `/servux` 命令、配置、权限 | `servux:main` | ✅ 完整 | 与 Fabric 版相同的 `config/servux.json` |
| 服务端投影粘贴（Paste） | `servux:litematics` | ✅ Folia 已支持 | 按区块投递到所属区域线程，逐区块推进 |
| 粘贴时不触发方块更新（BUD/侦测器） | — | ✅ 已支持 | `UPDATE_SKIP_ON_PLACE` 等标志位，等价原 Mixin 效果 |
| Easy Place V3（简单放置） | — | ✅ 已支持 | 拦截 `ServerboundUseItemOn` 解码 V3 协议，再于 `BlockPlaceEvent` 修正朝向 |
| 服务端投影保存 / 填充 / 删除 | `servux:litematics` | ⛔ Folia 暂不支持 | 会返回明确提示，不再抛区域线程异常 |
| NBT 查询权限覆盖 | — | ⛔ 不支持 | 需要拦截原版 `ServerboundBlockEntityTagQuery` 包 |
| 铁轨/楼梯/箱子镜像修正、Allay 修复 | — | ⛔ 不支持 | 原实现为 Mixin |

> 结论：**Litematica 的识别、粘贴、Easy Place，以及 MiniHUD 的数据功能均可用**。

---

## 2. 移植架构

原模组 204 个源文件、约 3 万行，其中 95% 是与平台无关的 NMS 逻辑（NBT、结构查找、数据包编解码），因此采用**保留原代码 + 替换平台层**的方式移植。

| Fabric 原实现 | 本插件实现 |
|---|---|
| `ModInitializer` / `DedicatedServer` Mixin | `JavaPlugin#onEnable` + `ServerLoadEvent(STARTUP)` |
| `MinecraftServer` Mixin（tick / reload / start / stop） | `ServuxBootstrap` 全局 tick 任务 + 生命周期回调 |
| `PlayerList` Mixin（join / respawn / quit） | `PlayerJoinEvent` / `PlayerQuitEvent` |
| `Commands` Mixin（注册 `/servux`） | 启动完成后把 Brigadier 命令注入 `Commands#getDispatcher()` |
| `ServerPlayNetworking`（收发自定义载荷） | 收：Bukkit `PluginMessageListener`；发：NMS `ClientboundCustomPayloadPacket`（`DiscardedPayload`） |
| `PayloadTypeRegistry` / `Payload` 记录类 | 直接使用 `IServerPayloadData#toPacket/fromPacket` 编解码 |
| `PacketSplitter` | 原样保留（1 MiB 分片，协议一致） |
| Access Widener（`TagValueInput.input` 等） | `fi.dy.masa.servux.util.Reflect` 反射读取 |
| `Level` Mixin（阻止方块更新） | `Block.UPDATE_SKIP_ON_PLACE / UPDATE_SUPPRESS_DROPS` 标志位 |
| `MixinLevelChunk` 更新抑制 | 同上（26.3 的 `LevelChunk#setBlockState` 原生支持该标志） |
| `MixinBlockItem_EasyPlace` + 网络 Mixin | `EasyPlaceSupport`：Netty 拦截 `ServerboundUseItemOn` + `BlockPlaceEvent` 修正 |
| `MinecraftServer#getTickCount()` | `ServerTicks` 全局单调计数器（Folia 上该方法会抛 `UnsupportedOperationException`） |

### 客户端兼容性（两个必须保持的细节）

1. **元数据标识串**：客户端会校验 `servux` 字段——
   - Litematica: `startsWith("servux-fabric-" + MC版本)`
   - MiniHUD / Tweakeroo: `startsWith("servux-fabric")`

   因此 `Reference.MOD_TYPE` 必须保持 `"fabric"`（即使本插件是 Paper 插件），
   最终发出 `servux-fabric-26.3-0.12.2`。

2. **频道注册包**：Fabric 客户端发送任何自定义载荷前会调用
   `ClientPlayNetworking.canSend()`，该集合只由服务端的 `minecraft:register`
   填充，且 Fabric 解析的是 **NUL 分隔** 的 ASCII 列表；Paper 自动发送的注册包是
   旧的逗号分隔格式，Fabric 解析失败，导致客户端**根本不会发握手包**。
   插件因此在玩家加入时（立即 + 40 tick 后各一次）补发 Fabric 格式的注册包。

### Folia 关键改造

1. **收包**：`PluginMessageListener` 在 Netty 线程触发，统一通过 `FoliaCompat.runForPlayer()` 投递到玩家所属的实体调度器再解码处理。
2. **结构数据**：`StructureDataProvider#tick` 只做玩家遍历，真正的区块读取 `tickPlayer()` 按玩家投递到其区域线程。
3. **粘贴**：`TaskPasteSchematicPerChunkDirect#executeFolia` 每个区块通过 `RegionScheduler.run(world, chunkX, chunkZ, ...)` 投递到所属区域线程，一次处理一个区块；全局 tick 只推进状态。
4. **发送**：`ServerPlayer.connection.send(...)` 线程安全，可在区域线程直接发送。
5. **共享状态**：`registeredPlayers` / `invalidPlayers` 等改为 `ConcurrentHashMap.newKeySet()`。
6. **全局 tick**：`GlobalRegionScheduler#runAtFixedRate(1,1)` 驱动 `TaskScheduler` 与各数据提供器。

> Bukkit 的 `Player#sendPluginMessage` 在 Paper 26.3 上会检查客户端是否通过已废弃的 `minecraft:register` 注册过频道，而 Paper 已不再维护该集合，因此发送改为直接走 NMS 连接，行为与 Fabric 版 `ServerPlayNetworking#send` 一致。

---

## 3. 构建

需要 JDK 21+（推荐 JDK 26，与服务器一致）。构建脚本只依赖服务器自带的 jar，无需 Gradle/Maven：

```powershell
# 1) 首次准备依赖：把 Folia 服务器 libraries/ 下所有 jar
#    以及 versions/26.3/folia-26.3.jar 复制到 libs/（详见 libs/README.md）
# 2) 编译打包（在仓库根目录执行）
powershell -ExecutionPolicy Bypass -File tools/build-plugin.ps1
# 产物：dist/servux-paper-26.3-0.12.2.jar
```

`tools/build-plugin.ps1` 使用 `javac` + `jar` 编译 `src/main/java` 下的全部源码。
`libs/*.jar` 已被 `.gitignore` 忽略，请不要把服务端依赖提交到仓库。

---

## 4. 安装

1. 把 `servux-paper-26.3-0.12.2.jar` 放入服务器 `plugins/`。
2. 重启服务器（`plugin.yml` 已声明 `folia-supported: true`、`api-version: '26.3'`）。
3. 首次启动生成 `<服务器根目录>/config/servux.json`，与 Fabric 版格式完全相同。
4. 客户端需手动开启同步开关（三个模组默认都是关闭的，且协议只在开启后才会握手）：
   - **Litematica** → 通用 / Generic → `entityDataSync`
   - **MiniHUD** → 通用 / Generic → `hudDataSync`（HUD 数据、结构边界框）与 `entityDataSync`（实体 NBT）
   - **Tweakeroo** → 通用 / Generic → `entityDataSync`
5. 若之前连过旧版本并失败，客户端可能已自动把 `entityDataSync` 关掉或标记为无效，重连/重启客户端后重新开启即可。

Easy Place 需要客户端把 `easyPlaceProtocolVersion` 设为 `V3` 或 `AUTO`。

---

## 5. 配置与命令

配置文件：`config/servux.json`（键名与 Fabric 版一致，可直接复制旧配置）。

```
/servux                     显示概览
/servux list [provider]     列出设置
/servux info <setting>      查看单项
/servux set <setting> <值>  修改
/servux reload | save       重载 / 保存配置
/servux search <pattern>    搜索设置
```

权限：使用标准 Bukkit 权限节点（`servux:command.about`、`servux:command.set` 等，兼容 LuckPerms），未配置节点时回退到原版 OP 等级判定。

---

## 6. 测试记录

测试环境：Folia 26.3-6（MC 26.3，Java 26），测试服目录 `D:\Niko-Server2.0\Niko-FoilaServer`。

协议探针（MCProtocolLib 26.3-3，`tools/probe/ServuxBot.java`）实测：

```
REGISTER-PAYLOAD [servux:entity_data|servux:hud_metadata|servux:litematics|servux:structures|servux:tweaks]
RECV servux:structures  type=1  nbt=[name="structure_bounding_boxes", servux="servux-fabric-26.3-0.12.2", version=3, timeout=600]
RECV servux:structures  type=2  splitterComplete=4042/4042
RECV servux:hud_metadata type=1 nbt=[..., spawnPosX/Y/Z, spawnDimension, servux="servux-fabric-26.3-0.12.2"]
RECV servux:entity_data  type=1  元数据 + type=5 方块实体 NBT 响应
```

真实客户端联调（Litematica 26.3）：

```
[servux] ServuxNet: advertised 5 channels to TowardsNight
[servux] decodeServerData(): received Litematic Data Register from player TowardsNight
[servux] handleBulkData: received task: LitematicaPaste from TowardsNight
```

即客户端已完成握手、识别 Servux，并成功发起服务端粘贴请求。

---

## 7. 已知限制

1. **服务端保存 / 填充 / 删除**：仍按区域不安全为由拒绝，粘贴已单独支持。
2. **Mixin 类功能未移植**：堆叠潜影盒、Allay 修复、铁轨/楼梯/箱子镜像修正、NBT 查询覆盖。
3. **天气广播**：原版通过 `ServerLevel#advanceWeatherCycle` Mixin 推送，现改为 HUD tick 内轮询，最多一个 `update_interval` 的延迟。
4. **`nbt_query_override` 配置项保留但无效**。
5. 仅验证过离线模式；在线模式（正版验证）不影响登录后的协议。

---

## 8. 目录结构

```
servux-folia/
├─ src/main/java/fi/dy/masa/servux/
│  ├─ plugin/          插件入口、生命周期、EasyPlaceSupport
│  ├─ network/         传输层（ServuxNet / ServuxContext / PacketSplitter / 各 Handler）
│  ├─ dataproviders/   六个数据提供器
│  ├─ schematic/       Litematica 结构/放置逻辑（保留）
│  ├─ scheduler/       任务调度与 InfoHUD（含 Folia 粘贴分支）
│  ├─ util/            NBT/DataTag/位置工具，FoliaCompat、ServerTicks、Reflect
│  └─ ...              其余原模组代码
├─ src/main/resources/ plugin.yml、语言文件
├─ libs/               编译依赖（仅放本地，不提交）
├─ tools/build-plugin.ps1  构建脚本
├─ tools/probe/        协议测试客户端（MCProtocolLib）
├─ docs/               测试输出
├─ NOTICE.md           使用声明（分发/二次开发必读）
├─ LICENSE.txt         LGPLv3
└─ dist/               构建产物（不提交）
```

---

## 9. 许可与使用声明

上游 Servux 使用 **LGPLv3**，本移植版沿用同一许可，详见 `LICENSE.txt`。
分发、修改或二次发布前，请阅读 [`NOTICE.md`](NOTICE.md)（项目性质、修改说明、免责声明与再分发要求）。

---

## 10. 更新记录

### 2026-11-12

- 新增 Folia 多版本适配：**26.2、26.1.2、1.21.11、1.21.8**，均已实机握手验证（5 个频道 + 元数据）。
- 26.1 系列使用 **26.1.2** 构建：Folia 在该系列只发布了 26.1.2。
- **1.21.10** 因 Folia 无构建暂不提供。
- 各版本主要差异：
  - 26.2：数据包 getter 命名、`Recipe.CODEC`；
  - 26.1.2：`EntityType`（非 `EntityTypes`）、`BlockTags.CONCRETE_POWDER`、`EntitySpawnReason.LOAD`；
  - 1.21.11：`ChunkPos` 字段访问（`.x`/`.z`）、`ChunkPos.asLong`、实体生成 API、关闭 gamerule 探测；
  - 1.21.8：`Identifier → ResourceLocation`、`net.minecraft.Util`、`GameProfile`、旧权限 API（`hasPermissions`）、`Painting` 包名、`startRiding` 签名，并按上游分支省略方块属性序列化。

### 2026-11-10

- 修复服务端粘贴触发侦测器 / BUD：使用 `UPDATE_SKIP_ON_PLACE`、`UPDATE_SUPPRESS_DROPS` 标志位。
- 新增 Easy Place V3 服务端实现：拦截 `ServerboundUseItemOn` 解码 V3 协议，再于 `BlockPlaceEvent` 修正方块朝向。
