## v1.5.8-fix4 (2026-11-14)

**修复媒体徽章字段名不匹配导致徽章显示文字而不是图片:**

- `BadgeRule.kt` 中字段名 `image` 不匹配徽章包 JSON 中的 `imageURL`，导致徽章图片 URL 为空，只能显示文字徽章
- 已将 `BadgeRule.image` 改为 `imageURL`，与徽章包 JSON 字段名一致
- `MediaBadgeLoader.kt` 中引用 `rule.image` 改为 `rule.imageURL`

---

## v1.5.8-fix1 (2026-11-14)

**修复 JSON 配置字段缺失导致的异常:**

- 修复 `PlayerHelper.updateCfg()` 中 `getInt("pr")` 缺失字段导致 `JSONException: No value for pr`
- 修复 `PlayerConfigDelegate` 中 `getInt("pl")`、`getInt("st")`、`getInt("et")`、`getDouble("sp")` 缺失字段导致的异常
- 修复 `PlayerActionsDelegate.onPlayerLongClicked()` 中 `getInt("pl")` 缺失字段导致的异常
- 修复 `PlaybackStarter.goPlayUrl()` 中 `getInt("pl")` 缺失字段导致的异常
- 所有 `getInt`/`getDouble` 调用改为 `optInt`/`optDouble` 带默认值，避免字段缺失时抛异常
- 移除了冗余的 try-catch 块
