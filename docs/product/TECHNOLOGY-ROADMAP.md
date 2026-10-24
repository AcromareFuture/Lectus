# 技术接入与建设顺序

适用于国内多商家商城、官方自营店及用户社区。依据 2026-10-24 的项目代码和当前服务器资源制定；下列新增项均为建议，未开通云服务，也未产生订阅费用。

所有接入项同时遵守[性能约束与验收](PERFORMANCE.md)：先消除已发现的社区逐条查询与前端入口加载问题，建立基线后再按瓶颈扩展组件。微服务数量和技术栈丰富度不作为性能指标。

## 现有能力与实际边界

- 用户端 React 19、Vite、Lucide、model-viewer；商家及平台端 Vue 3、TypeScript。沿用三端各自工作流，不为统一框架重写现有页面。
- Java 21、Spring Boot、Spring Cloud Gateway、JDBC / MyBatis-Plus、Flyway、PostgreSQL；目录服务已有可选 Redis 缓存。是否生产启用需按部署配置核实，不能将 Compose 清单视为运行状态。
- 会员服务已有阿里云短信 SDK、社区上传/互动、商城交易接口。社区媒体当前在本机持久目录；推荐按发布时间排序。已有 AI 控制器默认关闭，不代表用户端已接入可用 AI 导购。
- 核心服务已有 Actuator、部分服务有 Micrometer Prometheus 依赖，尚未形成完整监控告警体系。会员服务仍承载多个业务域，框架版本与其他核心服务不完全一致。
- 支付、物流、结算以渠道实际回调和验收为准，页面和本地交易逻辑不能作为已收款、已退款的依据。
- 当前生产主机内存约 7.3 GiB，检查时可用仅约 283 MiB，未配置 swap。新增常驻 JVM、搜索引擎、消息队列或监控全家桶之前需要独立容量规划。不要将下列组件全部塞进现有主机。

## 优先接入

| 顺序 | 选型 | 解决的问题 | 接入边界与完成条件 |
| --- | --- | --- | --- |
| P0 | 阿里云 OSS + CDN | 本地磁盘占满、图片加载慢、单机故障丢媒体 | 服务端签发短期上传凭证；私有暂存区；上传完成校验 MIME、大小、归属与校验和；审核通过后公开；生命周期清理孤立文件；保留本地适配器与迁移回退 |
| P0 | 阿里云内容安全 + 平台人工审核 | 图片、文字、视频缺少审核/举报闭环 | 发布状态 pending / published / rejected / hidden；审核失败不公开；超时进入待处理队列；人工复审、举报、申诉与审计日志；先使用非用户素材验证，再确定真实内容处理范围 |
| P0 | 云视频处理 MPS（或托管转码服务） | 手机视频格式不一致、文件过大、首帧慢 | 私有原片 → 异步转码 H.264/AAC → 封面 → 审核 → 发布；回调验签、幂等、重试；720p/1080p 按需生成；不要在会员服务请求线程里调用 FFmpeg |
| P0 | 微信支付 API v3 / 支付宝开放平台，择需接入 | 真实支付、原路退款与账单对账 | 先明确平台与商家的资金关系及进件资质；平台型业务评估电商收付通/服务商产品；支付金额来自订单快照；回调验签和防重、主动查单、退款状态机、日账单对账齐备才算完成 |
| P1 | Sentry（前端）+ OpenTelemetry（后端） | 白屏、请求失败难以复现，跨服务调用难定位 | sourcemap 私有上传；统一 requestId / traceId；遮蔽手机号、地址、Token 与请求正文；按环境分流；前端失败可关联服务端 trace；Sentry 先托管或独立部署 |
| P1 | Prometheus + Grafana，日志用 Loki 或现有云日志 | 只知道服务进程在，不知道业务是否失败 | 网关延迟与错误率、PG 连接池、上传失败、待审核积压、支付回调失败、磁盘与备份告警；采集端点只在内部可访问；日志/指标设置保留周期和采样 |
| P1 | 快递鸟或快递100，选择一家 | 运单信息依赖人工录入和推进 | 服务端订阅轨迹、回调验签和去重；支持异常件；展示信息来源和最后更新时间；“已签收”与用户“确认收货”保持业务区分 |

## 规模增长后再增加

| 能力 | 推荐起点 | 升级条件与替代选择 |
| --- | --- | --- |
| 商品与社区搜索 | PostgreSQL 索引、`pg_trgm`，品牌/类目/价格结构化筛选 | `pg_trgm` 有利于名称与相似匹配，不能当作完整中文分词。中文相关性、同义词、拼写容错需求明显后，再评估 Meilisearch 中文效果；复杂搜索和聚合用 OpenSearch + 中文分析器。不要同时维护两套搜索引擎 |
| 语义检索 | PostgreSQL `pgvector`，先离线评估 | 需要实际商品库、中文 embedding 和召回评估集；价格、库存、租户/商家可见性仍由业务库过滤。没有向量检索需求时不部署向量服务 |
| 异步任务 | PG 任务表 + `FOR UPDATE SKIP LOCKED`、重试和死信状态 | 先满足转码、审核、通知。多消费者、吞吐与隔离需求上来后再接 RabbitMQ；事件由事务内 Outbox 写入、消费者幂等，不能靠“发消息成功”等同订单成功 |
| 实时状态 | SSE，服务端推送审核、互动及订单状态 | 包含鉴权、心跳、断线重连、事件游标、离线补拉；用户私信需要双向低延迟后再选择 WebSocket，不把所有接口改成 WebSocket |
| 热点读与限流 | 现有 Redis + Caffeine 短缓存 | 先明确缓存键、失效策略、TTL、击穿保护与降级；Redis 不替代 PG 库存/资金事务；分布式锁必须有租期与所有权校验 |
| 业务韧性 | Resilience4j + HTTP 超时预算 | 短信、审核、物流等外部服务独立限流/熔断；禁止无界重试，支付写请求按幂等键处理；失败进入可重试状态 |
| 测试 | Testcontainers（真实 PostgreSQL）、Playwright E2E、k6 | 覆盖迁移、并发库存、Outbox 幂等、手机端发布、第三方回调；压测在隔离环境执行，不对现有生产商城施压 |
| 接口规范 | springdoc-openapi + OpenAPI 生成 TypeScript 客户端 | 统一错误码、分页、幂等键和上传约定；DTO 必须脱敏，后台接口不因文档生成对外公开；React 和 Vue 可共用契约，不共用页面实现 |
| 发布与恢复 | CI 构建与检查、版本目录原子切换、pgBackRest/WAL 备份 | 私有制品、独立凭据、可回滚迁移、数据库恢复演练；先确保可恢复，再追求编排平台。当前不优先上 Kubernetes 或 Service Mesh |

## 能增强业务的产品接入

1. **内容关联商品**：发布时选商品、详情展示关联商品、已购买用户晒单标记。只允许关联已审核可见商品，实时价格/库存从交易接口读取。这比初期复杂推荐算法更直接。
2. **互动与订单消息中心**：评论、关注、审核、发货通知统一进入站内信；短信仅用于必要事务消息。推送按用户设置、幂等与频控执行。
3. **导购和商家写作辅助**：通义千问通过服务端网关调用，先做基于真实商品查询的问答、商品文案辅助。价格、优惠、库存与订单状态必须查接口；模型不决定扣款、退款或权限。`pgvector` 只在检索评估证明有效后增加。
4. **轻量行为分析**：自托管 Umami 或服务端事件统计，关注发布成功率、加购率、结算漏斗和错误率；先定义事件、目的、脱敏和用户选择，再部署 SDK。不要为了统计采集评论正文、地址或手机号。
5. **商家/平台端运营补齐**：审核队列、失败任务重试、媒体资源管理、举报处理、订单异常处理，比新增仪表盘装饰更有价值。

## 推荐落地顺序

第一阶段先完成 OSS、审核和平台处理队列，补齐错误追踪、备份恢复；确定实际收款渠道与资质。第二阶段接视频异步处理、真实物流、内容关联商品及站内通知。第三阶段根据真实数据决定搜索引擎、语义检索、推荐算法及独立高性能组件。

Java 继续负责业务主流程。Go 只在测量确认存在热点时用于媒体任务执行器、推送网关或高并发只读聚合；独立部署、共享 OpenAPI/事件契约，不让 Go 与 Java 同时修改同一业务表。前端本轮使用浏览器原生 `<dialog>`、IntersectionObserver、sessionStorage 与现有 React 实现交互，未为这些能力引入新运行时依赖。

## 官方资料

- [OSS](https://help.aliyun.com/zh/oss/) / [内容安全](https://help.aliyun.com/zh/content-moderation/) / [媒体处理](https://help.aliyun.com/zh/mps/)
- [微信支付](https://pay.weixin.qq.com/doc/v3/) / [支付宝开放平台](https://open.alipay.com/) / [快递100](https://api.kuaidi100.com/)
- [PostgreSQL pg_trgm](https://www.postgresql.org/docs/current/pgtrgm.html) / [pgvector](https://github.com/pgvector/pgvector) / [OpenSearch](https://docs.opensearch.org/)
- [OpenTelemetry Java](https://opentelemetry.io/docs/languages/java/) / [Sentry React](https://docs.sentry.io/platforms/javascript/guides/react/) / [Grafana](https://grafana.com/docs/)
- [RabbitMQ](https://www.rabbitmq.com/docs) / [Resilience4j](https://resilience4j.readme.io/docs) / [Testcontainers](https://java.testcontainers.org/) / [Playwright](https://playwright.dev/docs/intro)
