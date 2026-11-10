# 使用声明 / NOTICE

> 适用于本仓库（Servux 的 Folia/Paper 移植版）及其构建产物。
> 发布或分发前请保留本文件与 `LICENSE.txt`。

## 1. 项目性质

- 本项目是 Minecraft 服务端模组 **[Servux](https://github.com/maruohon/servux)** 的**非官方** Paper/Folia 移植版。
- 移植基于 **[sakura-ryoko/servux](https://github.com/sakura-ryoko/servux) 的 `26.3` 分支**源码。
- 本项目与上游作者 **masa**、**Sakura-Ryoko**，以及 Mojang / Microsoft **没有任何隶属或背书关系**。
- 本项目不是官方发布；上游作者不对本移植版提供支持。

## 2. 版权与许可证

- 上游 Servux 使用 **GNU Lesser General Public License v3.0（LGPLv3）**。
- 作为衍生作品，本移植版同样以 **LGPLv3** 发布，完整许可证文本见 [`LICENSE.txt`](LICENSE.txt)。
- 在遵守 LGPLv3 的前提下，你可以自由使用、修改和再分发本项目，但必须：
  1. 保留原作者的版权声明与许可证文本；
  2. 明确标注你对代码所做的修改；
  3. 以 LGPLv3 分发衍生作品，并提供对应的完整源代码。
- 本项目**不包含** Minecraft 的客户端/服务端代码或资源；构建时需要自行准备 Folia 26.3 服务端 jar 及其依赖（见 `libs/README.md`）。Minecraft 相关权利归 Mojang / Microsoft 所有。

## 3. 本移植版的主要修改

为便于区分上游代码，主要改动如下（详见 `README.md`）：

- 将 Fabric 平台层替换为 Bukkit/Paper 插件层（生命周期、事件、命令注册）；
- 网络层由 `ServerPlayNetworking` 改为 Bukkit 插件消息 + NMS 自定义载荷发送；
- 补发 Fabric 格式的 `minecraft:register` 频道注册包；
- 元数据标识串保持 `servux-fabric-26.3-0.12.2` 以兼容客户端校验；
- Folia 区域化调度改造（按玩家/区块投递到 `EntityScheduler` / `RegionScheduler`）；
- 服务端粘贴按区块调度，并使用 `UPDATE_SKIP_ON_PLACE` 等标志抑制方块更新；
- 新增 Easy Place V3 的服务端实现（数据包拦截 + 放置后状态修正）；
- 移除全部 Mixin 与 Access Widener，改用反射/原生标志位/事件替代。

## 4. 使用风险与免责

- 本项目按 **“现状”** 提供，**不提供任何明示或暗示的担保**，包括但不限于适销性、特定用途适用性。
- 使用前请**备份世界存档**。因使用本项目导致的存档损坏、数据丢失、服务器崩溃、性能问题或其他损失，由使用者自行承担。
- 本项目仅用于学习、研究和自己管理的服务器；请遵守所在服务器、社区及 Minecraft EULA 的相关规定。
- 请勿将本项目用于破坏他人服务器、绕过保护机制、作弊或其他恶意用途。

## 5. 二次分发

- 在 GitHub 或其他平台发布本项目的修改版时，请：
  1. 保留本 `NOTICE.md` 与 `LICENSE.txt`；
  2. 在 README 中注明来源仓库与上游链接；
  3. 说明你做出的修改；
  4. 以 LGPLv3 提供源代码。
- 请不要使用上游作者的名义宣传本移植版，也不要暗示获得官方认可。

## 6. 第三方组件

- 测试工具 `tools/probe/` 使用 [MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib)（MIT 许可证，© GeyserMC），仅用于协议联调，不参与插件运行。
- 构建依赖（Folia 服务端 jar 及其 `libraries/`）版权归各自作者所有，**不随本仓库分发**。

## 7. 链接

- 上游 Servux：<https://github.com/maruohon/servux>
- 本移植版所基于的分支：<https://github.com/sakura-ryoko/servux/tree/26.3>
- 问题反馈：请在本仓库提交 Issue（并附上服务端版本、日志与复现步骤）。

---

## English summary

This is an **unofficial** Paper/Folia port of [Servux](https://github.com/maruohon/servux), based on the
[`26.3` branch](https://github.com/sakura-ryoko/servux/tree/26.3) by Sakura-Ryoko.

It is licensed under **LGPLv3** (see `LICENSE.txt`). You may use, modify and redistribute it
provided that you keep the copyright notices and license, mark your changes, and distribute
derivatives under the same license with source code.

The software is provided **as is**, without warranty of any kind. Back up your worlds before use.
Not affiliated with or endorsed by masa, Sakura-Ryoko, Mojang or Microsoft. Minecraft is a
trademark of Mojang/Microsoft.
