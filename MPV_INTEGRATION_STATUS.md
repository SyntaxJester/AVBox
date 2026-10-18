# MPV播放器内核集成状态

## 已完成
1. ✅ 版本号更新到 1.3.3 (versionCode=35)
2. ✅ MPV native库已复制到 jniLibs
   - libplayer.so
   - libmpv.so
   - libmv*.so
3. ✅ MPVLib.java 已添加

## 需要解决的问题
MPV播放内核需要以下依赖，无法简单移植：
1. **Media3 fork** - webhtv修改了Media3以支持MPV
2. **47个MPV Java类** - 需要完整移植并修改包名
3. **CMake构建配置** - 需要原生库编译
4. **第三方依赖** - iso cache、HLS自适应等

## 推荐方案
由于MPV集成复杂度过高，建议使用：
1. **当前方案**: IJK + ExoPlayer双内核，已支持自动跳过片头片尾
2. **未来方案**: 等待webhtv发布预编译的MPV AAR包
3. **替代方案**: 使用其他支持MPV的开源播放器项目

## 技术细节
webhtv的MPV实现包含：
- `androidx.media3.mpvplayer.*` (47个文件)
- `is.xyz.mpv.MPVLib` (JNI封装)
- 自定义HLS/ISO处理逻辑
- 音频指纹识别（用于广告/片头检测）

这些依赖与标准Media3不兼容，需要完整的fork版本。
