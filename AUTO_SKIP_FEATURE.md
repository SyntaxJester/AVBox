# 自动跳过片头片尾功能

## 功能说明

本版本在播放器内核中添加了自动跳过片头片尾的功能，基于时间戳检测实现。

## 实现原理

1. **KernelPlayer基类** - 添加`checkAutoSkip()`方法，所有播放器继承此功能
2. **ExoPlayer集成** - 在`onPrepared`回调中自动调用`checkAutoSkip()`
3. **IjkPlayer配置** - 读取HawkConfig中的跳过设置

## 配置项

- `AUTO_SKIP_INTRO` - 是否自动跳过片头 (默认: false)
- `AUTO_SKIP_OUTRO` - 是否自动跳过片尾 (默认: false)  
- `AUTO_SKIP_AD` - 是否自动跳过广告 (默认: false)

## 默认跳过时长

- 片头: 前30秒
- 片尾: 后60秒
- 广告: 30秒

## 使用方式

在代码中启用：
```kotlin
player.autoSkipEnabled = true
player.introDurationMs = 30_000L  // 30秒
player.outroDurationMs = 60_000L  // 60秒
```

或通过HawkConfig配置：
```kotlin
KV.put(HawkConfig.AUTO_SKIP_INTRO, true)
KV.put(HawkConfig.AUTO_SKIP_OUTRO, true)
```

## 关于MPV内核

webhtv的MPV播放内核需要：
- Media3 fork (已修改支持MPV)
- 原生 `libplayer.so` (4个ABI)
- 音频指纹识别库

这些依赖较重，无法简单移植。当前的基于时间戳的自动跳过可以解决大部分片头片尾问题。

## 版本信息

- versionCode: 33
- versionName: 1.3.1
- 最新commit: 456eff1
