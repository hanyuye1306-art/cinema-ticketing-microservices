# 管理后台修复与首页验证

验证环境：Windows 11，Node 18.13.0 / npm 9.9.4，Java 11，MySQL，通过 Gateway 8181 访问实际运行的服务。管理页面为 http://127.0.0.1:8888/welcome 。macOS 本轮未实机验证。

## 首页与头像

- `gouyan-web/gouyan-ui/src/views/Home.vue`：导航、页头、账号与真实头像；从当前账号 API 读取资料，图片失败时显示账号首字母。编辑本人头像后通过事件同步更新。
- `gouyan-web/gouyan-ui/src/views/Welcome.vue`：真实影片/影院/今日排片/已付票款统计、近七日订单、近期排片、地区分布、最近入库和菜单权限内的快捷入口。
- `gouyan-cloud/gouyan-auth/.../AdminDashboardController.java`：只读聚合接口 `/sysUser/dashboard`。系统管理员查看全部影院，影院负责人只统计自己的影院；普通用户拒绝访问。影片资料库统计是全站范围，页面已标注。
- 看板以北京时间计算日期；已付票款按当前场次票价 × 订单座位数计算，是当前业务表统计口径，不等同独立支付流水或财务结算报表。
- 数据请求失败显示明确错误及“—”，不保留可能过时的金额，不使用虚构趋势或演示数字。
- `src/assets/css/global.css`：修复原表格字号 40px，统一基础字体、卡片和面包屑。

## 增删改问题及修复

| 问题 | 修复位置/行为 |
| --- | --- |
| API 返回失败仍提示成功；表单校验回调未等待；重复点击 | `src/utils/adminCrud.js` 统一检查响应、等待表单校验和阻止重复提交，应用到 19 个管理页 |
| 关闭删除弹窗也继续删除；空选择发送请求 | 只有明确确认才继续，空选择拦截；错误显示接口原因 |
| 读取/回显请求失败未处理 | 统一读取错误处理，回显完成后才打开编辑弹窗 |
| 编辑图片累积、重试重复上传、回调覆盖表单对象 | 重新回显清空旧列表；复用上传成功地址；保存后旧图清理失败单独提示 |
| 系统管理员没有 cinemaId，影厅/排片新增失败或无法编辑其他影院 | 系统管理员明确选择影院；负责人使用所属影院；编辑保留已售座位 |
| 影片上映日期格式与日期选择器不一致 | 影片实体与表单统一日期格式 yyyy-MM-dd |
| 新增影片 SQL 字段拼写 movie_box_offcie 错误 | 修正为 movie_box_office；缺省统计值初始化 |
| 顶级菜单 parentId=null 异常；菜单循环 | 统一根节点；校验父级存在及循环关系 |
| 角色分配权限参数绑定失败；相同权限重复保存报失败 | MyBatis 多参数添加 @Param；相同配置视为成功，权限修改事务化 |
| 评论批量删除接口/标识错误；修改评论改变主键时间 | 使用 POST 复合标识删除；更新精确定位并保留 commentTime |
| 评论删除信任前端评分，可能破坏影片评分 | 事务内锁定影片，按实际剩余评论重新计算评分与人数 |
| 订单修改请求 DTO 不匹配 | 使用 `{sysBill: ...}` 信封；失败刷新真实状态 |
| 删除父数据留下孤立业务数据；批量部分删除 | `AdminDeleteGuard` 先检查整批关联关系；相关服务删除使用事务 |
| 影厅布局更新可能覆盖已售座位 | 仅真实布局变化才传播；存在订单时拒绝重排；名称/分类仍可修改 |
| 修改本人资料导致会话形状/令牌丢失 | 保留嵌套登录上下文，更新本人令牌与头像事件；空密码保持原密码 |

19 页：ActorInfo、ActorRole、CinemaInfo、CinemaArea、CinemaBrand、HallInfo、HallCategory、MovieInfo、MovieComment、MovieAge、MovieArea、MovieCategory、MovieRuntime、SessionInfo、BillInfo、UserInfo、UserHobby、RoleInfo、ResourceInfo。

删除保护属于业务约束：已支付订单不能直接删除或切回未支付；有关联记录的影院、影厅、电影、字典、角色、菜单等需要先处理关联记录；内置角色和当前登录账号受保护。页面会返回明确原因，避免出现“删除成功”却留下错误关联的情况。未增加数据库结构变更。

## 实际验证结果

- 后台 `npm run test:crud`：79 个子场景通过（19 页取消/关闭/空选择、API 拒绝时保留表单、错误不假报成功、座位保留、订单 DTO、看板数据和头像）。
- 用户端 `npm run test:profile`：4 个场景通过，包含后台本人资料更新。
- 后端 Maven：auth 34、bill 8、hall 1 项，共 43 项通过。
- 两个前端 `npm run build` 均通过；原有较大 bundle 提示仍存在。
- `scripts/smoke-test.sh` 全系统只读集成回归通过，Eureka 双节点、Movie 双实例轮询、Gateway、Feign 和两个前端均正常。
- `scripts/smoke-admin-crud.py --write-tests`：50 个场景通过。经真实 Gateway/数据库执行新增、查询、修改和删除；包含影片演员/分类关联、评论评分回算、角色授权和幂等授权、未支付订单、关联删除拒绝、管理员和影院负责人看板范围。
- 每次写回归前使用 mysqldump 备份。只删除本次随机标记的测试数据，按逆依赖顺序清理并回读；最后比较备份和测试后的所有表 INSERT 数据，确认原有业务记录不变。自增编号允许正常递增，不回退编号。
- 浏览器实测：首页真实数据、账号真实头像、刷新、快捷入口、影片编辑回显（图片和日期）、桌面与窄屏布局。没有在原有业务记录上执行保存/删除。

本地证据位于被 Git 忽略的 `.runtime/admin-crud/`：源文件备份 `source-before/`、数据库前后快照、`live-regression.log`、`last-run.json`、构建/测试日志、`homepage.jpg`。备份可能含原有业务数据，请保持本地保存。

## 重跑命令

在仓库根目录，用 PowerShell 或 macOS 终端进入各目录执行原生 npm 命令：

```text
cd gouyan-web/gouyan-ui
npm run test:crud
npm run serve -- --port 8888
npm run build

cd ../gouyan-user
npm run test:profile
npm run serve -- --port 8080
npm run build
```

仓库根目录：

```text
python -X utf8 scripts/smoke-admin-crud.py --write-tests
bash scripts/smoke-test.sh
```

写回归需要本地 `mysqldump`、正常运行的全部服务、管理员账号，以及用于备份的 `MYSQL_PASSWORD` 环境变量。网站管理员密码从安全提示输入，也可通过临时 `GOUYAN_PASSWORD` 环境变量提供，不写入脚本。只读 smoke 使用相同环境变量覆盖其演示密码；用户名可用 `GOUYAN_USERNAME` 覆盖。

仍需人工验证：macOS 实机启动；实际 QQ 邮件收件/垃圾箱投递（配置阶段只测试 TLS 和鉴权，不自动发送邮件）；按真实业务需求处理退款，不通过后台状态开关模拟退款。当前回归覆盖功能与错误处理，不是生产并发压测。
