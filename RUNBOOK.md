# gouyan 课程验收运行手册

## 环境

- JDK 8 或 11
- Maven 3.6+
- MySQL 8（数据库名 `movie`）
- Node.js；当前项目在 Node 20 下通过 `NODE_OPTIONS=--openssl-legacy-provider` 启动旧版 Vue CLI
- 默认数据库用户为 `root`，密码通过环境变量 `MYSQL_PASSWORD` 提供；本机空密码时无需设置

首次初始化数据库：

```bash
mysql -uroot -p < sql2023/movie.sql
```

前端首次安装依赖：

```bash
cd gouyan-web/gouyan-user && npm install
cd ../gouyan-ui && npm install
```

## 一键启动

```bash
./scripts/start-all.sh
```

可选参数：

- `--backend-only`：不启动两个 Vue 前端。
- `--skip-build`：复用已有 JAR，跳过 Maven 构建。
- `--hystrix-demo`：让一个 Movie 实例延迟响应，并为 Bill 启用熔断演示配置。

如果 MySQL root 有密码：

```bash
MYSQL_PASSWORD='你的密码' ./scripts/start-all.sh
```

启动顺序固定为：MySQL 检查 → `mvn install` 全模块构建 → Eureka 7001/7002 → 业务服务和 Movie 双实例 → Gateway → 前端。项目原始 JAR 没有可执行清单，因此脚本使用固定版本的 Spring Boot Maven Plugin 启动各模块。

## 状态、日志和停止

```bash
./scripts/status.sh
tail -f .runtime/bill.log
./scripts/stop-all.sh
```

`stop-all.sh` 只停止本脚本创建并记录 PID 的进程，不会停止启动脚本运行前已经存在的服务。

## 验收地址

- Eureka：<http://127.0.0.1:7001>、<http://127.0.0.1:7002>
- Gateway：<http://127.0.0.1:8181>
- 用户前台：<http://127.0.0.1:8080>
- 管理后台：<http://127.0.0.1:8888>
- 默认账号：`admin / 123456`

Hystrix 快速演示时，Bill 需额外启用 `hystrix-demo` Profile；Movie 可用启动参数
`--demo.movie.delay-ms=1500` 模拟超时。正常验收启动不启用这些激进参数。

## 集成回归

正常模式启动后执行：

```bash
GOUYAN_USERNAME=admin GOUYAN_PASSWORD='你的密码' ./scripts/smoke-test.sh
```

脚本自动验证登录、Gateway、电影查询、影院查询、Bill 到 Movie 的 Feign 调用，以及
Ribbon 是否轮询到两个 Movie 实例。账号不同时可设置 `GOUYAN_USERNAME` 和
`GOUYAN_PASSWORD`。

Hystrix 演示需先停止本脚本管理的服务，再以演示模式启动：

```bash
./scripts/stop-all.sh
./scripts/start-all.sh --backend-only --hystrix-demo
GOUYAN_USERNAME=admin GOUYAN_PASSWORD='你的密码' \
  ./scripts/smoke-test.sh --expect-hystrix --backend-only
```

最后通过用户前台完成一次真实写链路验收：选择一个仍有空座的场次，创建订单并模拟支付；
刷新订单确认状态为已支付，刷新场次确认座位已售出，并在管理后台确认电影票房只增加一次。
对同一订单再次提交支付请求时，票房和座位数不应重复增加。
