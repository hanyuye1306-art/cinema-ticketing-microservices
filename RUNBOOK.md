# gouyan 课程验收运行手册

## 1. 环境与依赖

项目最近一次完整验证使用以下版本：

- OpenJDK 11.0.32.1（源码目标版本为 Java 8，因此也支持兼容的 JDK 8）
- Apache Maven 3.9.16
- MySQL 8.4.11
- Node.js 20.20.2、npm 10.8.2
- Python 3.14.6
- curl 8.7.1

除 Java、Maven、MySQL、Node.js 和 npm 外，自动化脚本还需要：

- `mysqladmin`：启动前检查 MySQL 状态
- `lsof`：检查端口并记录服务进程
- `curl`：执行 HTTP 冒烟测试
- `python3`：解析冒烟测试的 JSON 响应

macOS 使用 Homebrew 时可安装主要依赖：

```bash
brew install openjdk@11 maven mysql node lsof python
```

## 2. 数据库初始化

所有后端服务使用本机 MySQL 的 `movie` 数据库，默认用户为 `root`。数据库密码不会保存在仓库中，通过环境变量 `MYSQL_PASSWORD` 提供；本机 root 空密码时无需设置。

先启动 MySQL：

```bash
brew services start mysql
```

创建数据库并导入初始化脚本：

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS movie CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -uroot -p movie < sql2023/movie.sql
```

如果 root 没有密码，去掉上述命令中的 `-p`。验证导入结果：

```bash
mysql -uroot -p movie -e "SHOW TABLES;"
```

`sql2023/movie.sql` 包含课程演示所需的电影、影院、场次和用户种子数据。仓库公开前应确认其中的手机号、邮箱和用户资料均为虚构演示数据。

## 3. 安装与构建

前端首次安装依赖：

```bash
cd gouyan-web/gouyan-user && npm install
cd ../gouyan-ui && npm install
cd ../../
```

后端测试和打包：

```bash
cd gouyan-cloud
mvn test
mvn package
cd ..
```

## 4. 一键启动

MySQL root 为空密码时：

```bash
./scripts/start-all.sh
```

建议同时为 JWT 设置独立随机密钥（不要提交到仓库）：

```bash
JWT_SECRET='至少32位随机字符串' ./scripts/start-all.sh
```

两个 Vue 项目通过 `VUE_APP_API_BASE_URL` 配置 API 地址，未设置时默认使用
`http://127.0.0.1:8181`。生产构建示例：

```bash
VUE_APP_API_BASE_URL='https://api.example.com' npm run build
```

MySQL root 有密码时，只对当前命令注入密码：

```bash
MYSQL_PASSWORD='你的本机数据库密码' ./scripts/start-all.sh
```

Spring 配置使用 `${MYSQL_PASSWORD:}` 读取同名环境变量；`start-all.sh` 也会将该值传给每个后端服务。不要把密码写进仓库、Shell 脚本或 `.env` 文件。

可选参数：

- `--backend-only`：不启动两个 Vue 前端。
- `--skip-build`：复用已有构建结果，跳过 Maven 构建。
- `--hystrix-demo`：让一个 Movie 实例延迟响应，并为 Bill 启用熔断演示配置。

启动顺序固定为：

1. 检查 MySQL 和 `movie` 数据库。
2. 执行 Maven 全模块构建。
3. 启动 Eureka 7001、7002 双节点。
4. 启动 Actor、Auth、Bill、Cinema、Hall、Movie 双实例和 Admin。
5. 启动 Gateway。
6. 启动用户前台和管理后台。

项目原始 JAR 没有可执行清单，因此脚本使用固定版本的 Spring Boot Maven Plugin 启动各模块。

## 5. 状态、日志和停止

```bash
./scripts/status.sh
tail -f .runtime/bill.log
./scripts/stop-all.sh
```

`stop-all.sh` 只停止启动脚本创建并记录 PID 的进程，不会停止脚本运行前已经存在的服务。

验收地址：

- Eureka：<http://127.0.0.1:7001>、<http://127.0.0.1:7002>
- Gateway：<http://127.0.0.1:8181>
- 用户前台：<http://127.0.0.1:8080>
- 管理后台：<http://127.0.0.1:8888>
- 课程演示账号：`admin / 123456`

## 6. 集成回归

正常模式启动后执行：

启动脚本显示完成后，建议等待约 30 秒，让 Gateway 服务发现缓存完成首次刷新，再执行冒烟测试：

```bash
bash scripts/smoke-test.sh
```

请在项目根目录执行；Windows CMD 和 Git Bash 均可使用上述命令。脚本默认使用网站演示账号 `admin / 123456`，与 MySQL 的连接账号密码无关。若网站密码已修改，可以用 `GOUYAN_USERNAME`、`GOUYAN_PASSWORD` 覆盖；Windows CMD 示例：

```cmd
bash -c "GOUYAN_USERNAME=admin GOUYAN_PASSWORD=你的实际网站密码 bash scripts/smoke-test.sh"
```

脚本会验证 Python 是否可用，并以 UTF-8 模式解析接口 JSON，避免 Windows 默认 GBK 编码导致中文订单数据解析失败。

脚本验证登录、Gateway、电影和影院查询、MyBatis 查询、Bill 到 Movie 的 Feign 调用，以及 Ribbon 是否轮询到两个 Movie 实例。

Hystrix 演示需要重新以演示模式启动：

```bash
./scripts/stop-all.sh
MYSQL_PASSWORD='你的本机数据库密码' ./scripts/start-all.sh --backend-only --hystrix-demo
GOUYAN_USERNAME=admin GOUYAN_PASSWORD='课程演示账号密码' \
  ./scripts/smoke-test.sh --expect-hystrix --backend-only
```

最后通过用户前台完成一次写链路验收：选择一个仍有空座的场次，创建订单并模拟支付；刷新订单确认状态为已支付，刷新场次确认座位已售出，并在管理后台确认电影票房只增加一次。对同一订单再次提交支付请求时，票房和座位数不应重复增加。

## 7. 常见问题

### MySQL 未运行或认证失败

- 使用 `mysqladmin ping -uroot -p` 检查服务和密码。
- 确认启动命令设置了正确的 `MYSQL_PASSWORD`。
- 空密码环境不要把示例文字或引号作为密码传入。

### 提示数据库 movie 不存在

重新执行“数据库初始化”中的创建和导入命令，并确认 `SHOW TABLES` 有结果。

### 端口已被占用

执行 `./scripts/status.sh`，再用 `lsof -nP -iTCP:端口 -sTCP:LISTEN` 查找占用进程。先确认进程归属，不要直接终止不属于本项目的服务。

### 服务启动超时

检查 `.runtime/服务名.log`。常见原因是数据库认证失败、Eureka 尚未就绪、端口冲突或 Maven 依赖下载失败。

### 前端提示缺少 node_modules

分别进入 `gouyan-web/gouyan-user` 和 `gouyan-web/gouyan-ui` 执行 `npm install`。Node 20 下启动脚本已自动设置 `NODE_OPTIONS=--openssl-legacy-provider`。

### Feign、Ribbon 或 Hystrix 验证失败

- Gateway 刚启动时先等待约 30 秒，再重试冒烟测试。
- 确认两个 Eureka 页面均能看到全部服务。
- 确认 `GOUYAN-MOVIE` 至少有 9010、9011 两个实例。
- 正常模式验证 Ribbon；只有使用 `--hystrix-demo` 启动时才使用 `--expect-hystrix`。

### 停止后仍有进程

脚本只管理自己记录的 PID 和端口。运行 `./scripts/status.sh` 确认状态，并检查对应 `.runtime` 日志；启动脚本之前已存在的服务需要由其原启动方式停止。
