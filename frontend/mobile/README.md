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

## 主要目录

- `src/components`：移动端通用 UI 组件与对话设置
- `src/screens`：对话、场景、专项训练、资产和个人中心页面
- `src/model`：跨页面状态与交互逻辑
- `src/data`：演示内容和训练数据
- `src/theme`：移动端设计令牌
- `assets/images/unispeaking`：移动端使用的品牌与角色图片副本
- `plugins`：重新生成原生工程时执行的 Expo 配置修正

## 已实现

- 自由对话完整大模块、教师/语速/水平设置及通话界面
- 场景练习与分步口语训练
- IELTS 全流程模拟、分析与报告
- 英文面试准备、模拟问答与报告
- 学习资产筛选、记录、详情、保存与删除
- 学习概览、会员方案、AI 助手及账号设置

应用包名：`com.unispeaking.mobile`
