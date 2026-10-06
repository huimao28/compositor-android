# Compositor for Android

Compositor 安卓移植版：免费、开源的 Photoshop 式图像编辑器。

上游是 [robbietilton/Compositor](https://github.com/robbietilton/Compositor)（Mac 原生，MIT 协议）。
本项目参考 [adi805/compositor-windows](https://github.com/adi805/compositor-windows) 的文档驱动方法论，
用 Kotlin + Jetpack Compose 从零实现，`.comp` 工程格式与 Mac 原版互操作。

## 现状（Phase 0）

- Gradle 工程：`:core`（纯 Kotlin/JVM，无 Android 依赖）+ `:app`（Compose UI）
- `:core`：文档模型（Document/Layer/Group）、上游 v3 的 9 种混合模式、
  `manifest.json` 读写（v1–v3 子集）+ round-trip 单元测试
- `:app`：横屏三栏界面（左工具条 | 中画布 | 右图层面板，抄桌面端布局），
  WindowSizeClass 自适应（Pad 大屏三栏，小屏画布+可收起面板）
- GitHub Actions：`:core:test` + `:app:assembleDebug`，产物上传 APK

## 构建

```bash
./gradlew :core:test        # 跑 core 单元测试
./gradlew :app:assembleDebug # 打 debug APK
```

需要 JDK 17。Android Studio 直接打开项目根目录即可。

> 说明：`gradle/wrapper/gradle-wrapper.jar` 以 base64 文本形式存放在
> `gradle/wrapper/gradle-wrapper.jar.b64`（二进制文件走 API 推送不便），
> 克隆后运行 `./scripts/restore-wrapper.sh` 还原即可；CI 会自动还原。

## 文档

- `docs/ROADMAP.md` —— 分阶段计划，每阶段有验收标准
- `docs/TECH-STACK.md` —— 版本钉死表
- `AGENTS.md` —— 给 AI 助手的协作说明

## 协议

MIT。上游版权归 Wonder Assembly LLC（Robbie Tilton）所有，见原仓库 LICENSE。
