# 研途 AI 前端

依据本次会话的 MySQL 架构设计与原始课程要求实现。Vue 3 + TypeScript（strict）+ Vite + Pinia + Vue Router，ECharts 负责统计图，所有模块按路由懒加载。前端新增在 `frontend/`；现有 Java、MySQL、Redis 与 AI 适配代码没有修改。

## 在 VS Code 中启动

在项目根目录打开终端：

```powershell
cd frontend
npm ci
npm run dev
```

访问 `http://127.0.0.1:5173`。需要 Node.js 22.12 以上（当前机器安装的 Node 满足要求）。`package-lock.json` 固定实际依赖版本；不要在前端环境变量放 AI 密钥或数据库密码。项目根目录提供 `.vscode/tasks.json`，通过“终端 → 运行任务 → 前端：启动开发服务”启动；推荐扩展为 Vue - Official 与 Prettier。

```powershell
npm run typecheck
npm run format:check
npm test
npm run build
npm run preview
```

生产静态文件在 `dist/`。浏览器端所有页面可编辑源文件在 `src/views/`，公共样式在 `src/styles.css`。

## 模式与真实能力边界

默认进入**演示模式**，页顶持续标识演示数据。示例院校名称、招生指标与教材片段仅用于验证前端流程，不能作为实际招生或考试依据。演示 AI 为明确标注的固定模板；没有调用真实模型、OCR 或评分服务。

演示模式支持：课程完成、每日打卡、25 分钟专注计时、筛选与院校对比、题目作答与错题自动收录、计划生成与确认/替换、任务勾选与时长调整、模考截止计时/自动交卷/客观题解析、复试问答与本地介绍模板、事项关注、行为驱动的仪表盘和周总结。完成课程使用课程**预计时长**，专注计时使用已完成轮次，不将计划安排当作实际学习时长。示例模考仅以正确比例折算100分，不代表考研估分；未构造排名或录取概率。

本地示例学习行为保存在 `localStorage:kaoyan-demo-v1`，页面底部可清空；模式在 `sessionStorage`。密码、身份凭据、聊天记录、图片和语音不写本地存储。用户状态仅存在内存，刷新真实模式后重新登录；后端补全安全的 session/me 契约后可增加恢复，当前未杜撰该接口。

点击右上角模式按钮进入**真实接口模式**，或复制 `.env.example` 为 `.env.local` 设置 `VITE_APP_MODE=live`。前端统一通过 `/api` 调用已有 Controller，开发代理默认 `127.0.0.1:8080`。真实模式不会偷偷回退为演示数据。当前多数 Service 返回 null，`/user/login` 返回空 Result，因此空成功响应被识别为“后端尚未返回业务数据”。页面加载、错误、空数据、取消与重试有独立状态。

`api/client.ts` 使用 fetch、Cookie credentials、超时、请求取消和 Result 解包。仅当浏览器存在 `XSRF-TOKEN` Cookie 时携带 `X-XSRF-TOKEN`；当前 Java 后端尚没有认证/CSRF协议，应先按架构实现并统一 token 名称，不能把前端校验当作服务端鉴权。后端当前用户 GET 返回实体且包含 password，前端没有调用该不安全响应；建议后端改为安全 UserVO。Long 用户 ID 应按字符串输出，前端拒绝超出安全整数范围的数值 ID。

语音采用浏览器 SpeechRecognition / SpeechSynthesis：用户点击才开始采集；浏览器可能访问服务商网络，不支持或权限失败可使用文字；停止按钮与页面卸载均停止采集及播放。没有伪装成自建 ASR/TTS。图片只做本地预览，支持 JPG/PNG/WebP 最大5MB；没有 OCR 接口时明确要求人工录入并确认题目文字。

## 模块覆盖

| 原模块                 | 路由                    | 页面及交互                                                                                     |
| ---------------------- | ----------------------- | ---------------------------------------------------------------------------------------------- |
| M01 数字人陪伴督学     | `/companion`            | SVG 动作形象、三种模式、文字/语音对话、提醒、打卡、专注计时                                    |
| M02 智能择校与专业分析 | `/schools`              | 专业分数地区梯度筛选、招生指标、最多3校对比、风险提示；录取概率明确待验证模型                  |
| M03 公共课辅导         | `/public-courses`       | 政治/英语/数学，词汇长难句作文、大纲带背与时政核验、数学推导题型、完成进度                     |
| M04 专业课支持         | `/professional-courses` | 计算机/教育学/经管/医学，知识体系、参考方向、重难点与真题思路                                  |
| M05 AI 答疑            | `/qa`                   | 文字提问、本地拍照图片预览/人工文字确认、引导式对话、追问、朗读                                |
| M06 刷题错题专项       | `/practice`             | 每日/周测/专项/错题入口，科目知识点筛选、客观作答、解析、自动收录错题、同学科练习              |
| M07 个性化规划         | `/planning`             | 日周月候选计划、确认生效与替换、完成进度、超期标记、未完成任务时长调整                         |
| M08 模考估分           | `/exams`                | 科目试卷、限时答题卡、刷新恢复、超时交卷、客观评分与答卷解析、薄弱科目反馈；真实排名估分待接入 |
| M09 复试训练           | `/interview`            | 专业问答、英语口语、压力模式、语音回答、自我介绍模板、礼仪流程                                 |
| M10 学习数据           | `/dashboard`            | 时长/正确率/打卡/专注占比、七日曲线、科目练习雷达、任务、周总结                                |
| M11 资讯政策           | `/news`                 | 分类搜索、详情来源、关注事项、报名/确认/准考证/初试清单；实时推送待接入                        |

只建立上述11个业务模块。登录、404、API适配和语音组件为技术支撑。

## 当前 Java 接口适配

| 已有接口                       | 调用方式                       | 现状                                                                        |
| ------------------------------ | ------------------------------ | --------------------------------------------------------------------------- |
| POST `/api/user/login`         | `{phone,password}` → UserVO    | Controller 未实现登录；null 被拒绝，不伪造token                             |
| POST `/api/ai-chat/chat`       | `{message,sessionId}` → string | 已有 AI 客户端；当前 Service 只传 message，未实现服务端多轮、流式和来源引用 |
| GET `/api/school/recommend`    | `major,score` → School[]       | Service 返回 null；地区/梯度客户端筛选                                      |
| GET `/api/exam/question`       | `userId` → Question[]          | Service 返回 null；不将拿题接口当作交卷评分接口                             |
| GET `/api/study-plan/generate` | `userId` → unknown             | Service 返回 null；真实候选 DTO 与确认接口缺失                              |
| GET `/api/dashboard/overview`  | `userId` → unknown             | Service 返回 null；当前保留原始JSON核验入口，不猜测统计字段                 |
| GET `/api/news/list`           | 可选 `category` → Notice[]     | Service 返回 null；关注/实时推送接口缺失                                    |

统一响应兼容 `{code:200,message,data}` 与设计的 `{code:"OK",message,data,traceId}`。对于尚未定义的 DTO，不擅自认为它已经存在。当前前端期望的列表契约在 `src/domain/types.ts`：可直接返回数组，或 `{items:[...]}`。School、Notice 与 Question 的 ID 为字符串；API 适配层校验核心字段与选项格式，不符合时明确报错。**正式模考试卷不能返回正确答案**，目前 Question.answer 只供演示练习或训练题接口使用；正式评分应由服务端执行。

后端后续工作包括：会话认证与注销、学习资料/知识引用、图像OCR、打卡/计划写入、练习提交与错题同步、模考会话与服务端截止/评分/排名、复试评分、统计 DTO、政策来源与通知。当前没有向这些不存在的接口发送请求，前端无法代替后端实际业务、数据库权限或模型实现。

## 部署

本机开发用 Vite proxy，无需修改后端 CORS。生产可由 Nginx 提供 `dist/` 并同源代理 `/api/`。`deploy/nginx.conf` 包含 SPA history 回退与 API 代理；`backend:8080` 必须替换成实际服务网络别名，容器放到同一个 Docker 网络。

```powershell
docker build -t kaoyan-frontend:1.0.0 .
```

Dockerfile 是构建模板，默认构建真实接口模式；需要容器演示可加 `--build-arg VITE_APP_MODE=demo`。部署前应冻结 Node/Nginx 镜像摘要并配置外层 TLS；真实登录 Cookie、CSRF及访问授权由后端实现。没有改动项目原来的 `docker-compose.yml`，避免覆盖现有 MySQL/Redis 服务。

## 编码规范与验证

使用 Composition API 与 TypeScript strict；页面专注模块交互，共享状态在 Pinia，HTTP 在 `api/`，确定性规则在 `domain/logic.ts`。不使用 `v-html` 渲染模型输出；文案与内容通过文本插值显示。路由懒加载、图表按需注册，语音/请求/图表实例均在卸载时清理。表单有标签，键盘可操作，图表提供文字摘要，窄屏导航为抽屉，减少动态效果的系统偏好受到尊重。

Vitest 验证截止时间、评分、计划跨月、空成功结果处理与损坏缓存恢复。Playwright 验证11模块、桌面与手机布局、候选确认/刷新恢复、错题收录、模考交卷与超期、课程统计联动、聊天、真实模式空响应隔离。测试默认使用本机已安装的 Edge；也可设置 `PLAYWRIGHT_CHANNEL=chrome` 使用已安装的 Chrome。没有这些浏览器时安装 `npx playwright install chromium`，并在配置中去掉 channel。受限环境下推荐先单独 `npm run dev`，再在另一终端设置 `PLAYWRIGHT_EXTERNAL_SERVER=1` 执行测试，避免测试进程负责开发服务的启停。

本次验证结果：TypeScript 检查、Prettier 格式检查、生产构建均通过；9项 Vitest 规则测试与桌面/手机共16项 Playwright 流程测试通过。截图用于检查11模块布局，记录在被 Git 忽略的 `test-results/` 中。

所有测试访问本机前端或拦截模拟接口，不向真实 AI 或数据库写入数据。真实服务联调尚需 Java 服务、MySQL/Redis 与模型凭据准备完成。
