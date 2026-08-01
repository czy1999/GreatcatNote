# GreatcatNote Mobile

GreatcatNote Mobile 是 `czy1999/Greatcat` 的个人 Android 客户端。它只做一条高频闭环：从 GitHub 私有仓库同步知识库，在手机上舒适地阅读 Markdown 和 PDF，并把手机导入的文件增量推回仓库。

## 快速开始

1. 安装最新的 `GreatcatNote-Mobile-debug.apk`。
2. 在 GitHub 创建只授权 `Greatcat` 仓库、`Contents: Read and write` 的 fine-grained personal access token。
3. 首次打开应用，只输入一次以 `github_pat_` 开头的令牌。
4. 点击“连接并同步”。

以下个人配置已经内置，无需填写：

| 项目 | 默认值 |
| --- | --- |
| 仓库 | `https://github.com/czy1999/Greatcat.git` |
| 分支 | `master` |
| 用户名 | `czy1999` |

令牌使用 Android Keystore 加密后保存在应用私有目录，不会写入仓库或 Git 地址。

## 当前能力

- 首次克隆私有 GitHub 仓库，后续执行增量 Git 同步。
- 浏览、搜索并按 Markdown/PDF 类型筛选知识库文件。
- 连续滚动阅读 Markdown，支持链接和文本选择。
- 按页阅读 PDF，并自动适配手机屏幕宽度。
- 直接打开手机中的 Markdown/PDF，不写入仓库。
- 将手机文件导入 `imports/`，下次同步时自动提交和推送。
- 同步冲突时停止并回滚 rebase，避免自动猜测导致笔记丢失。

## 同步规则

第一次同步会把 `master` 克隆到应用私有目录。之后每次同步按以下顺序运行：

```text
检测本地变更 -> 本地提交 -> pull --rebase -> push -> 刷新文件列表
```

电脑端修改必须先提交并推送到 GitHub，手机才能拉取。手机导入的文件会在同步时以 `sync: mobile changes` 提交。

如果提示冲突，请在电脑端解决后重新同步。应用不会自动覆盖两端内容。

## 构建

本地要求：

- JDK 17
- Android SDK 35
- Gradle 8.11.1

```bash
cd android-lite
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

APK 输出位置：

```text
android-lite/app/build/outputs/apk/debug/app-debug.apk
```

推送 `android-lite/**` 后，GitHub Actions 工作流 `.github/workflows/greatcatnote-android.yml` 会自动测试并生成 `GreatcatNote-Mobile-APK` 构建产物，不消耗本地计算资源。

## 开发入口

架构、数据流、故障定位和特性扩展方法见 [DEVELOPMENT.md](DEVELOPMENT.md)。

本项目位于 AGPL-3.0-or-later 的 GreatcatNote 源码树中，第三方依赖见 `THIRD_PARTY_NOTICES.md`。
