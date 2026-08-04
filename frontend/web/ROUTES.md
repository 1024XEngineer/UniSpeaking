# 前端分层路由约定

路由定义与解析统一位于 `src/router.js`。页面组件只负责渲染和调用路径生成器，后端合并时可以按以下模块边界接入数据接口。

## 公共与账户

| 路径 | 页面 |
| --- | --- |
| `/` | 启动页 |
| `/login`、`/signup` | 登录与注册 |
| `/level`、`/teacher` | 首次设置 |
| `/conversation` | 自由对话 |
| `/scenes` | 场景广场 |
| `/assets` | 场景学习资产 |
| `/profile` | 个人主页 |
| `/membership` | 会员中心 |
| `/settings` | 设置 |

场景训练使用 `/scenes/training` 与 `/scenes/training/result`。旧地址 `/training`、`/result` 会自动兼容并替换成新地址。

## IELTS

| 路径 | 页面 |
| --- | --- |
| `/ielts` | IELTS 训练中心 |
| `/ielts/part1`、`/ielts/part2`、`/ielts/part3` | 对应题库 |
| `/ielts/:part/:topic/setup` | 设备与考官设置 |
| `/ielts/:part/:topic/session` | 实时练习 |
| `/ielts/:part/:topic/analysis` | 分析中 |
| `/ielts/:part/:topic/report` | 练习报告 |
| `/ielts/mock/:screen` | 全真模考流程 |
| `/ielts/assets` | 学习资产概览 |
| `/ielts/assets/history` | 训练记录 |
| `/ielts/assets/trends` | 能力趋势 |

## 英文面试

| 路径 | 页面 |
| --- | --- |
| `/interview` | 材料与时长设置 |
| `/interview/preparing` | 面试准备 |
| `/interview/live` | 实时面试 |
| `/interview/finalizing` | 报告分析中 |
| `/interview/report` | 完整报告 |
| `/interview/report/partial` | 部分结果 |
| `/interview/report/failed` | 报告失败 |
| `/interview/assets` | 学习资产概览 |
| `/interview/assets/history` | 面试记录 |
| `/interview/assets/trends` | 能力趋势 |
| `/interview/assets/:recordId` | 单次面试报告 |

## 合并约定

- 路由参数只使用稳定 ID，不把页面标题作为接口主键。
- 页面刷新、前进后退和直接访问深层地址均由 `resolveRoute` 解析。
- 未知的 IELTS/面试子路径回到各自模块首页；完全未知地址回到启动页。
- 旧路径 `/training`、`/result`、`/ielts-assets` 保持兼容。
- 后端接口路径不需要与页面路由相同；建议分别使用 `/api/ielts/*`、`/api/interviews/*`、`/api/assets/*`。
- 合并部署时，除 `/api/*` 和静态资源外，所有前端 `GET` 路径都需要回退到 `index.html`，否则刷新深层地址会得到服务器 404。
- 合并前可运行 `npm run check:routes` 检查路由契约，再运行 `npm run build` 检查前端构建。
