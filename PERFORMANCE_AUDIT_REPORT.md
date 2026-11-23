# PenOS 5.5 渲染与性能全链路审计报告

> 审计范围：`MainActivity`、双引擎桌面（`NativeTurboDesktopView` + `DesktopScreen`）、`PenOsViewModel` 状态管线、`PenPreferences` 持久化、`InstalledAppRepository`、`DynamicWallpaperRenderer`、`Type.kt` 字体系统、`PenMotion.kt` 动效修饰符以及全屏覆盖层（`AppleTvDrawerScreen`、`StandByScreen`、`PomodoroFocusScreen`、`ControlCenterOverlay`）。
> 
> 核心结论：应用即便在**现代旗舰手机**上仍然明显卡顿、掉帧、甚至“划不动”，并非单点问题，而是由 **4 个 P0 级渲染/主线程/手势架构缺陷 + 6 个 P1 级高频开销与主线程 I/O 缺陷 + 2 个 P2 级稳定性与交互设计缺陷** 叠加导致的。

---

## 一、P0 级严重缺陷（直接导致现代手机卡顿、掉帧与“划不动”）

### BUG-01：每 15 秒整棵原生桌面卡片树被销毁重建 + 滚动条归零
- **位置**：`app/src/main/java/com/example/ui/desktop/NativeTurboDesktopView.kt`（L506、L551）及 `app/src/main/java/com/example/ui/PenOsViewModel.kt`（L604-615）
- **根因**：
  1. `NativeTurboDesktopView.bindState()` 生成结构去重签名 `sig` 时，把动态波动的 `state.clock.availRamMb` 拼接进了结构签名。
  2. `PenOsViewModel` 每 15 秒刷新一次 `_slowTelemetry`（携带实时 `availRamMb`），Android 设备可用内存几乎每 15 秒都在变化。
  3. 签名失配后直接触发 `cardRow.removeAllViews()`，把整个桌面所有卡片 `View` 销毁重建、重新测量排版，并把 `HorizontalScrollView` 滚动位置拉回原点，用户滑动时直接卡死或弹回。
- **修复方案**：
  - 将 `availRamMb` 从 `sig` 中彻底移除，仅在卡片列表/内容实质变化时才重建；
  - 新增 `penStatusRamView` 引用，通过 `updateClockTick(tick)` 就地更新文字（零 View 销毁、零重排）；
  - 重建前后保存并恢复 `deckScroll.scrollX`。

### BUG-02：根层 `BoxWithConstraints`（SubcomposeLayout）+ 手机默认开启 12dp 阴影裁剪壳包裹 `AndroidView`
- **位置**：`app/src/main/java/com/example/MainActivity.kt`（L104、L409-717、L816-862）、`app/src/main/java/com/example/data/prefs/PenPreferences.kt`（L140、L489）
- **根因**：
  1. `MainActivity` 在整棵树最顶层（`PenOsTheme` 之外）直接收集庞大的单体 `uiState`，任何细微状态变化都会触发根重组，并伴随 60+ 个未记忆化的内联 Lambda 重建。
  2. `PenDisplayAdaptiveShell` 使用 `BoxWithConstraints`（`SubcomposeLayout`），每次根重组都要重新执行子组合测量。
  3. `penFramePreviewEnabled` 默认值为 `true`，导致所有手机用户打开后，整个 App（含原生 `AndroidView`）都被硬塞进一个带 `shadowElevation = 12.dp` + `RoundedCornerShape(26.dp)` 的裁剪壳里。Android RenderThread 对包含原生 `AndroidView` 的圆角阴影裁剪容器每帧都要做离屏阴影与路径裁剪，造成现代手机掉帧，且 640×178dp 的外壳在手机上导致顶栏文字挤压、视觉局促。
- **修复方案**：
  - 将 `PenDisplayAdaptiveShell` 改为轻量 `Box` + `Modifier.onSizeChanged`（彻底消除 `SubcomposeLayout`）；
  - 将 `penFramePreviewEnabled` 默认值改为 `false`，手机端默认进入全屏自适应工业级横屏界面（密室内仍保留随时切换 640×178dp 词典笔模拟框的开关）；
  - 移除模拟框的 `shadowElevation = 12.dp`，改用零离屏消耗的 1dp 精细描边与纯色底框。

### BUG-03：主线程同步 Binder IPC + 每秒在主线程创建 4 个 `SimpleDateFormat`（ICU 表分配风暴）
- **位置**：`app/src/main/java/com/example/ui/PenOsViewModel.kt`（L95、L103、L600-673）
- **根因**：
  1. `PenOsViewModel` 字段初始化阶段（在主线程构造函数中）同步调用 `readTelemetry(includeHeavyStats = true)` 与 `readDeviceControl()`，阻塞执行 5 次跨进程 Binder IPC（`registerReceiver(BATTERY_CHANGED)`、`ConnectivityManager`、`ActivityManager.getMemoryInfo`、`Settings.System`、`AudioManager`）。
  2. `startLowPowerClockAndLoops()` 运行在 `viewModelScope` 默认调度器 `Dispatchers.Main.immediate`，**每秒**在主线程上实例化 4 个 `SimpleDateFormat(..., Locale.CHINA)` 对象（每次都加载 ICU 本地化表并产生大量短命对象触发 GC 卡顿），同时每 15 秒在主线程执行 3 次同步 Binder IPC。
  3. `_slowTelemetry` 把高频抖动的 `availRamMb` 合并进全局 `uiState`，引起无谓的全局流发射。
- **修复方案**：
  - 构造函数仅使用零 IPC 轻量默认值，首帧之后立即在 `Dispatchers.IO` 异步填充真实遥测与设备控制值；
  - 将时钟与遥测轮询协程彻底切至 `Dispatchers.Default` / `Dispatchers.IO`，并复用协程内线程隔离的 `SimpleDateFormat` 实例（零主线程分配、零主线程 IPC）；
  - `_slowTelemetry` 仅在分钟、日期、电量、充电状态或 Wi-Fi 变化时才发射；`_memoryHealth` 仅在可用内存波动 ≥ 24MB 或降级状态切换时才更新。

### BUG-04：手势事件链断裂 + 容器手势抢占 + 宽屏无横向溢出感
- **位置**：
  - `app/src/main/java/com/example/ui/desktop/NativeTurboDesktopView.kt`（L211-243）
  - `app/src/main/java/com/example/ui/drawer/AppleTvDrawerScreen.kt`（L106）
  - `app/src/main/java/com/example/ui/standby/StandByScreen.kt`（L128）
  - `app/src/main/java/com/example/ui/desktop/PomodoroFocusScreen.kt`（L88）
- **根因**：
  1. `DirectionLockSwipeLayout.onTouchEvent()` 在空白边距/卡片间隙收到 `ACTION_DOWN` 时，因为 `verticalLocked` 尚未置为 `true`，直接返回 `super.onTouchEvent(ev)`（即 `false`）。Android 事件分发机制看到 `ACTION_DOWN` 返回 `false` 后，直接丢弃后续所有 `ACTION_MOVE` / `ACTION_UP`，导致在非按钮区域上下滑彻底失灵！
  2. `AppleTvDrawerScreen`、`StandByScreen`、`PomodoroFocusScreen` 仍使用会立刻抢占指针的 `detectVerticalDragGestures`，与内部 `LazyRow` / 横向滑动产生手势打架。
  3. 在 800-920dp 宽屏手机上，默认 3 张原生卡片总宽仅 ~556dp，完全不超出屏幕宽度，用户横划时纹丝不动，体感极像“卡住了划不动”。
- **修复方案**：
  - 修复 `DirectionLockSwipeLayout.onTouchEvent()`：在 `ACTION_DOWN` 时明确返回 `true` 接管手势序列，并在水平/垂直锁定后给出明确触觉与视觉反馈；
  - 将 `AppleTvDrawerScreen`、`StandByScreen`、`PomodoroFocusScreen` 的纵向手势统一重构为非抢占式方向锁 `awaitEachGesture`；
  - 优化原生卡片宽度比例，并新增“更多组件 / 抽屉探索”尾卡（`native_card_more_widgets`），让默认 3 张核心卡片 + 尾卡在宽屏手机与 640dp 词典笔上都具备流畅顺滑的横向滑动余量与视觉节奏。

---

## 二、P1 级高频性能与主线程 I/O 缺陷

### BUG-05：DataStore 重复主线程反序列化 + `PenKeepAliveService` 重复 Binder IPC + 启动 4 次串行写盘
- **位置**：`app/src/main/java/com/example/data/prefs/PenPreferences.kt`（L427-520）、`app/src/main/java/com/example/ui/PenOsViewModel.kt`（L251、L307、L316、L571-575、L1770-1774）
- **根因**：
  1. `configFlow` 缺少 `.flowOn(Dispatchers.IO)` 与 `.distinctUntilChanged()`，且被 `PenOsViewModel` 同时收集两次，每次配置写入都会在主线程重复解析两遍 JSON/CSV。
  2. `observePreferencesForSystemGuards()` 未对 `keepAliveEnabled` 去重，导致**任意无关配置修改**（切换主题、同步时间戳等）都会在主线程重复调用 `PenKeepAliveService.start/stop()` 触发 AMS Binder IPC。
  3. 首次启动扫描应用后，循环调用 4 次 `prefs.togglePinnedPackage()` 触发 4 次 DataStore 写盘与 8 次全量配置重组。
  4. 多个 `combine` 链用无类型 `listOf(...)` 传递状态，缺少 `.distinctUntilChanged()`。
- **修复方案**：
  - `configFlow` 增加 `.flowOn(Dispatchers.IO).distinctUntilChanged()`；
  - `PenOsViewModel` 内将配置共享为单一 `configState`，并对 `keepAliveEnabled` 增加 `lastKeepAliveEnabled` 边沿检测去重；
  - 新增 `prefs.setPinnedPackages(List<String>)` 实现 1 次批量写盘；
  - 将无类型 `listOf(...)` 替换为强类型私有数据类 + `.distinctUntilChanged()`。

### BUG-06：已废弃的 `Modifier.composed {}` 强制子组合 + 零过绘制模式下仍创建持久协程
- **位置**：`app/src/main/java/com/example/ui/theme/PenMotion.kt`（L162-232）
- **根因**：两个 `springPressScale` 重载均使用已废弃的 `Modifier.composed {}`（Compose 1.6+ 明确标注为性能反模式，会导致每个卡片在布局阶段额外执行子组合）；且单参重载忽略了 `LocalZeroOverdraw.current`，在零动效模式下依然为每张卡片挂载一个常驻 `while (true)` 的 `pointerInput` 协程。
- **修复方案**：改写为直接 `@Composable fun Modifier.springPressScale(...)`，当 `zeroOverdraw || tier == PenMotionTier.NONE` 时直接返回 `this`（零协程、零状态分配、零图形层）。

### BUG-07：主线程同步无采样 `BitmapFactory.decodeFile()` 与未记忆化 `asImageBitmap()`
- **位置**：
  - `app/src/main/java/com/example/widgets/BuiltInWidgets.kt`（L118、L159）
  - `app/src/main/java/com/example/ui/desktop/DesktopScreen.kt`（L451）
  - `app/src/main/java/com/example/system/DynamicWallpaperRenderer.kt`（L195）
- **根因**：自定义壁纸与自定义图标直接在 Composable 主线程调用 `BitmapFactory.decodeFile()`（无 `inSampleSize` 降采样，一张 4K 照片会直接卡死主线程 150ms+ 并吃掉 48MB 内存），且 `appInfo.iconBitmap.asImageBitmap()` 未用 `remember` 缓存包装。
- **修复方案**：在 `BitmapDecodeCache` 中新增 `peekMemoryCache` 与基于 `produceState(..., Dispatchers.IO)` 的 `@Composable fun rememberSampledBitmap(path, maxWidth, maxHeight)`，将所有磁盘解码彻底移出主线程。

### BUG-08：字体资源重复注册 + 中文默认字体触发 Minikin 逐字回退 + 主线程 `File.exists()`
- **位置**：`app/src/main/java/com/example/ui/theme/Type.kt`（L17-60、L95、L160-165、L228）
- **根因**：
  1. 6 个静态 `.ttf` 文件被重复映射为 25 个 `Font(...)` 条目，加重资源解析和内存占用。
  2. 默认 `body` 槽位使用纯拉丁字体 `Inter`，在全中文界面上每段文字都会先在 `Inter` 中查字形失败，再回退到系统 CJK 字体，导致文本测量耗时翻倍。
  3. `PenFontCatalog.resolve()` 在组合期间对非外部字体也调用了同步 `File(externalPath).exists()`。
  4. 无条件开启 `fontFeatureSettings = "tnum"` 在旧版 Android 上禁用 HarfBuzz 快速排版路径。
- **修复方案**：精简重复 `Font` 条目；默认 `body` 槽位改用 `FAMILY_SYSTEM_SANS` 实现原生零回退中文渲染（时钟/数字指标仍保留 Outfit / JetBrains Mono 精致质感）；仅在 `familyKey == FAMILY_EXTERNAL` 时访问文件系统；在 API < 26 上跳过 `tnum` OpenType 特性以走快速路径。

### BUG-09：`StandByScreen` 与 `PomodoroFocusScreen` 每秒全屏重组
- **位置**：`app/src/main/java/com/example/ui/standby/StandByScreen.kt`（L111）、`app/src/main/java/com/example/ui/desktop/PomodoroFocusScreen.kt`（L81）
- **根因**：两者在最外层 Composable 直接收集 `LocalClockTickFlow`，导致每秒时钟跳动时重绘整个全屏背景、遮罩、控制栏与右侧大组件。
- **修复方案**：将 `LocalClockTickFlow` 收集下沉到极小的纯时钟叶子 Composable，全屏背景与右侧组件实现 0 重组。

### BUG-10：冷启动全量解码机内 100~250 个 App 图标并常驻 `uiState`
- **位置**：`app/src/main/java/com/example/system/InstalledAppRepository.kt`（L60-64）
- **根因**：每次启动扫描应用时一次性把手机上所有 App 图标解码为 72×72 `ARGB_8888` `Bitmap` 并全部塞入 `uiState.installedApps`（手机上有 200 个 App 时耗时数百毫秒且常驻数 MB 堆内存，并使 `InstalledAppInfo` 失去 Compose 稳定性）。
- **修复方案**：从 `InstalledAppInfo` 中移除 `iconBitmap`，引入按需加载的 `AppIconCache`（48 槽 `LruCache<String, ImageBitmap>` + `Dispatchers.IO` 异步解码），仅对屏幕可见的应用图标按需解码缓存。

---

## 三、P2 级稳定性与视觉设计优化

### BUG-11：手机端默认体验与原生引擎视觉品质升级
- **问题**：
  1. 手机端默认被锁死在 640×178dp 黑色模拟框内，顶栏文字拥挤；
  2. 原生极速引擎左侧 Dock 使用单字纯文本按钮（“词/实/控/原/息/昼”），缺少矢量图标、按压态与视觉辨识度；
  3. 原生卡片缺少精细的顶部色带层级、统一按压反馈（`StateListDrawable`）以及快捷探索尾卡。
- **优化方案**：
  - 手机端默认全屏自适应布局，充分利用现代横屏/宽屏空间；
  - 新增 6 枚 24dp 矢量图标资源（`ic_dock_vocab`、`ic_dock_lab`、`ic_dock_control`、`ic_dock_widgets`、`ic_dock_standby`、`ic_dock_theme`），为原生 Dock 打造高精度圆角图标按钮与即时按压态；
  - 为原生卡片升级瑞士精密仪表风格（高对比排版层级、微色带指示条、按压态高亮边框、细腻留白与“探索更多组件”尾卡）。

### BUG-12：Room 实体与系统诊断模型缺少 `@Immutable` 稳定性注解
- **位置**：`app/src/main/java/com/example/data/local/Entities.kt`、`app/src/main/java/com/example/system/SystemServices.kt`、`app/src/main/java/com/example/system/InstalledAppRepository.kt`
- **修复方案**：为全部 Room 实体、`SystemDiagnostics` 及 `InstalledAppInfo` 补齐 `@Immutable` 注解，全面解锁 Compose 强跳过（Strong Skipping）与零冗余重组。

---

## 四、修复与验证清单

- [x] BUG-01：移除 `availRamMb` 结构签名，就地刷新 RAM 文本并保持 `scrollX`
- [x] BUG-02：移除根层 `BoxWithConstraints` 与 12dp 阴影裁剪壳，手机默认全屏自适应
- [x] BUG-03：消除主线程同步 Binder IPC 与每秒 `SimpleDateFormat` 分配
- [x] BUG-04：修复 `DirectionLockSwipeLayout` 事件链断裂与覆盖层手势冲突
- [x] BUG-05：DataStore IO 调度去重 + KeepAlive 边沿触发 + 批量固定应用
- [x] BUG-06：消除 `Modifier.composed` 与零过绘制下的冗余协程
- [x] BUG-07：全部壁纸与自定义图标改为 `Dispatchers.IO` 异步降采样缓存
- [x] BUG-08：精简字体句柄 + 中文原生无回退 + 移除主线程 `File.exists()`
- [x] BUG-09：隔离 `StandByScreen` / `PomodoroFocusScreen` 每秒时钟重组
- [x] BUG-10：移除冷启动全量图标解码，落地 `AppIconCache` 按需异步缓存
- [x] BUG-11：升级原生 Dock 矢量图标、按压态与卡片精密视觉层次
- [x] BUG-12：补齐全链路 `@Immutable` 注解
- [x] 验证：`compile_applet` 通过 + `gradle :app:testDebugUnitTest` 全量通过 + `gradle :app:syncRootApk` 已更新 `PenOS-Launcher.apk`
