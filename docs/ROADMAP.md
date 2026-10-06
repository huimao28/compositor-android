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

## Phase 1 — 文档模型 hardened（✅ 已完成，2026-10-07）

- manifest 字段与上游 `ProjectStore.swift` 逐项核对（含 transform 子字段命名）
  → 修正：`format`/`documentID`/`activeLayerID`/`id`/`isVisible`/`flipX`/`flipY`/`sampling`；
  `imageFile` 为裸文件名且须严格等于 `"<id>.png"`；新增 `colorSpace` 校验与 `resolution` 可选字段
- 读 1–11（宽松忽略未知字段，v4+ 特性静默丢弃），写声明 v3；见 `docs/project-format.md`
- 分组校验（parentID 存在性、防环、64 层上限、分组无图/必须 Normal 混合）、visibility 继承语义、
  分组不透明度乘法继承（对标 `LayerHierarchy` / `LayerOpacity`）
- 拒绝测试：4 MiB manifest、10000 层、重名、空名、坏路径、v1 分组、v2 非默认外观、坏 transform 等（35 个测试）
- undo/redo：`DocumentHistory`（不可变快照栈，默认上限 100，`commit`/`undo`/`redo`/`clear`）

验收：与 Mac 版互导一个多图层 `.comp` 文件夹，图层顺序/不透明度/混合模式一致。

## Phase 2 — 像素引擎（🚧 代码+本地 60 测试已完成，待 CI 验证）

- `:core` 纯 Kotlin 像素引擎（架构铁律：禁止 Android API，单元测试覆盖）：
  - `Raster.kt`：`RasterImage`（直通 alpha ARGB_8888）+ `compositePixel`（PDF 基础 alpha 合成公式）
  - `BlendMode.blend`：9 种混合模式逐像素实现，对照 PDF 32000 §11.3.5（Normal/Multiply/Screen/
    Overlay/Darken/Lighten/ColorDodge/ColorBurn/Difference）
  - `Compositor.kt`：`compositeDocument` bottom-to-top 合成（visibility/分组继承/opacity/混合模式），
    图层经 Transform 做仿射放置（翻转/缩放到 size/顺时针旋转/平移），双线性反向采样
  - `Brush.kt`：画笔引擎（对照上游 `docs/brush-performance.md`）：dab 间距 = 直径 × 2.5%（软）/
    1.5%（硬）；软笔刷余弦衰减、硬笔刷 1px 抗锯齿边缘；opacity 上限作用于整笔累积；
    笔触不修改原图（返回新 RasterImage）
  - `History<T>` 泛型化：`DocumentHistory = History<Document>`（旧调用不变），
    新增 `PixelSession`（Document + 图层 rasters）与 `PixelHistory`，一笔 undo 像素精确还原
- `:app`：`BitmapBridge`（直通→预乘 alpha 转 Android Bitmap）、`DemoDocument`（渐变背景 +
  软笔刷笔触 + Multiply 绿圆 + 旋转 Screen 黄方块，真实走合成管线）、`CanvasArea` 绘制合成图，
  棋盘格保留为透明背景；合成在 `Dispatchers.Default` 后台线程执行
- 测试：60 个全过（35 旧 + 25 新：混合公式/合成/变换放置/画笔/undo）

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
