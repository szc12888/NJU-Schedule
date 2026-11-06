# NJU-Schedule · NJU 作业

在一个 Android App 里看南大的作业、截止时间和成绩。作业从 SElearning（Moodle）和智汇南雍（LMS）读取，可以按课程筛选，也可以把截止时间同步到系统日历。

## 开始使用

1. 在 **设置 → 统一身份登录** 完成学校认证。App 会依次连接各平台，首次同步完成后即可查看数据。
2. 到 **作业 → 课程管理** 勾选想看的课程。新发现的课程默认隐藏。
3. 在首页看待办或切换到月历。需要系统日历提醒时，在设置中授权并选择日历。

作业可以手动添加、通过 CSV 导入，也可以在本机标记完成。成绩页汇总教务课程成绩、GPA、排名百分比和体育教学成绩；带“≈”的名次是按百分比估算的。

## 从源码构建

支持 Android 8.0 及以上。构建需要 JDK 17+、Android SDK Platform 35 和 Build Tools 35.0.0。在 PowerShell 中设置 JAVA_HOME 和 ANDROID_HOME 后，从仓库根目录运行：

```powershell
& .\android\tools\build.ps1
```

APK 输出到 android/app/build/outputs/apk/debug/app-debug.apk。脚本会在本地生成调试签名。

网页解析测试使用合成数据，需要 Node.js：

```powershell
node --test android/tests/*.test.js
```

如需同时运行 JVM 测试，准备 org.json JAR 后运行：

```powershell
& .\android\tools\test.ps1 -JsonJar $env:NJU_JSON_JAR
```

## 数据与隐私

登录在学校网页内完成。作业和成绩缓存在手机的应用私有目录；启用日历同步后，作业提醒写入你选定的系统日历。退出全部登录会清除网页会话，已同步的本地缓存仍可查看。
