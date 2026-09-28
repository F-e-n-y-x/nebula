# :framegen — Lossless Scaling Frame Generation 桥接模块

把上游 [`lsfg-vk-android`](https://github.com/FrankBarretta/lsfg-vk-android)（MIT）作为子模块引入，
在 moonlight-android 里提供 AHardwareBuffer → Vulkan compute 的插帧管线。

> **当前状态：阶段 1 — 骨架。** 只验证 CMake 链路 + JNI 装载，无任何业务功能。

## 路线图

| 阶段 | 内容 | 状态 |
|------|------|------|
| 1 | submodule + CMake 骨架 + 占位 `FramegenInterceptor` | ✅ |
| 2 | MediaCodec → ImageReader 拦截 + 直通显示（验证零拷贝路径不破） | TODO |
| 3 | 接入 `LSFG_3_1::createContextFromAHB`，单倍率（2×）插帧跑通 | TODO |
| 4 | shader 提取：用户 SAF 选 `Lossless.dll`，pe-parse + dxbc → SPIR-V | TODO |
| 5 | UI 开关、GPU 白名单、HDR 互斥、frame pacing 重调 | TODO |

## 许可证

- **本目录**（除 `src/main/cpp/lsfg-vk-android/`）：与 moonlight-android 主仓一致（GPLv3）。
- `src/main/cpp/lsfg-vk-android/`：上游 MIT，详见该目录下 `LICENSE.md`。
- **`Lossless.dll` 永远不入仓**。用户必须自备正版，运行时通过 SAF 选择。

## 设备要求

- Android 10+（API 29+，AHardwareBuffer Vulkan import）
- arm64-v8a
- Vulkan 1.1+ 并支持 `VK_ANDROID_external_memory_android_hardware_buffer`
- 实测稳定：Adreno 7xx+（骁龙 8 Gen 2 及更新）

## Nebula (English)

- **Lossless.dll is never in git.** Private builds inject it from outside the repository:
  `nebulaLosslessDll=/path/to/Lossless.dll` in `local.properties` (or `-PnebulaLosslessDll=`). Nebula
  packages it as `assets/framegen/Lossless.dll` and stages it into `noBackupFilesDir` on first run.
  `-PnebulaPublicBuild=true` (and `CI=true`) refuses a configured DLL and fails if one reaches the
  merged assets. `.gitignore` ignores `*.dll` and `private-assets/`; `tools/check-private-assets.sh`
  (CI) and `:nebula:checkNoProprietaryFiles` (every build) fail if git tracks one.
- **lsfg-vk licence pin.** `src/main/cpp/lsfg-vk-android` is pinned to
  `3e89e5439a98f55d5acb003d20039426ab24e69c` (lsfg-vk-android v1.0.0-7, 2026-05-01), which carries the
  MIT `LICENSE.md`. lsfg-vk moved to CC BY-NC-ND 4.0 in August 2026; ND forbids derivatives, so do not
  update this submodule past the change point.
- Nebula additions to the native bridge: flow scale and the LSFG 3.1P performance model
  (`configureLsfgModel`, applied at the next context bootstrap), an instant generation pause
  (`setGenerationPaused`, used by the stream-menu toggle and thermal auto-off), a Vulkan feature
  probe (`probeDeviceCaps`) and a synthetic LSFG benchmark (`runBenchmark`) for the self-test.
- Multiplier: the pipeline has one generated-frame slot per real frame (`kGenerationCount = 1`), so
  only 2× is offered. 3× would need a second LSFG output, a third present slot and presenter pacing
  for three frames per input.
