# 文字检查器

离线中文错字/标点检查工具，面向工作交接与日常沟通场景。零联网、零成本。

## 组成

| 部分 | 路径 | 说明 |
|---|---|---|
| 检查引擎 | `checker.js` | 浏览器/Node 双端，950 对错别字 + 标点/重复规则 |
| 词库 | `data/pairs.js` | 网页词表 + 手工精选，经 56462 词词典双向校验 |
| 网页版 | `dist/文字检查器-离线版.html` | 单文件，手机/电脑浏览器打开即用 |
| Android App | `android/` | 悬浮球 + WebView 复用引擎，仅 SYSTEM_ALERT_WINDOW 权限 |
| 词库构建 | `data/build_pairs.js` | 从 `data/*.html` 词源重新生成词库 |

## 出 APK（GitHub Actions）

1. 把本目录推到 GitHub 仓库
2. Actions 会自动运行 `Build APK`，完成后在 Artifacts 下载 `text-checker-apk`
3. 手机安装（允许未知来源）

也可手动触发：仓库 Actions 页 → Build APK → Run workflow。

## Android 使用

1. 安装后打开 App，点「第一步：授权悬浮窗权限」
2. 三星路径：设置 → 应用 → 文字检查器 → 出现在顶部（或：更多设置 → 特殊权限 → 显示在其他应用上层）
3. 回到 App 点「第二步：启动悬浮球」
4. 微信写好话 → 全选复制 → 点蓝色「检」球 → 卡片自动检查 → 一键修正 → 复制修正文本 → 回微信粘贴

悬浮球可拖动；点击卡片右上角 ✕ 关闭。

## 隐私说明

- App 清单中唯一权限是 `SYSTEM_ALERT_WINDOW`，**无 INTERNET 权限**（系统设置中可验证）
- 检查引擎打包在 APK 内 assets，全程本地执行
- 网页版为单文件 HTML，不发起任何网络请求

## 本地测试

```bash
node test_checker.js   # 21 项规则回归测试
```
