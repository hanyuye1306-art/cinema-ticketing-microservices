# 前端依赖修复与验证记录

验证日期：2026-11-18（Asia/Shanghai）。实际环境：Windows，Node.js v18.13.0，npm 9.9.4。

两个项目仍分别维护一套 package.json 和 package-lock.json；Windows/macOS 共用，无需 Git Bash 或平台专用安装脚本。仓库根目录没有统一的 package.json、workspaces 或原有项目级 .npmrc，应进入对应前端目录执行 npm。

## 修改文件和备份

两个目录 gouyan-web/gouyan-user、gouyan-web/gouyan-ui 各修改/新增：

- package.json：替换 Sass 编译器，固定兼容版本，声明 Node/npm engines 和 packageManager。
- package-lock.json：在原锁定树基础上修复 URL、alias，再由 npm 9 补全为 lockfileVersion 3；没有删除锁文件重选全部版本。
- vue.config.js：显式指定 Dart Sass implementation，保留标题、端口和 publicPath。
- .npmrc：官方 Registry、strict-ssl=true、legacy-peer-deps=true。
- .nvmrc：18.13.0。

另新增本记录和 frontend-dependency-changes.json，后者逐项列出版本变化、新增、删除及 alias 修复。

原文件逐字节备份位置：C:\Users\lenovo\cinema-ticketing-microservices\.runtime\frontend-deps\2026-11-17T18-16-46-170Z

开始任务时用户端锁文件已有未提交修改，版本对照基于该工作区现状；原有 package-lock.json.backup、package-lock.json.before-fix 以及 scripts 下用户已有改动均保留。本次没有修改业务源代码、后端 Java 或数据库，没有 Git 提交或推送。

## 根因和修复方法

1. **历史镜像与错误 tarball 路径**：管理端仍有 1236 条 resolved 包含旧镜像/历史下载路径/参数；用户端有 351 条含历史参数或旧镜像的地址。用户端还存在 scoped 包的错误路径，例如 @vue/cli-service/-/@vue/cli-service-4.5.9.tgz。只替换域名不能把镜像私有的 /download/ 变为官方 Registry 路径，也不能修正 scoped 包的文件名。
2. **CERT_HAS_EXPIRED**：旧淘宝镜像地址存在历史证书问题；本次移除旧镜像，所有安装使用官方 HTTPS，保持证书校验。未复现用户此前的证书错误，不修改系统证书或用户全局 npm 配置。
3. **ETIMEDOUT**：访问遗留镜像或网络链路超时；此次官方 Registry 实际安装成功。网络超时本身仍可能由代理、DNS、防火墙或线路导致，无法用依赖文件保证永不发生。
4. **E404**：官方 Registry 不提供历史镜像的 /download/ 路径；scoped 包 tarball 也不能把完整 @scope/name 重复用作文件名。按包名、实际版本和 alias 目标构造标准地址，例如 @vue/cli-service/-/cli-service-4.5.9.tgz，清除整个查询串，保留 integrity，由实际安装校验下载内容。最终逐项校验用户端 1278、管理端 1279 个 resolved，全部为标准官方地址，没有 taobao、npmmirror、other_urls 或 /download/ 残留。
5. **vue-loader-v16 ETARGET**：管理端旧锁文件错误地指向 npm:vue-loader-v16@16.8.3，真实包为 vue-loader@16.8.3。vue-loader-v16 是安装别名，不是真实包名。修复后 v3 锁记录 name=vue-loader、version=16.8.3；Vue 2 实际使用的 vue-loader 保持 15.9.5。npm 9 从 v1 补全元数据期间仍曾产生 alias 告警，最终 v3 锁的实际安装和 npm ci 均通过，不再依赖旧锁补全流程。
6. **node-sass 与 Node 18**：node-sass 4.14.1 对应 Node 14 时代 ABI，不支持 Node 18；官方兼容表要求 Node 18 使用 node-sass 8+。为避免引入新的原生二进制依赖，使用纯 JavaScript 分发的 Dart Sass 1.32.13。该版本保留旧 SCSS/Element UI 所需的传统 Sass API 和导入行为，避免直接采用最新 Sass 所带来的额外语义变化。
7. **sass-loader**：原 manifest 的 ^7.0.3 实际锁定为 7.3.1；7.3.1 官方支持 Dart Sass，因此固定该现有版本，无需升级 loader 主版本。vue.config.js 显式选择 require('sass')。两个项目的 element-variables.scss 及其导入的 Element UI 主题均已实际编译。
8. **Node 18 / Webpack 的另一个真实故障**：首次生产构建均在 Webpack 4.44.2 的 crypto MD4 调用处失败，ERR_OSSL_EVP_UNSUPPORTED。定向使用 Webpack 4.47.0，其内置兼容实现支持 Node >=18；同时按其依赖要求更新 enhanced-resolve。没有设置 NODE_OPTIONS=--openssl-legacy-provider。
9. **npm 9 与旧 peer 依赖树**：项目级 legacy-peer-deps=true 让 npm 9 保留旧项目的 peer 解析方式，避免迁移时自动引入额外 peer 依赖，尤其是 CLI 4 附带的 Vue 3 loader alias 分支。该选项不关闭 SSL，也不省略业务运行依赖。npm install 与 npm ci 均自动读取同一配置，不应单独移除此设置后重写锁文件。
10. **Vue 编译器匹配**：Vue 和 vue-template-compiler 原来分别使用版本范围，未来解锁可能漂移。现在都固定为原锁定版本 2.6.12。

## 版本对照（两个项目相同的部分）

| 依赖 | 修改前实际锁定 | 修改后 |
| --- | --- | --- |
| Vue | 2.6.12 | 2.6.12（固定） |
| vue-template-compiler | 2.6.12 | 2.6.12（固定） |
| @vue/cli-service | 4.5.9 | 4.5.9 |
| @vue/cli-plugin-babel | 4.5.9 | 4.5.9 |
| vue-loader（Vue 2） | 15.9.5 | 15.9.5 |
| vue-loader-v16（alias 目标） | vue-loader 16.8.3，管理端包名错误 | vue-loader 16.8.3，修复元数据 |
| node-sass | 4.14.1 | 移除 |
| sass | 无 | 1.32.13 |
| sass-loader | 7.3.1 | 7.3.1（固定） |
| webpack | 4.44.2 | 4.47.0 |
| enhanced-resolve | 4.3.0 | 4.5.0 |
| element-ui | 2.14.1 | 2.14.1 |
| lockfileVersion | 1 | 3 |
| engines | 未声明 | Node >=18.13.0 <19；npm >=9 <10 |

除 webpack、enhanced-resolve 两个包的版本变化外，保留在原路径的所有包版本均未变；只新增 sass。随 node-sass 清除无用的依赖条目：用户端 66 条、管理端 81 条（包含 node-sass 本身和嵌套依赖）。完整路径/版本见 frontend-dependency-changes.json。保留 macOS 专用 fsevents 的 optional/os=darwin 元数据，Windows 自动跳过，不因 Windows 安装而从共享锁中删除。

## Windows 命令

使用 Node 18.13.0、npm 9.9.4（已在本机验证）。PowerShell 或 Git Bash 都可使用 npm 原生命令，不需要管理员终端。

用户端，PowerShell：

~~~powershell
Set-Location 'C:\Users\lenovo\cinema-ticketing-microservices\gouyan-web\gouyan-user'
node --version
npm --version
npm install
npm run serve
~~~

在另一个终端构建：

~~~powershell
Set-Location 'C:\Users\lenovo\cinema-ticketing-microservices\gouyan-web\gouyan-user'
npm run build
~~~

管理端，另一个 PowerShell 终端：

~~~powershell
Set-Location 'C:\Users\lenovo\cinema-ticketing-microservices\gouyan-web\gouyan-ui'
npm install
npm run serve
~~~

在另一个终端构建：

~~~powershell
Set-Location 'C:\Users\lenovo\cinema-ticketing-microservices\gouyan-web\gouyan-ui'
npm run build
~~~

如 PowerShell 的执行策略拦截 npm.ps1，可把命令中的 npm 改为 npm.cmd，无需修改系统执行策略。

## macOS 命令（待实际 macOS 验证）

使用相同 Node/npm 版本。下列 /path/to 是实际仓库位置的占位符。若使用 nvm，可在任一前端目录执行 nvm install、nvm use 读取 .nvmrc；npm 版本应为 9.x，验证基线为 9.9.4。

用户端：

~~~sh
cd /path/to/cinema-ticketing-microservices/gouyan-web/gouyan-user
node --version
npm --version
npm install
npm run serve
~~~

管理端，另一个终端：

~~~sh
cd /path/to/cinema-ticketing-microservices/gouyan-web/gouyan-ui
npm install
npm run serve
~~~

构建时，在对应项目目录分别执行：

~~~sh
npm run build
~~~

两个系统进行干净/CI 安装时，可在对应项目目录用 npm ci 替代 npm install；保留并使用共享锁文件，不复制其他系统的 node_modules。

## 实际验证结果

| 检查 | 用户端 | 管理端 |
| --- | --- | --- |
| Windows npm install | 通过 | 通过 |
| Windows 从最终锁文件 npm ci | 通过 | 通过 |
| Windows npm run build | 退出码 0；dist 生成 | 退出码 0；dist 生成 |
| Windows npm run serve | 编译成功，8080 | 编译成功，8888 |
| 开发首页 HTTP | 200，含 app 挂载点 | 200，含 app 挂载点 |
| 开发 JS 资源 HTTP | /js/chunk-vendors.js 200 | /js/chunk-vendors.js 200 |
| JSON、root 依赖一致性、alias、下载地址校验 | 通过 | 通过 |
| macOS 安装/启动/构建 | 待验证 | 待验证 |

启动验证使用 npm run serve -- --host 127.0.0.1，未改默认端口；检查完成后已关闭本次启动的进程。日常启动可访问 http://localhost:8080 和 http://localhost:8888。管理端生产 publicPath 仍为 /gouyan-ui。

验证日志位于 .runtime/frontend-deps：*-ci.log、*-build.log、*-build-before-webpack.log、*-serve.log 和 serve-results.json。全新安装未依赖用户端之前残缺的 node_modules。

## 仍需人工验证

- 在 macOS（包括实际使用的 Intel/Apple Silicon 机型）运行相同的 npm ci、npm run serve、npm run build，检查文件监听和热更新；本次不能声称 macOS 已运行通过。
- 使用真实后端检查登录、电影/影院查询、选座、下单、支付以及管理端增删改查；本次仅验证前端安装、编译和 HTTP 资源，没有端到端业务验收。
- 浏览器检查 Element UI 主题、字体、布局，以及中文拼音检索。Dart Sass 已通过实际 SCSS 构建，但未做替换前后页面像素对比。
- 保留旧栈导致仍有 deprecated、旧 Browserslist 数据和资源体积提示；生产构建的 3 项警告是资源体积、入口体积和拆包建议，不是编译失败。未自动升级业务库或执行 npm audit fix --force。
- Node 18、Vue 2 和旧 CLI 均属历史技术栈。此处声明的是此次兼容修复的运行范围，不代表长期安全维护承诺；后续迁移受支持运行时和处理历史依赖漏洞应单独评估、回归。

## 官方依据

- [node-sass Node ABI 兼容表](https://github.com/sass/node-sass#node-version-support-policy)
- [sass-loader 7.3.1 的 Dart Sass 和 implementation 文档](https://github.com/webpack-contrib/sass-loader/tree/v7.3.1#implementation)
- [Webpack 4.47.0：Node >=18 MD4 支持](https://github.com/webpack/webpack/releases/tag/v4.47.0)
- [npm 9 package-lock.json 文档](https://docs.npmjs.com/cli/v9/configuring-npm/package-lock-json/)
