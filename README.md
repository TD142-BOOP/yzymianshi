项目概述
TudouMianshi 是一个基于 Spring Boot 构建的 Java 后端项目，集成了多种常用的技术栈，包括数据库连接池、Redis、Elasticsearch、MyBatis-Plus、Swagger、Nacos 配置中心、Sa-Token 权限管理等。项目旨在提供一个高效、可扩展的后端服务，支持快速开发和部署。

技术栈
Spring Boot: 项目的基础框架，提供了快速开发的能力。

Spring MVC: 用于处理 HTTP 请求和响应。

Druid: 数据库连接池，提供了强大的监控和统计功能。

MySQL: 项目的主要数据库。

Redis: 用于缓存和分布式 Session 管理。

Elasticsearch: 用于全文搜索和数据分析。

MyBatis-Plus: MyBatis 的增强工具，简化了数据库操作。

Swagger (Knife4j): 用于生成和展示 API 文档。

Nacos: 作为配置中心，支持动态配置管理。

Sa-Token: 轻量级权限管理框架，用于用户认证和授权。

COS (对象存储): 用于文件存储和管理。

Hotkey: 用于热 key 探测和缓存管理。

配置文件说明
项目的配置文件为 application.yml，包含了以下主要配置：

Spring 配置:

应用名称、默认环境、Session 配置、数据库连接池配置等。

支持 Swagger3，配置了路径匹配策略。

数据库配置:

使用 MySQL 数据库，配置了 Druid 连接池的详细参数，包括连接池大小、超时时间、监控统计等。

Redis 配置:

Redis 的连接信息，包括数据库索引、主机地址、端口、超时时间等。

Elasticsearch 配置:

Elasticsearch 的连接信息，包括 URI、用户名和密码。

文件上传配置:

设置了文件上传的大小限制。

MyBatis-Plus 配置:

配置了全局逻辑删除字段、日志输出等。

对象存储配置:

配置了 COS 的访问密钥、区域和存储桶信息。

接口文档配置:

使用 Knife4j 生成 API 文档，配置了文档标题、版本和分组信息。

热 key 探测配置:

配置了热 key 探测的相关参数，如缓存大小、推送周期等。

Nacos 配置中心:

配置了 Nacos 的地址、数据 ID、分组、文件格式和自动刷新功能。

Sa-Token 配置:

配置了 Sa-Token 的 token 名称、有效期、并发登录控制等。

AI 配置:

配置了 AI 服务的 API Key。
