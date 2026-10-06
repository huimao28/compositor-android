# AGENTS.md — 给 AI 助手的协作说明

## 项目

Compositor 安卓移植版。上游是 robbietilton/Compositor（Mac/Swift，MIT），
方法论参考 adi805/compositor-windows（文档驱动 + 测试驱动）。

## 架构铁律

- `:core` 是纯 Kotlin/JVM 模块，**禁止**依赖任何 Android API。
  文档模型、混合模式、manifest 读写、像素算法都在这里，可单元测试。
- `:app` 是 Compose UI 壳，只做展示和手势，把状态变更翻译成对 `:core` 的调用。
- `.comp` 格式字段名跟上游走（见 `docs/project-format.md` 意图），
  transform 子字段命名在 Phase 1 用 ProjectStore.swift 核对。

## 工作流

- 先看 `docs/ROADMAP.md` 找当前阶段和验收标准；做完先跑测试再说话。
- 新功能 = 实现 + 单元测试（`:core`）+ ROADMAP 状态更新。
- 代码注释用英文，面向用户的文档用简体中文。
- 不要为了赶工把 Android 依赖漏进 `:core`；编译期就能发现，别抱侥幸心理。

## 常用命令

```bash
./gradlew :core:test          # core 单元测试
./gradlew :app:assembleDebug  # 打 debug 包
```
