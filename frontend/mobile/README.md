# UniSpeaking Mobile

UniSpeaking 的独立移动端项目，技术栈为 React Native、TypeScript、Expo SDK 57。

移动端在视觉语言与交互流程上参考已经定稿的 Web 端，但组件均在本项目中独立实现，不与 Web 端共用源码，也不需要修改 Web 项目。

## 新电脑首次运行

准备 Node.js、Android Studio、Android SDK 和一个 Android 虚拟设备或真机，然后在本目录执行：

```bash
npm install
npx expo prebuild --platform android
npm run android
```

`android` 和 `ios` 是可重新生成的本地目录，没有纳入版本控制。Expo 配置与必要的 Android 开发模式修正都保存在项目源码中。

如果只需要检查 TypeScript 和 Expo 配置，不启动模拟器：

```bash
npx tsc --noEmit
npx expo-doctor
```

## Umami 行为统计

移动端 Umami 默认关闭。生产构建使用与 Web 端相同的 Website ID，并通过 Umami Cloud
发送同名页面、模式选择、学习资产和训练生命周期事件：

```dotenv
EXPO_PUBLIC_UMAMI_ENABLED=true
EXPO_PUBLIC_UMAMI_ENDPOINT=https://cloud.umami.is/api/send
EXPO_PUBLIC_UMAMI_WEBSITE_ID=3ae2dee9-d585-43a9-93f3-fcafcd14b258
```

## Error monitoring

Client errors, API failures, device metadata, and RTC quality are always sent to the
authenticated UniSpeaking telemetry endpoint for Grafana/Loki. Sentry is enabled when
`EXPO_PUBLIC_SENTRY_DSN` is present.

For native symbol and JavaScript source-map uploads, also provide `SENTRY_ORG`,
`SENTRY_PROJECT`, and `SENTRY_AUTH_TOKEN` in the build environment.

Firebase Crashlytics is enabled only in a native development/release build that includes
`google-services.json` (Android) or `GoogleService-Info.plist` (iOS). Keep these files out
of Git. Their paths can be provided with `GOOGLE_SERVICES_JSON` and
`GOOGLE_SERVICE_INFO_PLIST` before running Expo prebuild/EAS Build.

已登录用户使用后端返回的用户 UUID 作为 Distinct ID，以关联 Web 和移动端会话。事件数据
不包含邮箱、昵称、口令、JWT、对话文本、音频、简历或岗位描述。生产统计主机名固定为
`unispeaking.qnsdk.com`，不要为移动端创建第二个 Website ID。

## 主要目录

- `src/components`：移动端通用 UI 组件与对话设置
- `src/screens`：对话、场景、专项训练、资产和个人中心页面
- `src/model`：跨页面状态与交互逻辑
- `src/infrastructure/analytics`：Umami 传输、页面归一化与有效时长计时
- `src/data`：演示内容和训练数据
- `src/theme`：移动端设计令牌
- `assets/images/unispeaking`：移动端使用的品牌与角色图片副本
- `plugins`：重新生成原生工程时执行的 Expo 配置修正

## 当前进度

| 模块 | 状态 |
| --- | --- |
| 自由对话 | 前端已定稿 |
| 场景广场 | 前端已定稿 |
| 学习资产 | 前端已定稿 |
| IELTS | 待继续开发与定稿 |
| 英文面试 | 待继续开发与定稿 |
| 个人主页 | 待继续开发与定稿 |

当前只有自由对话、场景广场和学习资产属于移动端定稿范围。IELTS、英文面试和个人主页
虽然已有部分页面、路由或演示数据，但都只是阶段性原型，不能视为完成版本，后续仍需
参考 Web 端交互继续开发。

应用包名：`com.unispeaking.mobile`
