# Spring Boot初始化项目

## 项目简介
一个基于Spring Boot的脚手架，集成主流技术栈（Redis/Elasticsearch/Pulsar等），提供开箱即用的：
- **用户权限管理**（Sa-Token）
- **题库与面试系统**（ES全文检索）
- **高性能点赞服务**（异步消息队列）
- **分布式基础设施**（锁/限流/热点检测）

## 技术栈
| 技术          | 用途                     | 配置文件位置               |
|---------------|--------------------------|---------------------------|
| Spring Boot 2 | 核心框架                 | `pom.xml`                 |
| MyBatis-Plus  | ORM框架                  | `MyBatisPlusConfig.java`  |
| Sa-Token      | 认证授权                 | `SaTokenConfigure.java`   |
| Redis         | 缓存/分布式锁            | `RedisConfig.java`        |
| Pulsar        | 消息队列                 | `PulsarConfig.java`       |
| Elasticsearch | 题库全文检索             | `QuestionEsDao.java`      |
| Docker        | 容器化部署               | `Dockerfile`              |

## 快速启动
### 环境准备
```bash
# 需提前安装：
- JDK 17+
- MySQL 8.0
- Redis 6.x
- Elasticsearch 7.x
```

## 核心功能
### 1. 用户系统
- 登录/注册（JWT令牌）
- 角色权限控制（`UserRoleEnum`）
- 敏感操作审计（`LogInterceptor`）

### 2. 题库服务
- 题目CRUD（`QuestionController`）
- 题库分类管理（`QuestionBankService`）
- ES同步任务（`FullSyncQuestionToEs`）

### 3. 分布式工具
- 布隆过滤器（`BlackIpFilter`）
- 热点Key检测（`HotKeyAspect`）
- 分布式锁（`DistributedLockAspect`）
  <img width="951" height="792" alt="{CC399C1F-5DC0-461D-8876-0F8164F700DF}" src="https://github.com/user-attachments/assets/807e46b7-a840-4a63-93af-fec6c0e8dd8f" />

