# Compositor for Android — Roadmap

分阶段计划。抄 adi805/compositor-windows 的规矩：每阶段有明确验收标准，
测试不绿不算做完。

## Phase 0 — 脚手架（当前）

- Gradle 工程（`:core` 纯 Kotlin/JVM + `:app` Compose），版本目录集中管理
- `:core`：Document/Layer/Group 数据模型、9 种混合模式枚举、
  manifest.json 读写（v1–v3 子集）与 round-trip 测试
- `:app`：横屏三栏界面骨架（工具条/画布占位/图层面板），WindowSizeClass 自适应
- CI：GitHub Actions 跑 `:core:test` + 打 debug APK

验收：`./gradlew :core:test` 全绿；`./gradlew :app:assembleDebug` 产出可安装 APK。

## Phase 1 — 文档模型 hardened

- manifest 字段与上游 `ProjectStore.swift` 逐项核对（含 transform 子字段命名）
- 分组校验（parentID 存在性、防环、64 层上限）、visibility 继承语义
- 不安全路径/超限 manifest 的拒绝测试（已部分覆盖，补齐 4 MiB / 512 MiB / 10000 层边界）
- undo/redo 命令栈（command pattern）

验收：与 Mac 版互导一个多图层 `.comp` 文件夹，图层顺序/不透明度/混合模式一致。

## Phase 2 — 像素引擎

- 基于 Android Bitmap 的 RasterSurface；图层合成（9 种混合模式逐像素实现，对照 PDF 规范）
- 画笔引擎：按上游 brush-performance.md（spacing + stroke opacity 上限）
- CanvasArea 接入真实渲染；棋盘格保留为透明背景

验收：合成结果与桌面端像素级一致（抽样测试）；一笔 undo 还原像素精确。

## Phase 3 — UI 接线

- 图层面板接真实 Document（增删/改名/排序/可见性/不透明度/混合模式）
- 画布手势：单指画笔、双指缩放/平移；触控笔压感（有笔时）
- 打开/保存 `.comp`（Storage Access Framework）、导入 PNG/JPEG、导出拼合 PNG/JPEG
- 顶栏按钮逐个接线（撤销/重做/保存/导出）

验收：真机/Pad 上走完「新建→画画→保存→重开」全流程。

## Phase 4 — 工具

- 选区（矩形/椭圆/套索/魔棒）、移动/缩放/旋转变换
- 调整图层（色阶/曲线/色相饱和度/曝光，v7 子集）
- 蒙版（v4）与裁剪蒙版（v5，maskSourceID）

验收：每个工具至少一个走文档模型的集成测试。

## 非目标

- iOS/macOS（上游已覆盖）、云同步、插件 API
- 桌面端键鼠交互照搬：触屏手势优先

## 风险

- 大图性能：Bitmap 全内存方案在手机上吃内存，Phase 2 后期视情况上分块（tile）
- HEIC/RAW 解码：Android 端用系统解码器，行为与 ImageIO 可能有差异，记录在案
