# Tudou 面试答题系统 (tudoumianshi)

## 项目概述

下面从项目背景、技术选型、架构及模块划分、开发／运行／部署全流程，以及设计初衷这几部分来介绍。

### 一、项目背景与目的

- **项目名称**：Tudou 面试答题系统（tudoumianshi）
- **目标**：
  - 为面向"面试题库＋模拟面试＋点赞排行＋全文检索"四个核心场景提供一套完整可运行、可扩展的示例系统；
  - 集成常见企业级中间件（MySQL、Redis、Elasticsearch、Pulsar、Nacos、Sentinel、Sa-Token 等），作为 Spring Boot 实战脚手架；
  - 演示分层架构、异步消息、定时任务、全量／增量数据同步、API 文档、全局异常、代码生成等最佳实践。

### 二、技术选型

- **Java & Spring Boot**：Java 8 + Spring Boot 2.7.2
- **Web 框架**：Spring MVC + Freemarker + Knife4j（Swagger UI）
- **ORM**：MyBatis + MyBatis-Plus（XML 映射 + 注解）
- **缓存**：Redis（Spring-Data-Redis + Redisson）、布隆过滤、HotKey
- **消息**：Apache Pulsar（异步点赞消息）
- **搜索**：Elasticsearch（MySQL ↔ ES 全量/增量同步）
- **认证**：Sa-Token（分布式 Session 存储于 Redis）
- **配置中心**：Nacos Config
- **熔断限流**：Sentinel
- **数据库**：MySQL（建表与初始数据脚本位于 `sql/`）
- **工具**：Hutool、Volcengine AI SDK、Druid 数据源（去广告）、Maven Wrapper、Docker

### 三、架构及模块划分

项目采用典型的分层架构，模块划分如下：

```text
src/main/java/com/tudou/tudoumianshi
├─ controller    // 接口层（REST API）
├─ service       // 业务层接口
├─ manager       // 业务实现（可封装更高一层业务流程）
├─ mapper        // MyBatis Mapper 接口
├─ model         // 实体（entity）、DTO/VO、枚举
├─ aop           // 切面，如日志、限流、鉴权
├─ config        // 各种中间件配置类
├─ listener      // Pulsar 消息监听
├─ job           // 定时任务／一次性任务
├─ exception     // 全局异常处理与返回体封装
├─ utils         // 公共工具方法
├─ generate      // 代码生成器（Freemarker 模板）
└─ …             // 其他辅助模块
│
resources
├─ mapper        // XML 映射文件
├─ templates     // Freemarker 模板
└─ sql           // 建表与初始数据脚本
```
![{04C343D3-2820-4E57-A7B7-7BAB89C1B8B9}](https://github.com/user-attachments/assets/6f170852-4bc3-40d8-a3bc-2eb45d00a071)

真实模拟企业级服务场景，整合主流中间件，帮助快速搭建新项目。
- 提供面试题管理与模拟面试功能，在学习和二次开发中复用。
- 演示异步消息、分布式缓存、全文检索、配置中心、监控报警、鉴权权限、动态代码生成等最佳实践。
- 为开发者提供一套可扩展、可维护、可落地的实战脚手架。
