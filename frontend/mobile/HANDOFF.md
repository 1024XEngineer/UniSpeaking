# UniSpeaking 移动端前端交接说明

## 1. 项目定位

这是 UniSpeaking 的独立 Android 移动端前端，使用 React Native、TypeScript、Expo SDK 57 和 Expo Router。

- 应用包名：`com.unispeaking.mobile`
- 当前主要目标：先完成高保真前端、路由和可交互原型，再接入后端
- 视觉原则：参考 Web 端的信息结构、交互逻辑、图标与组件语言，但针对手机屏幕重新排版，不是机械缩放 Web 页面
- Web 端参考源码：`frontend/web`
- 移动端源码：`frontend/mobile`

## 2. 当前产品逻辑

### 公共与首次使用流程

1. 欢迎页：登录、注册两个主入口。
2. 登录/注册页：严格分层路由。
3. 首次登录轻问询：
   - 第一步选择英语难度。
   - 第二步选择 AI 老师，移动端使用循环圆弧头像选择器。
4. 完成后进入四个主 Tab：对话、场景、资产、我的。

### 自由对话

- 首页展示当前老师、对话设置和开始对话按钮。
- 对话页支持监听态与字幕态上下切换。
- 通话控件、音波纹、头像和字幕结构参考 Web 端。
- 场景训练中的最终“说”环节复用该字幕模式，但场景对话默认始终显示字幕，不提供字幕开关。

### 场景训练

- 场景广场包含“创建专属场景”和每日推荐。
- 专属场景文本是未来后端可动态替换的数据入口。
- 推荐场景进入确认弹窗，再进入“学、读、说”三步训练。
- “学”阶段把单词与词组作为分开的功能。
- “读”阶段包含示范播放、录音与逐句评分弹窗。
- “说”阶段完成后应弹出五维评分结果，不跳转到独立结果页。

### 学习资产（务必遵守）

- 默认学习资产只展示“普通场景学习”，不能把 IELTS 和英文面试混进默认记录列表。
- 普通场景资产详情可以查看：
  - 最近一次聊天记录；
  - 每句话的评价与总结；
  - 场景中积累的单词、词组等表达。
- IELTS 和英文面试是独立入口、独立路由、独立资产界面。
- IELTS 与英文面试后端尚未开发。目前相关页面只能作为前端原型/占位体验，不能伪装成已接入真实数据。
- 数据结构集中在 `src/data/learningAssets.ts`，接后端时优先替换这一层，避免把请求散落在 UI 中。

## 3. 主要代码结构

```text
src/app/                 Expo Router 路由
src/components/          通用 UI、设备框、教师选择、对话设置
src/data/                老师、场景、学习资产等演示数据
src/model/AppModel.tsx   跨页面状态和业务动作
src/navigation/routes.ts 路由常量与生成函数
src/screens/             页面与主要业务组件
src/theme/               颜色、排版和设计令牌
assets/                  品牌、人物与图标资源
android/                 Android 原生工程
plugins/                 Expo 原生配置修正
```

关键页面：

- `src/screens/AuthScreens.tsx`
- `src/screens/ConversationScreen.tsx`
- `src/screens/ScenesScreen.tsx`
- `src/screens/AssetsScreen.tsx`
- `src/screens/SpecialtyAssetsScreen.tsx`
- `src/screens/SpecialtyFlows.tsx`
- `src/screens/ProfileScreen.tsx`
- `src/components/ui.tsx`
- `src/components/TeacherSwipeStack.tsx`

学习资产分层路由：

```text
/(app)/(tabs)/learning
/(app)/learning/scenes/[id]
/(app)/learning/ielts
/(app)/learning/ielts/history
/(app)/learning/ielts/trends
/(app)/learning/interview
/(app)/learning/interview/history
/(app)/learning/interview/trends
/(app)/learning/interview/[id]
```

## 4. 当前设计基线

- 页面底色：白色。
- 模块：纯白背景、细边框和非常轻的阴影；不要使用深灰阴影。
- 当前模块阴影透明度约 `4%`，Android `elevation` 通常为 `2`。
- 当前字重层级：
  - 主标题与重点标题：`600`
  - 按钮、标签、强调文字：`500`
  - 普通正文和辅助文字：`300`
- 对话首页“开始对话”按钮：黑底白字。
- 不使用此前尝试过的蓝色导航/设置配色。
- 底部导航和专项训练图标尽量复用 Web 端的同风格图标。
- 用户会在实体机上自行视觉审查；除非明确要求，不要主动生成截图审查。

## 5. 安装与运行

### 安装依赖

```bash
npm install
```

### 类型检查

```bash
npx tsc --noEmit
```

### 浏览器预览

```bash
npm run web -- --port 8081
```

### Android 实体机（用户明确要求不要使用虚拟机）

1. 打开 Android 手机的开发者选项与 USB 调试。
2. 用 USB 连接并确认授权：

```bash
$ANDROID_HOME/platform-tools/adb devices -l
```

3. 仅构建实体机需要的 ARM64 架构并安装：

```bash
cd android
./gradlew app:installDebug -PreactNativeArchitectures=arm64-v8a
cd ..
```

4. 启动 Metro：

```bash
npm run start -- --dev-client --port 8081
```

5. 新终端建立 USB 转发并启动 App：

```bash
$ANDROID_HOME/platform-tools/adb reverse tcp:8081 tcp:8081
$ANDROID_HOME/platform-tools/adb shell am start -n com.unispeaking.mobile/.MainActivity
```

最近使用的实体机型号为小米 `M2102K1C`，但不要把设备序列号写死到代码或脚本中。

## 6. 验证状态与已知问题

- 最近一次 `npx tsc --noEmit` 通过。
- Android ARM64 Debug 构建已成功安装到实体机。
- Web 导出曾通过：`npx expo export --platform web`。
- 全量 ESLint 仍有一些较早存在的动画 Hook 告警，主要分布在：
  - `ConversationSettings.tsx`
  - `TeacherSwipeStack.tsx`
  - `AuthScreens.tsx`
  - `ScenesScreen.tsx`
- IELTS 和英文面试目前没有后端，相关数据均为演示数据。
- 当前工程已纳入 UniSpeaking 单仓库，位于 `frontend/mobile`。

## 7. 接手后的优先顺序

1. 先完整阅读 Web 端学习资产、普通场景详情、IELTS 与面试模块代码，确认数据边界后再改 UI。
2. 校正默认学习资产：只展示普通场景记录。
3. 完成普通场景资产详情中的聊天记录、逐句评价与总结结构。
4. 保持 IELTS、面试入口独立，并明确使用占位数据。
5. 将 `src/data` 中的演示数据逐步替换为 service/repository 层接口。
6. 每次改动至少运行 `npx tsc --noEmit`；用户要求时再部署到实体机。

## 8. 协作约定

- 修改前先检查 Web 端逻辑，不清楚的产品规则再询问用户。
- 不要擅自启动 Android 模拟器。
- 不要把 Web 页面按比例硬塞进手机；复用结构、图标、动效和视觉语言即可。
- 不要把 IELTS/面试的未开发后端状态误报为已完成。
- 保留用户已有修改，不要进行破坏性 Git 操作。
