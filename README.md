# PaperQQBot

> Paper 服务器 × QQ 机器人插件：将服务器事件实时推送到 QQ 群，支持游戏内 ↔ QQ 群消息互通、QQ 绑定、群签到、远程管理指令与大模型对话。
>
> 支持 **NapCat（OneBot 11）** 与 **QQ 官方机器人** 两种接入模式。

---

## 功能特性

- 🚀 **服务器事件推送**：启动 / 关闭 / 玩家进出 / 空服 / 聊天 / 死亡 / TPS 警告，每个 QQ 群可独立配置要接收的事件
- 🔗 **QQ 绑定**：群内 @机器人 获取验证码 → 游戏内 `/bindqq 验证码` 完成绑定；支持强制绑定（未绑定不能移动、到期踢出）
- ✅ **QQ 群签到**：群内 @机器人 `/签到`，通过 Vault 向服务器经济插件发放金币奖励
- 💬 **消息互通**：QQ 群消息 ↔ 游戏内聊天双向转发
- 🤖 **@ 回复**：群成员 @机器人 即可查询服务器在线人数、TPS 等状态
- 🛠 **管理员远程指令**：管理员在群内 @机器人 + 指令（如 `/gamemode creative`）即可在服务器内执行
- 🧠 **大模型对话**：兼容 OpenAI Chat Completions 协议（OpenAI / DeepSeek / 通义千问 / Ollama 等），支持多轮上下文记忆
- 🌐 **WebUI 控制台**：浏览器在线查看与修改配置，无需停机编辑文件
- 📝 **模板占位符**：推送消息支持丰富的服务器信息占位符，可自定义模板

---

## 环境要求

| 依赖 | 要求 | 说明 |
| --- | --- | --- |
| 服务端 | Paper 26.2 及以上（api-version 1.20+） | 其他 Paper 系服务端也可尝试 |
| Java | Java 25 | 插件以 Java 25 编译目标构建 |
| Vault（可选） | 任意 Vault 支持的经済插件 | 签到金币发放依赖；未安装时金币相关功能不可用 |
| NapCat（NapCat 模式） | NapCat 客户端（OneBot 11） | 需开启 HTTP 服务与 WebSocket 服务 |
| QQ 开放平台（官方模式） | 已创建的 QQ 机器人 | 需 AppID 与 AppSecret |

---

## 快速开始

1. 前往 [Releases](https://github.com/7891mc/PaperQQBot/releases) 下载 `PaperQQBot-1.0.0.jar`
2. 将 JAR 放入服务端 `plugins/` 目录
3. 重启服务器（或 `reload confirm`）
4. 编辑 `plugins/PaperQQBot/config.yml`，或浏览器打开 WebUI：`http://127.0.0.1:8765`
5. 配置完成后执行 `/paperqqbot reload`，并用 `/paperqqbot test` 验证推送

> 首次启动会在 `plugins/PaperQQBot/` 下生成 `config.yml`，修改配置后执行 `/paperqqbot reload` 生效。

---

## 机器人模式配置

### 模式一：NapCat（推荐，使用普通 QQ 号）

1. 部署并登录 [NapCat](https://napcat.apifox.cn/)（或 OneBot 11 兼容客户端）
2. 在 NapCat 配置中开启 **HTTP 服务**（用于发送消息），记录端口（如 `3000`）
3. 在 NapCat 配置中开启 **Websocket 服务器**（用于接收群消息），记录端口（如 `3001`，通常与 HTTP 端口不同）
4. 修改 `config.yml`：

```yaml
mode: "napcat"              # 使用 NapCat 模式

bot:
  http-url: "http://127.0.0.1:3000"   # NapCat HTTP 上报地址
  access-token: ""                    # NapCat access_token（未设置则留空）

ws:
  enabled: true                       # 接收群消息，必须开启
  url: "ws://127.0.0.1:3001"          # NapCat Websocket 地址
  access-token: ""                    # 留空则复用 bot.access-token
```

### 模式二：QQ 官方机器人

1. 在 [QQ 开放平台](https://q.qq.com/) 创建机器人
2. 将机器人加入目标群，并在群设置中允许机器人接收**群内全部消息**（否则收不到非 @ 的群消息）
3. 修改 `config.yml`：

```yaml
mode: "official"           # 使用 QQ 官方机器人模式

official-bot:
  app-id: "你的AppID"       # 机器人 ID
  secret: "你的AppSecret"   # 机器人密钥
```

---

## 推送事件配置

| 事件 | 触发时机 | 默认开关 |
| --- | --- | --- |
| `start` | 服务器启动 | ✅ |
| `stop` | 服务器关闭 | ✅ |
| `join` | 玩家加入 | ❌ |
| `quit` | 玩家退出 | ❌ |
| `empty` | 服务器空服 | ✅ |
| `chat` | 玩家聊天 | ❌ |
| `death` | 玩家死亡 | ❌ |
| `tps-warning` | TPS 低于阈值 | ✅ |

每个群在 `groups` 中独立配置：

```yaml
groups:
  - group-id: 1000000001
    name: "管理员群"
    enabled: true
    events: ["*"]                    # 接收所有事件
  - group-id: 1000000002
    name: "玩家公告群"
    enabled: true
    events: ["start", "stop", "empty"]  # 只接收指定事件
```

- `events: ["*"]`：接收全部事件
- `events: []`：该群不接收任何推送
- 不写 `events`：继承 `event-toggles` 的全局默认开关

TPS 警告阈值与间隔见 `triggers`：

```yaml
triggers:
  tps-warning-threshold: 15.0       # TPS 低于该值推送警告
  tps-warning-cooldown-seconds: 300 # 同一群推送最小间隔（秒）
```

---

## QQ 绑定与签到

### 绑定流程

```
QQ 群内 @机器人 /绑定        → 机器人私发/群内给出 6 位验证码
游戏内 /bindqq 验证码        → 绑定成功
```

- 验证码有效期：`binding.code-expire-minutes`（默认 10 分钟）
- 支持换绑：`binding.allow-rebind: true`
- 强制绑定：`binding.require-bind: true` 时，未绑定玩家进服自动发码，**不能移动**，超时未绑定被踢出；断线重连在有效期内沿用原码

### 群签到

```yaml
signin:
  enabled: true
  command: "/签到"          # 群内 @机器人 签到指令
  reward: 100               # 每次签到奖励金币（经 Vault 发放）
```

### 管理员远程指令

`admins` 中配置的 QQ 号，可在群内 @机器人 后直接执行游戏指令：

```
@机器人 /gamemode creative
@机器人 /list
```

---

## 消息互通

```yaml
chat-relay:
  group-to-server: true                    # QQ 群消息转发到游戏内
  group-format: "§7[QQ群] §f%name%: %message%"
```

- NapCat 模式可收到群内全量消息
- 官方机器人模式需群管理员开启"允许机器人接收群内全部消息"

---

## 大模型对话

启用后，群内 @机器人（非管理员指令、非预设指令）会调用大模型生成回复：

```yaml
llm:
  enabled: true
  api-url: "https://api.deepseek.com/v1/chat/completions"  # OpenAI 兼容接口
  api-key: "sk-xxxx"
  model: "deepseek-chat"
  system-prompt: "你是一个 Minecraft 服务器助手，用简洁友好的中文回答玩家关于服务器的问题。"
  timeout-seconds: 30
  temperature: 0.7
  context-turns: 5          # 记住最近 5 轮对话，0 = 关闭记忆
```

---

## @ 回复（查询服务器状态）

```yaml
at-reply:
  enabled: true             # 需同时开启 ws.enabled
  self-id: 0                # 机器人 QQ 号，填 0 则自动学习
  cooldown-ms: 3000         # 同一群最小回复间隔
  query-command: "/查询"     # @机器人 + 该指令 返回服务器状态
```

---

## 游戏内命令

| 命令 | 说明 |
| --- | --- |
| `/paperqqbot help` | 查看帮助 |
| `/paperqqbot reload` | 重载配置 |
| `/paperqqbot status` | 查看运行状态（模式、连接等） |
| `/paperqqbot test` | 测试向群推送 |
| `/paperqqbot bindlist` | 查看绑定列表 |
| `/paperqqbot unbind <玩家>` | 解绑指定玩家 |
| `/paperqqbot blacklist <玩家>` | 拉黑玩家（禁止绑定） |
| `/paperqqbot coins give/take/set <玩家> <数量>` | 金币操作 |
| `/paperqqbot coins look <玩家>` | 查询玩家金币 |
| `/paperqqbot add/remove/del/list <群号>` | 群管理 |
| `/bindqq <验证码>` | 游戏内绑定 QQ（仅本人可见验证码请求） |
| `/qqunbind` | 解除 QQ 绑定 |
| `/coins` | 查询金币余额 |

> 群内 @机器人 `/绑定`、`/签到`、`/查询` 为 QQ 侧指令；游戏内 `/bindqq`、`/qqunbind`、`/coins` 为服务器指令。

---

## 权限节点

| 权限 | 默认 | 说明 |
| --- | --- | --- |
| `paperqqbot.admin` | op | 管理 PaperQQBot（/paperqqbot 全部子命令） |
| `paperqqbot.use` | 所有人 | 使用绑定、签到等查询功能 |

---

## WebUI 控制台

默认地址：`http://127.0.0.1:8765`

```yaml
web:
  enabled: true
  host: "127.0.0.1"     # 127.0.0.1 仅本机访问；0.0.0.0 对外（务必设置 token）
  port: 8765
  access-token: ""      # 对外务必设置长随机字符串
```

- WebUI 首次打开需输入官网注册后获得的**专属绑定码**（`account.api-base` 对应官网），绑定后仅该账号可登录
- 支持在线查看与修改全部配置，修改后保存即可生效

---

## 消息模板占位符

推送模板（`templates.*`）与 @回复模板（`at-reply.template`）支持以下占位符：

| 占位符 | 说明 |
| --- | --- |
| `%server_name%` | 服务器名 |
| `%server_address%` | 服务器地址 |
| `%server_contact%` | 对外 QQ 群号 |
| `%server_extra%` | 服务器备注 |
| `%server_website%` | 服务器官网 |
| `%server_version%` | Minecraft 版本 |
| `%server_motd%` | 服务器 MOTD（去颜色码） |
| `%server_ip%` | 服务器 IP（自动获取，可能不准） |
| `%server_port%` | 服务器端口 |
| `%online_players%` / `%max_players%` | 当前/最大在线人数 |
| `%player_list%` | 在线玩家列表 |
| `%tps_1m%` / `%tps_5m%` / `%tps_15m%` | TPS 均值 |
| `%memory_used_mb%` / `%memory_max_mb%` | 已用/最大内存 |
| `%uptime_seconds%` / `%uptime_formatted%` | 运行时间 |
| `%datetime%` / `%date%` / `%time%` | 推送时间 |
| `%player_name%` / `%player_display%` | 触发玩家名（join/quit/chat/death） |
| `%death_message%` | 死亡消息（death） |
| `%chat_message%` | 聊天内容（chat） |
| `%sender_id%` | 触发者 QQ 号（at-reply） |

---

## 从源码构建

```bash
mvn clean package
```

构建产物：`target/PaperQQBot-1.0.0.jar`

依赖：Paper API、VaultAPI、log4j-core（均为 provided，运行时由服务端提供）。

---

## 常见问题（FAQ）

**Q：群里收不到推送？**
A：依次检查：① `mode` 与机器人实际模式一致；② `groups` 中群号正确且 `enabled: true`；③ 该群 `events` 覆盖了对应事件；④ NapCat HTTP 地址端口是否正确、是否可连通；⑤ 执行 `/paperqqbot test` 手动测试。

**Q：@机器人 没有回复？**
A：确认 `at-reply.enabled` 与 `ws.enabled` 均为 `true`，且 `ws.url` 指向 NapCat 的 **WebSocket 服务**端口（通常与 HTTP 端口不同）。

**Q：官方模式只能收到 @ 消息？**
A：官方机器人默认只接收 @ 消息，需在群设置中允许机器人**接收群内全部消息**。

**Q：签到/金币提示不可用？**
A：需要安装 Vault 及配套经济插件；未安装时金币功能自动禁用。

**Q：WebUI 打不开或提示未授权？**
A：确认 `web.enabled: true`、端口未被占用；从浏览器访问需先完成官网账号绑定码登录。

**Q：需要 Java 几？**
A：插件以 Java 25 编译，服务端需运行在 Java 25 上。

---

## 说明

- 仓库源码由 v1.0.0 发布版 JAR 反编译生成（Vineflower），用于二次开发与参考；原始注释与命名以发布版为准
- 推送服务与官网账号体系由 `account.api-base` 配置的服务提供
- 如需贡献代码，欢迎提交 Issue / Pull Request
