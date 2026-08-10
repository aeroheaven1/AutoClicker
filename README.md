# 连点器 (Auto Clicker)

基于 **Material Design 3 (2024)** 的 Android 自动化连点器应用，支持 **Shizuku** 和 **Root** 两种权限方式。

## ✨ 功能

| 功能 | 说明 |
|------|------|
| 🖱️ **点击模拟** | 通过 `input tap` 实现精确的屏幕点击 |
| 👆 **滑动模拟** | 支持自定义起点/终点/时长的滑动操作 |
| 🎬 **操作录制** | 手动编排或真实触摸录制完整操作序列 |
| 🔄 **循环执行** | 支持设置重复次数或无限循环 |
| 🎨 **Material You** | 动态取色，跟随系统主题 |
| 🔒 **双权限支持** | Shizuku + Root，自动选择最佳方式 |

## 📱 权限要求

### Shizuku（推荐）
无需 Root！下载 [Shizuku](https://shizuku.rikka.app/) 并按照指引激活即可。

### Root
设备需通过 Magisk / KernelSU / APatch 等方式获取 Root 权限。

## 🏗️ 项目结构

```
AutoClicker/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/autoclicker/app/
│       │   ├── MainActivity.kt          # 主 Activity
│       │   ├── AutoClickerApp.kt        # Application 类
│       │   ├── data/
│       │   │   ├── Script.kt            # 脚本数据模型
│       │   │   └── ScriptRepository.kt  # 脚本存储
│       │   ├── service/
│       │   │   ├── ICommandRunner.kt    # 命令执行接口
│       │   │   ├── RootRunner.kt        # Root 权限执行器
│       │   │   ├── ShizukuRunner.kt     # Shizuku 权限执行器
│       │   │   ├── ClickService.kt      # 自动化执行服务
│       │   │   └── TouchEventRecorder.kt # 触摸事件录制
│       │   └── ui/
│       │       ├── theme/               # MD3 主题
│       │       ├── screens/             # 页面
│       │       │   ├── HomeScreen.kt    # 主页
│       │       │   ├── RecordScreen.kt  # 录制页
│       │       │   └── SettingsScreen.kt # 设置页
│       │       └── components/          # 组件
│       │           ├── ScriptList.kt    # 脚本列表
│       │           └── FloatingControl.kt # 悬浮窗
│       └── res/                         # 资源
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## 🔧 构建

用 Android Studio 打开项目目录，Sync Gradle 后即可构建。

```bash
# 命令行构建
./gradlew assembleDebug
```

### 依赖

- Kotlin 1.9.22
- Jetpack Compose + Material 3
- Shizuku API 13.1.5
- AndroidX DataStore
- Gson

## 📝 使用

1. 确保 Shizuku 已运行或设备已 Root
2. 打开应用，等待权限检测完成
3. **快速操作**: 点击「快速点击/滑动」立即执行
4. **录制脚本**: 点击「录制脚本」创建自动化流程
5. **执行脚本**: 在列表中点击播放按钮运行

### 获取坐标

在系统「开发者选项」中开启「指针位置」，屏幕顶部会显示实时坐标。

## ⚠️ 注意

- 本工具仅供学习和辅助用途，请勿用于违规操作
- 部分应用可能检测并阻止自动化操作
- Android 14+ 需要前台服务通知
