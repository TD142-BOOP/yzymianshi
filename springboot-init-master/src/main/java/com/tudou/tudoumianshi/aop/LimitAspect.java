package com.tudou.tudoumianshi.aop;

import cn.dev33.satoken.stp.StpUtil;
import com.tudou.tudoumianshi.annotation.LimitCheck;
import com.tudou.tudoumianshi.common.ErrorCode;
import com.tudou.tudoumianshi.exception.BusinessException;
import com.tudou.tudoumianshi.manager.CounterManager;
import com.tudou.tudoumianshi.manager.HotKeyCounterManager;
import com.tudou.tudoumianshi.model.entity.User;
import com.tudou.tudoumianshi.service.UserService;
import com.tudou.tudoumianshi.utils.IpUtils;
import org.aspectj.lang.annotation.Before;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

//@Aspect
//@Component
public class LimitAspect {
    @Resource
    private CounterManager counterManager;

    @Resource
    private HotKeyCounterManager hotKeyCounterManager;

    @Resource
    private UserService userService;

    @Before("@annotation(limitCheck)")
    public void beforeMethod(LimitCheck limitCheck){
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 显式获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        String ip = IpUtils.getClientIp(request);
        crawlerDetect(loginUser.getId(), ip);
    }

//    @NacosValue(value = "${warnCount}", autoRefreshed = true)
//    private Integer warnCount;
//
//    @NacosValue(value = "${banCount}", autoRefreshed = true)
//    private Integer banCount;

    /**
     * 检测爬虫
     * @param loginUserId
     */
//    private void crawlerDetect(long loginUserId, String ip) {
//        // 调用多少次时告警
//        final int WARN_COUNT = warnCount;
//        // 超过多少次封号
//        final int BAN_COUNT = banCount;
        // 调用多少次时告警
//        final int WARN_COUNT = 10;
//        // 超过多少次封号
//        final int BAN_COUNT = 20;
//        // 拼接访问 key
//        String key = String.format("user:access:%s", loginUserId);
//        // 一分钟内访问次数，180 秒过期
//        long count = counterManager.incrAndGetCounter(key);
//        // 是否封号
//        if (count > BAN_COUNT) {
//            // 踢下线
//            StpUtil.kickout(loginUserId);
//            // 封号
//            updateNacosBlackList(ip);
//            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "访问太频繁，已被封号");
//        }
//        // 是否告警
//        if (count == WARN_COUNT) {
//            // 可以改为向管理员发送邮件通知
//            throw new BusinessException(110, "访问太频繁，请稍后再试");
//        }
//    }


    /**
     * 检测爬虫
     * @param loginUserId
     */
    private void crawlerDetect(long loginUserId, String ip) {
        // 拼接访问 key
        String key = String.format("user:access:%s", loginUserId);
        // 一分钟内访问次数，180 秒过期
        Boolean result = hotKeyCounterManager.incrAndGetCounter(key);
        // 是否封号
        if (result) {
            // 踢下线
            StpUtil.kickout(loginUserId);
            // 封号
            //updateNacosBlackList(ip);
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "访问太频繁，已被封号");
        }
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
}
