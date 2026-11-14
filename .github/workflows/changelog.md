## v1.5.8-fix1 (2026-11-14)

**修复 JSON 配置字段缺失导致的异常:**

- 修复 `PlayerHelper.updateCfg()` 中 `getInt("pr")` 缺失字段导致 `JSONException: No value for pr`
- 修复 `PlayerConfigDelegate` 中 `getInt("pl")`、`getInt("st")`、`getInt("et")`、`getDouble("sp")` 缺失字段导致的异常
- 修复 `PlayerActionsDelegate.onPlayerLongClicked()` 中 `getInt("pl")` 缺失字段导致的异常
- 修复 `PlaybackStarter.goPlayUrl()` 中 `getInt("pl")` 缺失字段导致的异常
- 所有 `getInt`/`getDouble` 调用改为 `optInt`/`optDouble` 带默认值，避免字段缺失时抛异常
- 移除了冗余的 try-catch 块

