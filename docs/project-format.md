# Compositor `.comp` 格式：安卓移植版实现说明

上游格式定义见 robbietilton/Compositor 的 `docs/project-format.md`
（v1–v11）与 `Compositor/IO/ProjectStore.swift`。本文记录安卓移植版
（`:core`）当前实现的范围与有意偏离。

## 实现范围：v1–v3 子集

| 上游版本 | 内容 | 本移植版 |
|---|---|---|
| v1 | 图层：`id`、`name`、`isVisible`、`transform`、`imageFile` | ✅ 完整实现 |
| v2 | 分组：`parentID`、`isGroup`（64 层嵌套上限、防环、父须为组） | ✅ 完整实现 |
| v3 | `opacity`（0–1）、`blendMode`（9 种） | ✅ 完整实现 |
| v4–v11 | 蒙版、调整图层、参考线、文本、特效等 | ❌ 读取时忽略，写入时不产生 |

## 字段名（已与 `ProjectStore.swift` 逐项核对）

顶层：`format`（=`"com.compositor.project"`）、`version`、`colorSpace`（=`"sRGB"`）、
`resolution`（可选）、`documentID`、`width`、`height`、`activeLayerID`（可选）、`layers`。

图层：`id`、`name`、`isVisible`、`transform`、`imageFile`（可选）、`parentID`（可选）、
`isGroup`、`opacity`、`blendMode`。

`transform`：`origin{x,y}`、`size{width,height}`、`rotation`（顺时针角度）、
`flipX`、`flipY`、`sampling`（`"Nearest"` / `"Smooth"` / `"High quality"`）。

`imageFile` 是 `images/` 目录下的裸文件名，上游要求严格等于 `"<id>.png"`。

## 读取策略：接受 1–11，宽松忽略

- `version` 在 1–11 之间接受；未知字段一律忽略（`ignoreUnknownKeys`）。
- v4+ 的附加字段（`maskFile`、`adjustment`、`text`、`effects`、`guides` 等）
  会被静默丢弃——这是有意的阶段性取舍，Mac 版用 v11 保存的普通多图层文档
  （只用 v3 子集字段）可以正常互导。
- 写入始终声明 `version: 3`，只写 v3 子集字段。

## 校验规则（对标 `ProjectStore.validate` 的 v3 相关部分）

- `format`、`colorSpace` 必须精确匹配；`resolution` 如出现须在 1–9600 内。
- 画布边长 1–30000；图层数 ≤ 10000；`manifest.json` ≤ 4 MiB。
- 图层：`id` 唯一；`name` 非空且 UTF-8 ≤ 16384 字节；`transform` 须满足
  `LayerTransform.isValid`（各分量有限、`size` 在 1–300000 内、`origin` 绝对值 ≤ 1000000）；
  `opacity` 有限且在 0–1 内。
- 版本门：
  - v1：不允许 `parentID` / `isGroup`；
  - v1–v2：`opacity` 必须为 1、`blendMode` 必须为 Normal；
  - v1–v7：分组的 `opacity` 必须为 1（v8+ 才允许分组自带不透明度）。
- 分组（对标 `LayerHierarchy.validate`）：`parentID` 必须存在且指向分组；
  无环；祖先链 ≤ 64 层；分组不能带 `imageFile`；分组的 `blendMode` 必须为 Normal。
- `activeLayerID` 如出现必须指向存在的图层。

## 可见性与不透明度继承（对标上游）

- 有效可见性 = 自身 `isVisible` 与所有祖先分组 `isVisible` 的 AND
 （`LayerHierarchy.entries` 语义；子项标志本身不被修改）。
- 有效不透明度 = 自身 `opacity` × 所有祖先分组的 `opacity`
 （`LayerOpacity.effective`；分组是 pass-through，不单独合成）。

## 安全

- 拒绝 `manifest.json` > 4 MiB、图层数 > 10000、画布/变换超限。
- `imageFile` 必须严格等于 `"<id>.png"`，从源头杜绝路径穿越
  （上游同策略；读取图片时仍需把文件约束在包内并校验大小 ≤ 512 MiB，Phase 3 实现）。
