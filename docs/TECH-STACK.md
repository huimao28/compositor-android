# TECH-STACK

版本钉死表。升级前先验证构建+测试全绿，再更新此表。

| 组件 | 版本 | 说明 |
|---|---|---|
| Gradle | 8.10.2 | wrapper 自带 |
| Android Gradle Plugin | 8.7.3 | |
| Kotlin | 2.1.20 | Compose 编译器走 `org.jetbrains.kotlin.plugin.compose` |
| Compose BOM | 2025.04.01 | |
| compileSdk / targetSdk | 35 | |
| minSdk | 26 | Android 8.0+ |
| Java | 17 | |
| kotlinx-serialization-json | 1.8.1 | `:core` manifest 编解码 |
| material3-window-size-class | 1.3.1 | Pad/手机自适应 |
| JUnit | 4.13.2 | `:core` 单元测试 |

验证命令：

```bash
./gradlew :core:test
./gradlew :app:assembleDebug
```
