# 光匣

一款完全离线的 Android 胶片摄影助手，包含摄像头测光、闪光指数计算和胶卷备忘录。

## 功能

- 后置摄像头反射式测光：由系统管理多摄选择，支持全局平均 / 中心点、光圈优先 / 快门优先、曝光锁定、±3 EV 设备校准。
- 闪光联动：输入全功率 GN 与拍摄 ISO 后，调节光圈或距离会自动给出闪光功率；手动调节功率时自动匹配光圈。支持 1/1～1/256、0.1/0.3 EV 步长以及米制/英制整体切换。
- 胶卷备忘录：多相机卡片、当前胶卷、装卸日期、历史记录与备注。
- 所有记录只存储在本机；应用不申请网络权限。

## 构建与安装

环境要求：JDK 17 或更高版本、Android SDK 35。

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`。

当前版本：1.2.11（versionCode 19）。仪器测试需要连接 Android 设备后执行 `./gradlew connectedDebugAndroidTest`。

## 测光校准

手机摄像头并非计量级测光表。首次使用时，建议对均匀照明的灰卡分别使用可信测光表和本应用读数，再在测光页调整设备 EV 校准。中心点模式使用画面中央约 15% 的自动曝光区域；不同手机厂商的 AE 实现可能产生轻微差异。

## 数据与兼容性

- 最低 Android 6.0（API 23），目标 API 35；可分发 APK 包含 v1/v2/v3 签名，构建目录另生成供增量安装使用的 v4 `.idsig`。
- 数据使用 Room 和 DataStore 保存在应用私有目录。
- 删除相机会级联删除该相机的全部胶卷记录，界面会在操作前确认。
