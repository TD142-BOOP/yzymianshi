**项目概述**

下面从项目背景、技术选型、架构及模块划分、开发/运行/部署全流程，以及设计初衷这几部分来介绍。
一、项目背景与目的
项目名称：Tudou 面试答题系统（tudoumianshi）
目标：
为面向“面试题库＋模拟面试＋点赞排行＋全文检索”四个核心场景提供一套完整可运行、可扩展的示例系统；
集成常见企业级中间件（MySQL、Redis、Elasticsearch、Pulsar、Nacos、Sentinel、Sa-Token 等），作为 Spring Boot 实战脚手架；
演示分层架构、异步消息、定时任务、全量/增量同步、API 文档、全局异常、代码生成等最佳实践。
二、技术选型
Java 8 + Spring Boot 2.7.2
Web 框架：Spring MVC + Knife4j（Swagger UI）
ORM：MyBatis + MyBatis-Plus（XML 映射 + Annotation）
缓存：Redis（Spring-Data-Redis + Redisson）、布隆过滤、HotKey
消息：Apache Pulsar（异步点赞消息）
搜索：Elasticsearch（MySQL↔ES 全量/增量同步任务）
认证：Sa-Token（分布式 Session 存储于 Redis）
配置：Nacos Config
熔断限流：Sentinel
数据库：MySQL（建表与初始数据脚本位于 sql/）
工具：Hutool,Volcengine AI SDK、Druid 数据源（去广告）、Maven Wrapper、Docker
src/main/java/com/tudou/tudoumianshi
├─ controller    // 接口层（REST API）
├─ service       // 业务层接口
├─ manager       // 业务实现（可封装更高一层业务流程）
├─ mapper        // MyBatis Mapper 接口 + XML 文件（resources/mapper）
├─ model         // 实体（entity）、传输对象（DTO/VO）、枚举  
├─ aop           // 切面，如日志、限流、鉴权  
├─ config        // 各种中间件配置类（Redis、Pulsar、ES、Sa-Token、Druid…）  
├─ listener      // Pulsar 消息监听  
├─ job           // 定时任务／一次性任务（DB↔ES、异步点赞数据落库补偿）  
├─ exception     // 全局异常处理与返回体封装  
├─ utils         // 公共工具方法封装  
└─ …  
![{04C343D3-2820-4E57-A7B7-7BAB89C1B8B9}](https://github.com/user-attachments/assets/6f170852-4bc3-40d8-a3bc-2eb45d00a071)
实模拟企业级微服务场景，整合主流中间件，夯实技术栈能力；
提供完整的面试题管理＆模拟面试系统，适合复用或二次开发；
演示异步消息、分布式缓存、分布式限流、全文检索、配置中心、监控报警、鉴权与权限、动态代码生成等全栈实践.
