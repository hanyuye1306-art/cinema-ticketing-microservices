# gouyan 课程设计最终集成验收报告

验收日期：2026-10-20（Asia/Shanghai）

## 1. 总体结论

项目可以进入课程设计报告与答辩准备阶段。课程要求涉及的微服务注册发现、双节点 Eureka、
OpenFeign、Ribbon、Hystrix、Gateway、MyBatis 分层与购票业务链路均已通过源码审查和真实运行验证。
全模块 `mvn test`、`mvn package` 均成功；真实支付数据已清理并核验恢复，无 P0 遗留。

## 2. 最终测试总表

| 项目 | 状态 | 证据 | 备注 |
|---|---|---|---|
| 数据库备份 | PASS | 项目外备份 110443 字节 | 写测试前完成 |
| 支付事务单元测试 | PASS | 6/6 通过 | 含三种失败步骤 |
| 正常支付 | PASS | 票房 0→45、座位 0→3、订单已支付 | Gateway HTTP |
| 重复支付 | PASS | 票房保持 45 | 未重复累计 |
| 无效场次支付 | PASS | HTTP 200、业务码 500、订单未支付 | 无脏写 |
| 测试数据清理 | PASS | 订单恢复为 6 条，座位和票房恢复 | 精确删除订单 15/16 |
| Hystrix Timeout | PASS | `HystrixTimeoutException`，约 0.52s | 下游延迟 1500ms，阈值 500ms |
| Hystrix OPEN/短路 | PASS | OPEN；后续约 8–13ms 返回 | 指标记录 short-circuited |
| Hystrix 恢复 | PASS | 探测成功后 CLOSED | 9011 返回真实数据 |
| 双 Movie 停止 Fallback | PASS | 业务码 503、`fallback=true`，约 0.012s | 两实例随后恢复 |
| Maven test | PASS | 7/7 通过，0 失败，0 跳过 | 13 模块 reactor |
| Maven package | PASS | 全模块成功 | 未跳过测试 |
| 全系统冒烟 | PASS | 两个 Eureka、7 个业务/网关能力、双前端 | 最终正常 Profile |

## 3. 真实支付测试

- 正常支付：场次 28、电影 8、测试座位 1排1号。订单 15 创建并支付成功；订单状态为已支付，
  座位由 0 变为 3，电影票房由 0 增至 45，增量等于场次价格 45。
- 重复支付：再次提交订单 15，接口仍返回成功，票房保持 45，未产生新订单或重复座位变化。
- 无效场次：订单 16 指向隔离 ID 999999999；支付返回业务码 500，订单状态保持未支付，票房未变化。
- 异常回滚：`BillPaymentServiceImplTest` 分别覆盖订单更新失败、座位更新失败、票房更新失败；
  服务方法声明 `@Transactional(rollbackFor = Exception.class)`，每个失败点抛出运行时异常触发整笔事务回滚。
- 清理：订单 15/16 已删除；场次 28 原始座位 JSON、电影 8 原始票房 0.00 已恢复；订单总数回到 6。
- 备份：`~/.codex/gouyan-backups/movie-before-final-acceptance-20261020.sql`，110443 字节。

## 4. Hystrix 测试

- 正常请求：Bill 经 Feign 返回真实电影数据，正常模式下 9010/9011 严格交替。
- Fallback：两台 Movie 同时停止时，Bill/Gateway 保持可用，JSON 业务码 503、`fallback=true`，
  failure 信息明确为连接拒绝，未返回虚假电影数据。
- Timeout：Movie 9010 延迟 1500ms，Hystrix 命令超时 500ms；约 0.520s 返回
  `HystrixTimeoutException`。
- OPEN：达到演示阈值后状态接口返回 `state=OPEN`、错误率 100%。
- 快速短路：OPEN 后请求约 0.008–0.013s 返回，failureMessage 明确包含 short-circuited/OPEN。
- 恢复 CLOSED：sleep window 后探测到 9011 成功，状态恢复 CLOSED；指标中
  `cumulativeTimeout=3`、`cumulativeShortCircuited=5`、`cumulativeSuccess=2`。
- 正常 Profile 恢复：演示进程已停止；正常 Bill 的演示状态地址返回 HTTP 404，随后完整冒烟通过。

## 5. Maven 测试

测试总数 7，通过 7，失败 0，错误 0，跳过 0。

- `gouyan-bill`：6 个支付事务测试全部通过。
- `gouyan-hall`：1 个 Spring Context 测试通过。
- 其余模块：无测试源，模块构建成功。

Hall 原失败根因是测试读取生产 `application-druid.yml` 中的固定密码，导致本机空密码 root 认证失败；
并非业务代码错误。新增测试专用 `application.yml`，密码来自 `MYSQL_PASSWORD`（默认空），同时关闭测试期
Eureka 注册，不影响生产配置。

## 6. 课程要求符合性

| 硬性要求 | 状态 | 源码与运行证据 |
|---|---|---|
| Spring Boot + Spring Cloud | PASS | 父 POM 管理 Boot 2.3.2、Cloud Hoxton；13 模块构建成功 |
| Eureka 双节点 | PASS | `gouyan-eureka` 的 peer1/peer2；7001、7002 注册表均通过 |
| Ribbon 负载均衡 | PASS | Bill `RoundRobinRule`；6 次调用 9010/9011 严格交替 |
| OpenFeign | PASS | `MovieFeignClient`；Bill 经服务名获取真实电影数据 |
| Hystrix | PASS | `BillMovieQueryServiceImpl`、FallbackFactory、状态控制器；Timeout/OPEN/恢复实测 |
| Gateway | PASS | `gouyan-gateway/application.yml` 路由；所有服务均经 8181 验证 |
| Controller/Service/Mapper/Entity | PASS | Bill Controller、Payment Service、System Mapper、domain 实体分层明确 |
| MyBatis CRUD | PASS | `gouyan-system` Mapper/XML；电影、影院、影厅、订单真实查询及支付更新通过 |
| 至少五张业务表 | PASS | 数据库共 22 张表；电影、影院、影厅、场次、订单等均实际使用 |
| 可运行业务流程 | PASS | 登录→查询电影/影院/场次→创建订单→模拟支付完整通过 |

## 7. 文件修改记录

- `BillPaymentService.java`、`BillPaymentServiceImpl.java`：集中支付事务、校验、幂等与回滚。
- `SysBillController.java`：支付委托事务服务；Feign 查询显式传递 Token。
- `SysBillMapper.java`、`SysBillMapper.xml`：增加订单行锁查询。
- `BillPaymentServiceImplTest.java`：6 个成功、幂等及失败步骤测试。
- `BillMovieQueryService.java`、`BillMovieQueryServiceImpl.java`：恢复线程隔离，支持真实 Hystrix 超时。
- `application-hystrix-demo.yml`：演示命令超时设为 500ms。
- `gouyan-hall/src/test/resources/application.yml`：隔离的测试数据源配置。
- `scripts/start-all.sh`、`status.sh`、`stop-all.sh`：可重复启动、状态和停止。
- `scripts/smoke-test.sh`：全系统只读冒烟、Eureka 双节点、Feign/Ribbon/前端检查。
- `RUNBOOK.md`：启动、回归、Hystrix 演示说明。

## 8. 剩余问题

- P0：无。
- P1：启动脚本依赖本机 MySQL 已运行、数据库已初始化；脚本会在前置检查失败时终止。
- P2：旧版 Vue CLI 在 Node 20 下需要 `NODE_OPTIONS=--openssl-legacy-provider`，脚本已处理。

## 9. 最终运行状态

正常 Profile 下应运行：Eureka 7001/7002、Actor 9000、Auth 9002、Bill 9004、Cinema 9006、
Hall 9009、Movie 9010/9011、Admin 8183、Gateway 8181、用户端 8080、管理端 8888。
PID 为运行时信息，以 `./scripts/status.sh` 和 `lsof -iTCP:<port> -sTCP:LISTEN` 为准。

访问地址：Eureka `http://127.0.0.1:7001`、`http://127.0.0.1:7002`；Gateway
`http://127.0.0.1:8181`；用户端 `http://127.0.0.1:8080`；管理端 `http://127.0.0.1:8888`。

## 10. Git 状态

所有本轮变更均保持未提交；未执行 commit 或 push。以最终 `git status --short` 输出为准。
