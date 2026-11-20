# AVBox 工程状态（每次改前先读这里）

## 当前版本
v1.6.0 / versionCode 71（上一版 v1.5.12 / vc70）

## 这一轮改了什么（v1.6.0）

### 1. 媒体徽章订阅：只留五条在线订阅，内置包全清
- 删除 `res/raw/media_badges.json`、`res/raw/xijing_club_badges.json`（合计约 1.8 MB）
- `BadgeSubscriptions.BUILTIN` 整个移除，`all()` 只返回 `PRESET` 五条：
  心月 / Streambridge / 西京 / Xperience / Nuvio Liquid
- `BadgeStore`：去掉「订阅拉不到就回退内置规则」那段；`readRaw()` 删除；
  读订阅时过滤掉旧版本存下的 `raw:xxx` 条目（升级后自动清干净）；
  删光订阅回到五条预置，`currentSubscription()` 兜底也改成预置第一条
- 从此应用内不带任何徽章规则和徽章图，规则一律在线拉、配图靠「下载离线图」

### 2. 去掉订阅入口和订阅页上的说明小字
- 播放设置 →「媒体徽章订阅」这一行的副标题「切换订阅、添加链接、下载离线图片」删除
  （`settings_media_badges_subscription_subtitle` 连同中文 values 里的定义一起删）
- `BadgeSubscriptionSheet` 顶部「订阅是 JSON 徽章包…」、底部「内置包把规则和 3206 张配图…」
  两段说明删除；底部那段本来就已过时（图早就不在包里）
- 未选中订阅时的占位文案「内置」改为「未选择」

### 3. 播放器：徽章 / 底部控制条 / 片名标识三者不再重叠
- 根因：徽章底边固定 `badgeBottomPadding()`（`vs_140`），而底部控制条竖屏比横屏
  多一行动作图标，实际高度常大于这个固定值 → 徽章压在进度条/按钮上
- 修法：`PlayerOverlay` 用 `onSizeChanged` 实测底部控制条高度，新增
  `overlayBottomInset(controlsHeight)` = `max(设计值, 控制条高度 + vs_10)`
- 徽章底边、片名标识底边都改用这个值；片名仍按徽章实测高度继续往上让
  （`PlayerTitleLogo` 新增 `bottomInset` 参数，默认 0 时行为与旧版一致）
- 控制条还没测量出来（首帧）或比设计值矮时，退回原设计值，位置跟以前一样

### 4. 版本号
- `versionName` 1.5.12 → **1.6.0**，`versionCode` 70 → **71**

## 这一轮改了什么（v1.5.12）

### 1. 修「我的 → 播放设置」黑屏
- 根因：`BadgeStore` 里 `prefs` 字段声明在 `_state` 之后，而 `_state` 的初值
  `MutableStateFlow(initialState())` 里就要用 `prefs` 读订阅 → 构造那一刻 `prefs` 还是 null，
  一创建徽章仓库就 NPE。播放设置页第一行 `remember { MediaBadgeLoader.store(context) }`
  正好第一个踩到，所以点进去直接黑屏（播放页同理，只是之前没走到）。
- 修法：`prefs` 提到 `_state` 之前；`initialState()` 外面包 `runCatching`，
  存的数据坏了也退回内置规则，不让界面崩。
- 新增回归测试 `BadgeStoreInitTest`（2 项）：裸创建仓库要立刻可用；prefs 塞坏数据也不能崩。

### 2. 「运行日志」整页换成 Arcane 那套「运行与诊断」
- 新增 `ui/page/RuntimeLogPage.kt`，照 Arcane `RuntimeLogSettings` 的行为搬：
  - 「开启日志」总开关，关掉就完全不再写（配置键 `runtime_log_enabled`，启动时生效）
  - 「自动滚动」开关（`runtime_log_autoscroll`）
  - 复制日志 / 导出日志文件 / 清空日志 三个动作
  - 日志区最后 500 行、等宽字体、按级别着色（E 红 / W 黄），带「共 N 行 + 当前文件名」
- 上半部分新增「运行状态」：应用版本、设备、系统、播放内核、解码、渲染、徽章订阅、
  内存、可用存储、日志文件、最近一次被拦下的异常
- `EchoLog` 补接口：`lines()` / `lineCount()` / `revision()`（变更计数，界面按它追新）/
  `startedAt()` / `clear()` / `exportTo()`
- `LogViewActivity` 从手写 View 改成 `BaseActivity` + Compose（`RuntimeLogScreen`）；
  「我的」页入口改名「运行与诊断」，走 `LogViewActivity.start(context)`
- 新增文案 `settings_runtime_log*` / `runtime_diag_*` / `runtime_log_*`（values + values-en）

## 上一版记录（v1.5.11）

## 这一轮改了什么

### 1. 媒体徽章改成订阅制（照 Arcane 的 MediaBadgeStore）
- 新增 `player/badge/BadgeSubscription.kt`：订阅模型 + 预置订阅目录
  - 内置规则包 2 个（`raw:media_badges` / `raw:xijing_club_badges`），只留规则、不再带图
  - 预置在线订阅 5 条：心月 xinyue4capyplayer、Streambridge noframe、西京 pppboom、
    Xperience elite、Nuvio Liquid（用户提供的 5 条链接）
  - 订阅 id 由 URL 的 SHA-1 推导，同一链接重复添加会覆盖
- `BadgeStore.kt` 重写：
  - 订阅可加 / 可删 / 可切换，存 SharedPreferences（`badge_subscriptions_v2`）
  - 规则 JSON 缓存到 `files/badge_packs/<id>.json`，TTL 24h；过期先拉、拉不到用旧缓存
  - 加订阅时先真拉一次并解析，成功才入库并自动切过去
  - 删除订阅会连规则缓存和离线图一起删
  - 订阅加载失败 → 回落到内置规则（只有文字，不会整块消失）
- 离线图片（`BadgeImageStore.kt`）：
  - 目录 `files/badge_packs/<id>-images/`，文件名 = URL 的 SHA-256 前 30 字节，svg 保后缀
  - 批量下载：4 并发，进度（已下/总数/失败数）实时上报，可暂停、可继续、可清空
  - 播放时取图顺序：本地离线图 → 网络地址（GitHub raw 走 jsDelivr 镜像）→ 文字徽章
- 新增 `ui/page/BadgeSubscriptionSheet.kt`：订阅管理界面
  - 切换订阅、添加 HTTPS 链接、删除订阅、更新规则、
    下载 / 继续 / 更新 / 清空离线图，进度显示「离线 x/y 张 · n 张失败」

### 2. 去掉「显示方式」，固定徽章
- 设置项、状态字段、OSD 判断、配置键、文案全部移除
- 播放器渲染固定走「有图出图、没图出文字」这一条路
- 旧键 `media_badges_display_mode` / `media_badges_selected_link` / `media_badges_custom_link`
  连同 `MEDIA_BADGE_LINK_*` 常量与 KV 注册一并删掉

### 3. 徽章自动隐藏（照 Arcane 的 showTransport 行为）
- `PlayerOverlay.kt`：徽章层 alpha 跟随 `state.controlsVisible`，呼出控制条淡入、收起淡出
- 只淡出、不撤布局 → 片名标识的让位高度不跳

### 4. 清理内置徽章图（省安装包体积）
- 删除 `assets/badge_images`（3206 张，原占 17 MB 目录体积）
- `BadgeImageStore` 精简为只管离线图；`BadgePacks.kt`、`BadgePacksTest`、`BadgeImageAssetsTest` 删除
- 默认选中改为第一条在线订阅（心月），内置包降级为没网时的规则兜底

## 验证记录（v1.5.12）
- 编译：`:app:compileDebugKotlin`、`:app:assembleDebug` 通过
- 全量单测 1402 项，3 项失败（均为工程自带老问题，本轮未触碰）：
  `EpisodeMatcherTest` 2 项、`HomeSettingsDefaultTest.phoneKeepsImmersiveDefault`
- 徽章相关 17 项全绿，含新增 `BadgeStoreInitTest` 2 项
- APK 核对：v1.5.12 / vc70、dex 含 `RuntimeLogPageKt` / `LogViewActivity` / `BadgeStore`、
  资源含「运行与诊断」、0 张内置 badge_images、V2 签名 + zipalign 通过

## 验证记录（v1.5.11）
- 编译：`:app:compileDebugKotlin` 通过；`:app:assembleDebug` 通过
- 全量单测 1400 项，3 项失败（均为原工程自带，与本轮无关）：
  `EpisodeMatcherTest` 2 项、`HomeSettingsDefaultTest.phoneKeepsImmersiveDefault`
  - 上一轮记的 14 项 Robolectric 失败，本轮给测试进程挂代理后全部通过
- 新增单测：`BadgeSubscriptionsTest`（6 项）、`BadgeOfflineNameTest`（4 项）
- APK 核对：v1.5.11 / vc69、APK 内 0 张 badge_images、无 `media_badges_display_mode` 残留、
  `BadgeSubscriptionSheetKt` / `BadgeStore` / `BadgeSubscription` 均在 dex、
  新文案「媒体徽章订阅」在 resources、V2 签名 + zipalign 通过
- 5 条订阅链接实测：全部 HTTP 200，均为标准 groups+filters 格式
  （规则数 1650 / 310 / 727 / 31 / 261），首图 SVG/PNG/WebP 全部可下载

## 构建环境（每轮要重装/重配）
- Gradle 9.8.1：`~/tools/gradle-9.8.1/bin/gradle`（wrapper 下不来发行版，手动下载）
- JAVA_HOME=/opt/deepbot-toolchain/jdk-21.0.13+9，需加 `-Porg.gradle.java.installations.paths=<同上>`
- Java 出网需显式给代理：`GRADLE_OPTS="-Dhttp(s).proxyHost/Port=..."`（勿写进交付文件）
- 跑 Robolectric 单测时同样要 `JAVA_TOOL_OPTIONS` 带代理，否则 android-all 下不下来
- Chaquopy 需要 Python 3.10：`uv python install 3.10`，把其 bin 目录加到 PATH
- `local.properties` 里 `sdk.dir=/opt/deepbot-toolchain/android-sdk`（交付包不含此文件）

## 注意事项
- v1.6.0 起应用内**不再内置任何徽章规则和徽章图**；规则在线拉，图走订阅「下载离线图」
- 片名标识取图仍走 TMDB（中文 logo → 英文 → 第一个，w500）
- 「始终显示片名标识」默认开
