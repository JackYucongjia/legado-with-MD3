# 且看有声书融合架构

## 决策

且看将 Audiobookshelf 作为独立的伴随服务端，通过原生 Android 客户端模块接入。
Node.js、Nuxt、Sequelize、服务端 SQLite、FFmpeg 和媒体扫描器不打包进 APK。

当前工作区中的 `.audiobookshelf` 仅用于协议核对和服务端 Fork 开发。正式维护时应将它
保留为独立仓库或 Git submodule，并使用可识别的 Fork 版本号，例如
`2.34.0-qiekan.1`。

## 模块边界

- `:modules:audiobookshelf-client`
  - Audiobookshelf REST 请求、DTO 和协议模型。
  - 不依赖且看的 Room、Compose、主题或阅读领域。
- `app/domain/gateway/AudiobookGateway.kt` 与 `app/domain/model/AudiobookModels.kt`
  - 服务器、媒体库、书籍、播放会话和进度的领域接口及模型。
- `app/data/audiobook`
  - 领域仓库、服务器配置映射和安全令牌存储。
- `app/ui/audiobook`
  - Compose、MVI/UDF、Navigation 3 页面。
- `app/service/audiobook`
  - 后续阶段新增的 Media3 `MediaSessionService`。

Audiobookshelf 条目不能映射为现有 `Book`/`BookChapter`，也不能直接写入旧版
`AudioPlay` 的全局状态。远端条目统一使用 `serverId + libraryItemId` 标识。

## 认证与安全

- API 使用独立的标准 OkHttpClient，不复用且看支持书源规则的宽松 TLS 客户端。
- 登录请求使用 `x-return-tokens: true`。
- access token 仅保存在进程内存中。
- refresh token 使用 Android Keystore 的 AES/GCM 密钥加密，并写入
  `noBackupFilesDir`，不进入且看备份。
- 401 刷新采用 single-flight，避免刷新令牌并发轮换。
- 服务器地址和用户名可随 `servers.json` 备份；密码和令牌不得进入配置 JSON。
- 公网地址默认使用 HTTPS。HTTP 仅用于用户明确配置的可信局域网环境。

## 导航与隐私

首版入口位于“我的 > 有声书”，不增加默认第六个底部导航项。后续可将有声书加入
主导航显示与排序设置，但默认保持隐藏。

后续接入且看首页“继续收听”、听书历史和统计时，必须在领域层统一应用隐私过滤，
而不是由各个组件自行过滤。隐藏书库的内容不得进入首页、历史、统计和锁屏元数据。

## 实施阶段

1. 连接与浏览：服务器配置、登录、令牌刷新、媒体库和书籍列表。
2. 播放：独立 Media3 Session、direct play、HLS、STRM、多音轨和进度同步。
3. 体验融合：继续收听、迷你播放器、书签、历史、主导航和隐私联动。
4. 离线：独立缓存数据库、断点续传、空间管理和离线进度回传。
5. 高级能力：Socket.IO、OIDC、播客和 115 动态换链或代理。

## 第一阶段验收

- 可连接带根路径的 Audiobookshelf 地址，例如 `/audiobookshelf`。
- 可使用用户名和密码登录，并列出当前账号可访问的媒体库。
- 应用重启后可用加密 refresh token 恢复会话。
- refresh token 无效时保留服务器地址和用户名，并要求重新输入密码。
- WebDAV 服务器列表不显示 Audiobookshelf 类型的连接。
- 旧备份恢复流程不变，备份文件中不包含 Audiobookshelf 密码或令牌。
