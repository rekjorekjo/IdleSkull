# IdleSkull 0.2.18-beta

- 更新检查改为单一来源：只读取最新已发布 GitHub Release 中的 `latest.json` 资产。
- 不再读取或依赖 `main/latest.json`，也移除 Release 后自动同步清单的 GitHub Actions 工作流。
- 为兼容 beta prerelease，不使用 GitHub `/releases/latest`；改为从公开 Releases 列表按 `published_at` 选取最新发布版本，再读取其 `latest.json`。
- 最新 Release 若缺少或携带错误的 `latest.json`，检查会直接失败，避免误回退到旧版本清单。
- 发布脚本只生成 `dist/latest.json`，与 APK 一起作为 Release 资产上传即可。
- 版本更新为 0.2.18-beta（versionCode 21）。
