# 04 前端src与联调

[返回文档目录](README.md)

本篇针对当前实际使用的`C:\Users\jack\Desktop\UniversityAIUserPage`，不是后端仓库里的旧`frontend/`副本。它与后端是两个工作目录，Git提交也需要分别处理。

## 目录

1. [前端根目录](#前端根目录)
2. [入口和路由](#入口和路由)
3. [api：后端通信](#api后端通信)
4. [stores：共享状态](#stores共享状态)
5. [domain与composables](#domain与composables)
6. [components：逐个组件](#components逐个组件)
7. [views：逐个页面](#views逐个页面)
8. [前端类型与接口](#前端类型与接口)
9. [测试与构建](#测试与构建)
10. [两个IDE如何协作](#两个ide如何协作)

## 前端根目录

```text
UniversityAIUserPage/
├─ src/                    Vue、TypeScript和样式源代码
├─ contracts/              OpenAPI契约及验证材料
├─ scripts/                契约生成脚本
├─ tests/                  单元、浏览器及响应样本测试
├─ deploy/nginx.conf       部署时静态资源和API转发配置
├─ .vscode/                VS Code任务、调试和编辑设置
├─ index.html              HTML入口，含Vue挂载点和main.ts脚本
├─ package.json            项目依赖、dev/build/test等命令
├─ package-lock.json       固定安装依赖版本
├─ vite.config.ts          Vue插件、5173开发端口、/api/v1代理及打包分块
├─ tsconfig.json           TypeScript编译检查配置
├─ vitest.config.ts        单元测试配置
├─ playwright.config.ts    桌面及手机浏览器测试配置
├─ .env.example            前端变量示例
├─ Dockerfile              前端镜像构建配置
├─ dist/                   构建产物
└─ node_modules/           安装的第三方依赖
```

VITE_开头的环境变量可能进入浏览器构建结果，不能存数据库密码、JWT签名密钥或模型API key。`VITE_API_BASE=/api/v1`表示浏览器请求前缀；`API_PROXY_TARGET=http://127.0.0.1:8080`是Vite开发代理的目标。

## 入口和路由

| 文件 | 作用与交互 |
| --- | --- |
| `src/main.ts` | 创建Vue应用，注册Pinia和Router，引入全局样式并挂载到index.html中的app节点。 |
| `src/App.vue` | 全局外壳：登录路由显示LoginView；业务区域提供侧栏、顶部信息、模式切换和退出。live业务路由使用ContractWorkspace，其他情况交RouterView显示页面；因此真实业务逻辑不能只去views里找。 |
| `src/router/index.ts` | 声明11个模块与URL、根路径到/login的重定向，执行路由守卫和用户恢复，设置页面标题；监听认证过期事件，清理用户并返回登录页。 |
| `src/styles.css` | 全局排版、布局、导航、通用按钮、组件及响应式样式。LoginView还有局部样式；App里补充了手机退出按钮可见性。 |

路由仅影响浏览器展示，不是权限的最终边界。即使修改浏览器里的app.user或mode，也不能替代后端JWT、CSRF和数据库所有者校验。

## api：后端通信

| 文件 | 具体作用 | 修改注意 |
| --- | --- | --- |
| `api/client.ts` | 最底层HTTP客户端。组合API前缀、获取并缓存CSRF、携带Cookie、处理FormData／JSON／二进制、超时及取消、解析统一响应、抛ApiError，并发出认证过期事件。 | 所有请求通用问题先查这里。成功返回data，204不强行解JSON。不要在这里自行把失败伪装成成功。 |
| `api/errors.ts` | 定义ApiError类（message、code、traceId、status）与isRecord运行时类型判断。client也重导出供上层统一使用。 | 它是错误与类型工具，没有网络请求。 |
| `api/backend.ts` | 为页面提供较易读的业务调用包装。新login/register/currentUser/logout使用/auth接口并把displayName映射成前端nickname；其他部分功能包装正式execute操作。 | Auth接口不是旧op002包装。这里没有覆盖全部81项业务；真实操作工作台还直接调用contract.ts。 |
| `api/contract.ts` | `execute(operationId,input,options)`按契约检查参数、组装路径和查询、处理幂等key及响应Schema；`waitJob()`轮询任务；`subscribeEvents()`订阅并校验SSE。 | 页面一般不用手工拼业务URL。长任务先接受jobId，再查询结果。 |
| `api/schema.ts` | 运行时Schema处理与校验，导出operations、schemas及Schema类型，提供assertSchema等；给接口执行器和动态表单使用。 | TypeScript静态类型不能检查网络返回的真实JSON，这个文件负责相应运行时边界。 |
| `api/generated.ts` | 从OpenAPI生成的TypeScript数据模型和Operations输入／输出类型。 | 文件声明禁止手工修改；变更契约后重新生成。 |
| `api/contract-data.json` | 生成的运行时操作及Schema元数据，供schema.ts读取。 | 与generated.ts保持来自同一契约版本，不能只改类型而不改运行时约束。 |

两条实际请求入口：

```text
登录注册：LoginView → backend.ts → client.ts → /auth/*
正式业务：ContractWorkspace → contract.execute → schema/client → /api/v1业务接口
```

`scripts/generate-contract.py`负责根据前端契约生成相应API文件，执行命令为`npm run contract:generate`。后端及前端契约是多个文件副本，目前不能假设修改后端一份就会自动同步到前端。

当前新增auth接口由手写封装接入，不在既有81项生成操作的完整声明内。若要把认证也纳入统一契约生成，需要单独维护契约和生成流程；不能只在文档里把它们自动算进op001～op081。

## stores：共享状态

| 文件 | 保存什么 | 如何使用 |
| --- | --- | --- |
| `stores/app.ts` | mode、当前user、是否检查过会话、toast及displayName等。 | `setMode()`清理身份／目录缓存和CSRF等关联状态；notify显示提示。mode在sessionStorage中按封装存取，用户身份刷新后由后端恢复。 |
| `stores/catalog.ts` | 已读取的院校、科目、知识点、题目、计划、资源选项、档案及多个版本映射。 | `collect()`从响应递归收集可选资源，供动态表单选择真实id，减少手输技术ID；reset在切换身份等场景清空缓存。 |
| `stores/study.ts` | 演示学习进度：打卡、完成单元、作答、计划、模考、学习事件等。 | 通过localStorage保存并校验演示状态；不是后端MySQL的替代。清空演示数据不等于删除真实账号数据。 |

Pinia让不同组件共享内存状态；浏览器存储解决刷新后的部分本地恢复；数据库才是live业务数据的持久来源。三者不要混称“都存到数据库里”。

## domain与composables

| 文件 | 职责 |
| --- | --- |
| `domain/types.ts` | 界面侧的TypeScript类型：User、School、Question、Plan、ExamSession等，以及AppMode、LoadState、Subject等联合类型；和后端契约生成类型不是同一层模型。 |
| `domain/logic.ts` | 本地学习计划、客观题评分、剩余考试时间、日期等纯逻辑，主要给演示流程和单元测试用。 |
| `domain/contract-ui.ts` | 正式契约界面的字段中文名称、枚举展示、默认值、技术字段处理、日期等展示规则，帮助把Schema变成可使用的表单。 |
| `domain/session-storage.ts` | 对sessionStorage读写删除的安全封装，存储不可用时避免界面直接崩溃。 |
| `composables/useLoad.ts` | 可复用请求状态：idle/loading/success/error等、error文本及run包装，捕获异步异常供页面显示。 |
| `composables/useSpeech.ts` | 复用浏览器侧语音录入／播报等交互能力，具体可用性依赖浏览器能力。它不承担后端模型API密钥或数据库访问。 |
| `data/demo.ts` | 明确标记的演示院校、课程、题目及其他本地样例；不应当作真实业务数据或真实AI结果。 |

Composable是Vue组合式函数，通常以use开头，复用状态和行为。它不等于后端Service；调用关系仍需要通过API才能进入后端。

## components：逐个组件

| 文件 | 作用及关系 |
| --- | --- |
| `components/ContractWorkspace.vue` | live模式主要业务入口。按当前路由找M01～M11操作，准备资源、生成表单、调用execute、跟踪异步任务、展示返回，处理取消、部分保存／计时及事件重连等交互。业务后端仍由Java Service负责。 |
| `components/ContractField.vue` | 按Schema递归渲染输入字段，处理对象、数组、枚举及资源选项等；作为工作台表单的基本单元。 |
| `components/ContractResult.vue` | 按契约把返回数据组织为界面结果，配合字段中文名称、资源及状态展示。 |
| `components/AppIcon.vue` | 将应用使用的图标名称映射为图标组件，统一尺寸等使用方式。 |
| `components/AsyncState.vue` | 可复用异步状态展示，例如加载、失败、无数据和重试区域。 |
| `components/AvatarGuide.vue` | 数字人／陪伴引导的视觉与说明组件，供页面或工作台复用。 |
| `components/ChartPanel.vue` | 图表展示封装，接收数据并使用图表库绘图；图表只是展示，不替代后端统计规则。 |
| `components/ChatPanel.vue` | 聊天输入、消息、发送状态及相关交互的可复用面板；具体消息来源由使用者及mode决定。 |
| `components/CourseLibrary.vue` | 课程列表、内容选择和学习操作的共用组件，供公共课／专业课页面使用。 |
| `components/LiveUnavailable.vue` | 某项真实能力暂不可用时的明确提示，避免用演示结果伪装真实后端成功。 |

组件不是数据库表，也不是Java对象。父组件通过props传值、通过事件或函数收集操作，必要时再调用API；不要从浏览器直接连接MySQL。

## views：逐个页面

真实模式下App通常展示ContractWorkspace；以下模块View主要承载原有页面及演示交互。LoginView和NotFound等仍有独立路由意义。

| 文件 | 路由／模块 | 内容及主要交互 |
| --- | --- | --- |
| `views/LoginView.vue` | `/login` | 登录／注册切换、账号昵称密码校验、显示密码、提交后台、跳转、演示入口、淡绿色居中卡片与萤火虫动画。 |
| `views/DashboardView.vue` | `/dashboard`，M10 | 学习总览、时长、进度、图表和学习反馈入口。 |
| `views/CompanionView.vue` | `/companion`，M01 | 数字人陪伴、打卡、督学相关页面。 |
| `views/SchoolsView.vue` | `/schools`，M02 | 院校专业筛选、推荐与比较等界面。 |
| `views/PublicCoursesView.vue` | `/public-courses`，M03 | 公共课课程内容和学习操作，复用CourseLibrary。 |
| `views/ProfessionalCoursesView.vue` | `/professional-courses`，M04 | 专业课课程内容与资料学习，复用课程组件。 |
| `views/QaView.vue` | `/qa`，M05 | AI答疑／聊天入口，关联ChatPanel等交互。演示答复与真实模型答复必须根据mode区分。 |
| `views/PracticeView.vue` | `/practice`，M06 | 做题、提交、批改反馈、错题和专项训练入口。 |
| `views/PlanningView.vue` | `/planning`，M07 | 候选学习计划、确认生效、任务勾选和进度展示。 |
| `views/ExamsView.vue` | `/exams`，M08 | 模考答题、倒计时、答案恢复、交卷和答卷解析。演示计时逻辑与后端考试截止逻辑分开。 |
| `views/InterviewView.vue` | `/interview`，M09 | 复试场景、回答训练、自我介绍与总结等页面入口。 |
| `views/NewsView.vue` | `/news`，M11 | 资讯政策、关注及提醒展示。 |
| `views/NotFoundView.vue` | 未匹配路径 | 页面不存在时的导航／返回提示。 |

一个实际例子：你在live模式点击“刷题与错题”，路由变成/practice，但App会展示对应M06的ContractWorkspace。若要修改真实作答参数和操作按钮，应先查工作台与契约组件；若要修改演示题目数据，再查PracticeView、study store及demo数据。

## 前端类型与接口

JavaScript没有Java的class／interface运行时机制；TypeScript中的interface和type主要用于开发及编译检查，构建后大多数类型声明会被擦除。

| 定义位置 | 类型类别 | 含义 |
| --- | --- | --- |
| `api/errors.ts` | `ApiError`类 | 运行时存在的自定义错误对象。 |
| `api/client.ts` | `RequestOptions`接口 | 请求方法、body、signal、幂等键、超时、响应校验及二进制选项。 |
| `api/contract.ts` | `Input`接口、`OperationId`类型 | path/query/body输入结构及生成操作id范围。 |
| `api/schema.ts` | `Schema`等类型 | 运行时校验器所用的Schema描述类型。 |
| `stores/catalog.ts` | `ResourceOption`接口 | 资源选择项的value、显示text及原始record。 |
| `stores/study.ts` | 内部`DemoState`接口 | 本地演示数据保存结构及版本。 |
| `domain/types.ts` | 界面User、Plan、Question等 | 针对页面展示或演示逻辑的数据结构。 |
| `api/generated.ts` | 契约模型、输入／输出、Operations等 | 后端OpenAPI对应的网络数据结构；第5篇会列出当前导出的类型名称。 |

例如后端返回`displayName`，界面User使用`nickname`，两者由backend.ts映射。这是展示适配，不意味着数据库里必须有同名nickname列。不要把前端User与后端UserAccountRepository.Account直接视为同一个对象。

`domain/types.ts`中的界面接口逐个含义如下：

| 接口 | 说明 |
| --- | --- |
| `User` | 当前界面用户id、昵称及可选目标学校／专业。 |
| `School` | 院校卡片的地区、专业、分数线、招生人数、比例及风险／来源展示。 |
| `Question` | 本地题干、选项、正确答案下标和解析。 |
| `Attempt` | 演示作答记录：选项、正误、时间、练习或考试类别。 |
| `PlanTask` | 演示学习任务的标题、科目、时长、日期和完成状态。 |
| `Plan` | 演示学习计划，组织版本、周期、目标及多个PlanTask。 |
| `ExamSession` | 本地演示考试的题目、已选答案、开始时间、时限及交卷时间。 |
| `Notice` | 资讯卡片的分类、标题、日期、来源、内容及重要标记。 |
| `LearningEvent` | 演示学习事实的科目、分钟数、日期及专注状态。 |
| `Message` | 聊天界面的一条消息，含id、user/assistant角色和内容。 |
| `CourseUnit` | 演示课程单元的标题、标签、时长、内容和知识要点。 |

第5篇还列出当前239条导出类型声明，并给出生成模型的主要字段、组合类型或所关联的operationId，以便从类型反查接口。

## 测试与构建

| 路径／命令 | 作用 |
| --- | --- |
| `tests/unit/logic.test.ts` | 演示评分、计划、考试计时及本地状态等规则测试。 |
| `tests/unit/contract.test.ts` | 正式接口数量、字段边界、客户端请求及存储等契约相关测试。 |
| `tests/e2e/modules.spec.ts` | 演示模式各模块、计划、做题、模考等浏览器交互。 |
| `tests/e2e/contract.spec.ts` | 正式契约业务界面及受控响应的浏览器流程测试；查看其fixture／路由mock设置判断是否是真实后端调用。 |
| `tests/e2e/auth.spec.ts` | 真实认证联调：首屏、居中和减少动画、注册、Cookie属性、刷新恢复、退出及重新登录，分别跑桌面和手机。需后端运行。 |
| `tests/fixtures/responses.json` | 契约响应测试样本。 |
| `tests/fixtures/job-results.json` | 各类异步任务结果样本。 |
| `npm run dev` | 开发服务器，默认5173；要一直保持运行。 |
| `npm run typecheck` | TypeScript／Vue类型检查。 |
| `npm run build` | 类型检查后生成dist，不会启动Java。 |
| `npm test` | Vitest单元测试。 |
| `npm run test:e2e` | Playwright浏览器测试，所需后端或受控样本取决于测试文件。 |
| `npm run contract:generate` | 从契约生成TypeScript及运行时元数据。 |

`dist/`是构建结果，不应手工修改。Vite开发代理只在开发服务器运行时起作用；直接双击dist/index.html不是完整的前后端部署。

Nginx部署配置提供前端路由fallback、`/api/`转发和SSE相关代理设置，后端目标名为`backend:8080`，需要部署网络与之匹配；不能把这个容器主机名直接当作本机Vite目标。

## 两个IDE如何协作

| 开发工具 | 打开的目录 | 应做的事 |
| --- | --- | --- |
| IDEA | `D:\STUDY\UniversityAIUser` | 重新加载Maven，选择KaoyanAi MySQL运行，看到8080启动成功 |
| VS Code | `C:\Users\jack\Desktop\UniversityAIUserPage` | npm run dev，开发／调试Vue和TypeScript |
| 浏览器 | `http://127.0.0.1:5173/login` | 使用前端，通过代理调用后端 |

后端`.run/KaoyanAi MySQL.run.xml`固定工作目录，使`.local-data`配置相对路径正确；`scripts/setup-local-auth.ps1`只生成一次本地JWT密钥。IDEA不会自动继承你在另一个已打开终端刚设置的临时环境变量。

前端`.vscode/tasks.json`提供启动、构建和测试任务；`.vscode/launch.json`浏览器地址已修正为5173/login；`.vscode/settings.json`是编辑器格式化等设置，`.vscode/extensions.json`是推荐扩展。

快速检查顺序：

1. 后端`http://127.0.0.1:8080/api/v1/session/csrf`是否200。
2. 前端代理`http://127.0.0.1:5173/api/v1/session/csrf`是否200。
3. 页面是否使用同一host，避免localhost与127.0.0.1切换丢Cookie。
4. 注册／登录请求的状态、业务code和message是什么。
5. 成功登录后auth/me是否200，刷新是否仍能恢复用户。

数据库、JWT及CSRF的问题要沿后端查；纯排版和萤火虫效果先查LoginView；实际请求字段与动态表单先查contract.ts、Schema和ContractWorkspace。
