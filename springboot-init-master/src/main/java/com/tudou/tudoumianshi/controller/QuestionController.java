package com.tudou.tudoumianshi.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tudou.tudoumianshi.common.BaseResponse;
import com.tudou.tudoumianshi.common.DeleteRequest;
import com.tudou.tudoumianshi.common.ErrorCode;
import com.tudou.tudoumianshi.common.ResultUtils;
import com.tudou.tudoumianshi.constant.UserConstant;
import com.tudou.tudoumianshi.exception.BusinessException;
import com.tudou.tudoumianshi.exception.ThrowUtils;
import com.tudou.tudoumianshi.manager.CounterManager;
import com.tudou.tudoumianshi.model.dto.question.*;
import com.tudou.tudoumianshi.model.entity.Question;
import com.tudou.tudoumianshi.model.entity.User;
import com.tudou.tudoumianshi.model.vo.QuestionVO;
import com.tudou.tudoumianshi.service.QuestionService;
import com.tudou.tudoumianshi.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;


/**
 * 题目接口
 *
 */
@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {

    @Resource
    private QuestionService questionService;


    @Resource
    private CounterManager counterManager;

    @Resource
    private UserService userService;

    // region 增删改查





    @PostMapping("/ai/generate/question")
    public BaseResponse<Boolean> generateQuestions(@RequestBody QuestionGenerateRequest questionGenerateRequest,
                                             HttpServletRequest request) {
        int questionNum = questionGenerateRequest.getQuestionNum();
        String questionType = questionGenerateRequest.getQuestionType();
        ThrowUtils.throwIf(questionNum <= 0, ErrorCode.PARAMS_ERROR,"题目数量必须大于0");
        ThrowUtils.throwIf(StringUtils.isAnyBlank(questionType), ErrorCode.PARAMS_ERROR,"题目类型不能为空");

        User loginUser = userService.getLoginUser(request);
        Boolean result = questionService
                .aiGenerateQuestions(questionType, questionNum, loginUser);
        return ResultUtils.success(result);


    }
    /**
     * 创建题目
     * @param questionAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(questionAddRequest == null, ErrorCode.PARAMS_ERROR);
        // todo 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionAddRequest, question);
        // 数据校验
        questionService.validQuestion(question, true);
        // todo 填充默认值
        User loginUser = userService.getLoginUser(request);
        question.setUserId(loginUser.getId());
        // 写入数据库
        boolean result = questionService.save(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 返回新写入的数据 id
        long newQuestionId = question.getId();
        return ResultUtils.success(newQuestionId);
    }

    /**
     * 删除题目
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestion.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新题目（仅管理员可用）
     *
     * @param questionUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        if (questionUpdateRequest == null || questionUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // todo 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionUpdateRequest, question);
        // 数据校验
        questionService.validQuestion(question, false);
        // 判断是否存在
        long id = questionUpdateRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


//    @Value("${nacos.config.data-id}")
//    private String dataId;
//
//    @Value("${nacos.config.server-addr}")
//    private String serverAddr;
//
//    @Value("${nacos.config.group}")
//    private String group;
//    private void updateNacosBlackList(String ip){
//        Properties properties = new Properties();
//        properties.put("serverAddr", serverAddr);
//
//        try {
//            // 创建ConfigService
//            ConfigService configService = NacosFactory.createConfigService(properties);
//
//            // 从Nacos获取配置
//            String content = configService.getConfig(dataId, group, 5000);
//            System.out.println("获取到的YAML配置内容: " + content);
//
//            // 解析YAML
//            Yaml yaml = new Yaml();
//            Map<String, Object> yamlMap = yaml.load(content);
//
//            // 获取黑名单IP列表
//            List<String> blacklist = (List<String>) yamlMap.get("blackIpList");
//            // 添加新的IP到黑名单
//            if (!blacklist.contains(ip)) {
//                blacklist.add(ip);
//                System.out.println("添加新的IP到黑名单: " + ip);
//            }
//
//            // 将更新后的YAML内容发布到Nacos
//            String updatedContent = yaml.dump(yamlMap);
//            boolean isPublishOk = configService.publishConfig(dataId, group, updatedContent);
//            if (isPublishOk) {
//                System.out.println("配置更新成功");
//            } else {
//                System.out.println("配置更新失败");
//            }
//        } catch (NacosException e) {
//            e.printStackTrace();
//        }
//    }


//    /**
//     * getQuestionVOById
//     * 监控用户访问接口频率，发现爬虫用户直接封号
//     */
//    public BaseResponse<QuestionVO> handleBlockException(long id, HttpServletRequest request, BlockException ex) {
//        User loginUser = userService.getLoginUser(request);
//        String ip = IpUtils.getClientIp(request);
//        // 踢下线
//        StpUtil.kickout(loginUser.getId());
//        // ip更新到nacos
//        updateNacosBlackList(ip);
//        // 限流操作
//        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "访问太频繁，已封号");
//    }
    /**
     * 根据 id 获取题目（封装类）
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    //@LimitCheck
//    @SentinelResource(value = "getQuestionVOById",
//            blockHandler = "handleBlockException")
    public BaseResponse<QuestionVO> getQuestionVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUserPermitNull(request);
        ThrowUtils.throwIf(loginUser==null,ErrorCode.NOT_LOGIN_ERROR);
        crawlerDetect(loginUser.getId());
        // 检测爬虫
        // 查询数据库
        Question question = questionService.getById(id);
        //String key="question_detail_" + id;
//        if (JdHotKeyStore.isHotKey(key)) {
//            //注意是get，不是getValue。getValue会获取并上报，get是纯粹的本地获取
//            Object cachedQuestionVO = JdHotKeyStore.get(key);
//            if(cachedQuestionVO != null) {
//                return ResultUtils.success((QuestionVO) cachedQuestionVO);
//            }
//        }
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        //JdHotKeyStore.smartSet(key,question);
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVO(question, request));
    }

    /**
     * 检测爬虫
     *
     * @param loginUserId
     */

    private void crawlerDetect(long loginUserId) {
//        // 调用多少次时告警
//        final int WARN_COUNT = warnCount;
//        // 超过多少次封号
//        final int BAN_COUNT = banCount;
        // 调用多少次时告警
        final int WARN_COUNT = 10;
        // 超过多少次封号
        final int BAN_COUNT = 20;
        // 拼接访问 key
        String key = String.format("user:access:%s", loginUserId);
        // 一分钟内访问次数，180 秒过期
        long count = counterManager.incrAndGetCounter(key);
        // 是否封号
        if (count > BAN_COUNT) {
            // 踢下线
            StpUtil.kickout(loginUserId);
            // 封号
            User updateUser = new User();
            updateUser.setId(loginUserId);
            updateUser.setUserRole("ban");
            userService.updateById(updateUser);
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "访问太频繁，已被封号");
        }
        // 是否告警
        if (count == WARN_COUNT) {
            // 可以改为向管理员发送邮件通知
            throw new BusinessException(110, "警告访问太频繁");
        }
    }


    /**
     * 分页获取题目列表（仅管理员可用）
     *
     * @param questionQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest,HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<Question> questions = questionService.listQuestionQueryByPage(questionQueryRequest,request);
        return ResultUtils.success(questions);
    }

    /**
     * 分页获取题目列表（封装类）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                               HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.listQuestionQueryByPage(questionQueryRequest,request);
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 分页获取题目列表（封装类）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo/sentinel")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPageSentinel(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                               HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 获取客户端 IP 地址
        String remoteAddr = request.getRemoteAddr();
        Entry entry = null;
        try {
            entry = SphU.entry("listQuestionVOByPage", EntryType.IN, 1, remoteAddr);
            // 被保护的业务逻辑
            // 查询数据库
            Page<Question> questionPage = questionService.listQuestionQueryByPage(questionQueryRequest, request);
            // 获取封装类
            return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
        } catch (Throwable ex) {
            // 业务异常
            if (!BlockException.isBlockException(ex)) {
                Tracer.trace(ex);
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
            }
            // 降级操作
            if (ex instanceof DegradeException) {
                return handleFallback(questionQueryRequest, request, ex);
            }
            // 限流操作
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "访问过于频繁，请稍后再试");
        } finally {
            if (entry != null) {
                entry.exit(1, remoteAddr);
            }
        }
    }

    /**
     * listQuestionVOByPage 降级操作：直接返回本地数据
     */
    public BaseResponse<Page<QuestionVO>> handleFallback(QuestionQueryRequest questionQueryRequest,
                                                         HttpServletRequest request, Throwable ex) {
        // 可以返回本地数据或空数据
        return ResultUtils.success(null);
    }

    /**
     * 分页获取当前登录用户创建的题目列表
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        questionQueryRequest.setUserId(loginUser.getId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 编辑题目（给用户使用）
     *
     * @param questionEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest, HttpServletRequest request) {
        if (questionEditRequest == null || questionEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // todo 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionEditRequest, question);
        List<String> tags = questionEditRequest.getTags();
        if (tags != null && !tags.isEmpty()) {
            question.setTags(StringUtils.join(tags));
        }
        // 数据校验
        questionService.validQuestion(question, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = questionEditRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestion.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    private RestHighLevelClient client;

    public void ElasticsearchHealthChecker(String hostname, int port) {
        // 创建Elasticsearch客户端
        client = new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost(hostname, port, "http")
                )
        );
    }
    /**
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/search/page/vo")
    public BaseResponse<Page<QuestionVO>> searchQuestionVOByPage(
            @RequestBody QuestionQueryRequest questionQueryRequest,
            HttpServletRequest request) throws IOException {

        // 1. 参数校验（预防性降级）
//        long size = questionQueryRequest.getPageSize();
//        ThrowUtils.throwIf(size > 200, ErrorCode.PARAMS_ERROR);
//
//        try {
//            // 2. 检查 ES 健康状态（主动降级判断）
//            ElasticsearchHealthChecker("localhost", 9200);
//            ClusterHealthRequest healthRequest = new ClusterHealthRequest();
//            ClusterHealthResponse health = client.cluster().health(healthRequest, RequestOptions.DEFAULT);
//            ClusterHealthStatus status = health.getStatus();
//
//            if (status == ClusterHealthStatus.RED) { // 仅当 RED 状态才降级
//                log.warn("ES 集群不可用，降级到 MySQL");
//                return fallbackToMySQL(questionQueryRequest, request);
//            }
//
//            // 3. 尝试 ES 查询（带超时控制）
//            Page<Question> questionPage = questionService.searchFromEs(questionQueryRequest);
//            return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
//
//        } catch (Exception e) {
            // 4. 异常降级（如超时、连接失败）
            //log.error("ES 查询异常，降级到 MySQL", e);
            return fallbackToMySQL(questionQueryRequest, request);
//        }
    }

    // 降级方法
    private BaseResponse<Page<QuestionVO>> fallbackToMySQL(
            QuestionQueryRequest questionQueryRequest,
            HttpServletRequest request) {
        Page<Question> questionPage = questionService.listQuestionQueryByPage(questionQueryRequest, request);
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 批量删除题目
     * @param questionBatchDeleteRequest
     * @return
     */
    @PostMapping("/delete/batch")
    public BaseResponse<Boolean> batchDeleteQuestions(@RequestBody QuestionBatchDeleteRequest questionBatchDeleteRequest) {
        ThrowUtils.throwIf(questionBatchDeleteRequest==null, ErrorCode.PARAMS_ERROR);
        List<Long> questionIdList = questionBatchDeleteRequest.getQuestionIdList();
        ThrowUtils.throwIf(questionIdList==null||questionIdList.isEmpty(), ErrorCode.PARAMS_ERROR);
        questionService.batchDeleteQuestionToBank(questionIdList);
        return ResultUtils.success(true);
    }

    // endregion
}
