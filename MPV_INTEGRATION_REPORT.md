# MPV播放器内核集成报告

## 当前状态

### 已完成
1. ✅ **版本号**: v1.3.3 (versionCode=35)
2. ✅ **自动跳过片头片尾**: 已集成到KernelPlayer/ExoPlayer/IjkPlayer
3. ✅ **MPV native库**: 已添加到jniLibs
   - arm64-v8a: libplayer.so, libmpv.so, libav*.so等
   - armeabi-v7a: 同上
4. ✅ **MpvSimplePlayer.kt**: 创建了简化包装类（TODO状态）

### 需要解决的问题
**完整MPV内核集成无法在当前环境完成**，原因：

1. **Media3 Fork依赖**
   - webhtv使用的是修改版Media3 (1.11.0-alpha01-fongmi)
   - 需要47个MPV相关Java类
   - 包含HLS/ISO自适应缓存等特殊逻辑

2. **Native构建配置**
   - 需要CMakeLists.txt配置
   - 需要third_party/mpv-native库
   - 需要mpv-player-jni绑定

3. **工具链问题**
   - 当前CI环境无法下载JDK 21
   - 沙箱中JDK 17运行Gradle会崩溃

## 替代方案

### 方案A: 使用现有IJK + ExoPlayer双内核（推荐）
- 已支持自动跳过片头片尾
- 稳定可靠，无复杂依赖
- 版本v1.3.3已包含此功能

### 方案B: 等待预编译MPV AAR
- 建议向webhtv项目请求预编译的MPV AAR包
- 或从mpv-android releases获取

### 方案C: 手动配置MPV集成
```groovy
// 需要添加到app/build.gradle
implementation project(':mpv-player')
// 或
implementation 'is.xyz:mpv:1.9.0'
```

## 技术细节

webhtv的MPV实现架构：
```
MpvPlayerEngine (Java)
  └─ MpvPlayer (androidx.media3.mpvplayer)
      ├─ MPVLib (is.xyz.mpv) - JNI绑定
      ├─ MpvPlayerConfig
      └─ 47个策略类 (HlsAdTimeline, MpvAudioDecoderPolicy等)
```

本地native库:
- libplayer.so - MPV播放器核心
- libmpv.so - MPV库
- libav*.so - FFmpeg解码器
