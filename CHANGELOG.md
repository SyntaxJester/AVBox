## v1.3.19 (2026-10-29)

**整合 X-BOX HTML 功能和代理功能:**

- 新增 HtmlActivity：完整的 WebView 容器，支持 fm-api.js 注入
- 新增 HtmlSpider：处理 type=6 的 HTML 类型站点
- 新增 fm-api.js：JS API 桥接层，提供 fm.req/fm.play/fm.open 等接口
- 新增 webnav.jar：WebNav 导航服务（csp_WebNav/csp_Nostr）
- 更新 SpiderLoader.getCSP() 支持 .html 类型站点
- 更新 AndroidManifest.xml 添加 HtmlActivity 声明

**HTML 类型站点配置示例:**
```json
{
    "key": "xxx",
    "api": "./assets/html/example.html",
    "type": 6,
    "ext": "..."
}
```

---

## v1.3.18 (2026-10-29)

**新增 HTML 代理支持:**

- 修复 HTML 页面相对资源加载 404 问题
- 新增 `/html/` 路由处理器，支持从 assets/html 目录提供文件
- 支持所有常见 MIME 类型（HTML, JS, CSS, JSON, 图片，字体等）
- 与现有 WebViewBridge.resolveUrl() 配合工作，HTML 页面的 fm.req/fetch 现在可以正常加载相对资源

**技术细节:**
- RemoteServer 新增 `/html/` 路由处理器
- 新增 `guessMimeType()` 方法自动识别 MIME 类型
- 使用 AssetManager 读取 assets/html 目录

---

## v1.3.14 (2026-10-24)

**同步原版 v1.2.9 全部功能:**

- 修复网盘源点进文件夹后显示成其他影片
- 优化错误提示
- 优化超时处理
- 优化搜索框的焦点处理
- 优化订阅配置的缓存更新逻辑
- 优化软解回退逻辑，失败自动回退硬解
- 优化错误处理逻辑，分离视频和音频的解码错误
- 优化崩溃检测，避免因导入未适配高版本安卓的 jar 源造成无限闪退
- 详情页的弹窗改为固定高度
- 搜索结果页左侧列表新增过滤站源功能
- 支持本地导入 js 源
- 支持播放 iso 格式的蓝光原盘

**技术细节:**
- 新增 ISO 播放引擎（46 个新文件）
- 新增 js 爬虫工具类（HttpGate, JsSession, ProxyStream 等）
- 新增 UI 组件（FocusDismiss, PressEffect）
- 新增 50+ 单元测试

---

## v1.3.13 (2026-10-24)

- 移除 deprecated WebSettings APIs
- 使用现有 color resource
- WebView 性能优化
