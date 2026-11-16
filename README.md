# 桌面清单 Android 客户端源码

此仓库包含 Android 应用生产源码、资源与 Gradle 构建配置。签名密钥、本地 SDK 配置、APK/AAB、测试夹具及构建输出均不包含在内。

## 构建

使用 Android Studio 打开仓库，或在 Windows PowerShell 运行：

```powershell
.\gradlew.bat assembleDebug
```

发布版签名需要你自己的签名材料；签名文件不会提交到仓库。