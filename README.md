# 收入显示器 Android App

实时显示月薪换算后的每秒/分/时收入，并根据系统时间显示今日已赚金额。

## 功能

- 输入月薪，实时显示每秒/分钟/小时收入
- 今日已赚（按 9:00~18:00 工作制计算）
- 工作进度条
- 本次计时累计收入
- ~60fps 实时刷新

## 编译

### 方式一：Android Studio

1. 用 Android Studio 打开本文件夹
2. 等待 Gradle Sync 完成
3. `Build → Build APK(s)`

### 方式二：GitHub Actions（云端自动编译）

1. 在 GitHub 创建新仓库
2. 将本项目推送上去：
   ```bash
   git init
   git add .
   git commit -m "init"
   git remote add origin https://github.com/你的用户名/income-meter.git
   git push -u origin main
   ```
3. 打开 GitHub 仓库页面 → **Actions** 标签
4. 等待 workflow 运行完成（约 3~5 分钟）
5. 点击 workflow 运行记录 → **Artifacts** → 下载 `income-meter-debug-apk.zip`
6. 解压得到 `app-debug.apk`，发送到手机安装即可

> 注意：安装前需在手机上开启「允许安装未知来源应用」

## 技术栈

- Kotlin 1.9
- Android minSdk 24（Android 7.0+）
- Gradle 8.6 + AGP 8.3
- 无第三方依赖
