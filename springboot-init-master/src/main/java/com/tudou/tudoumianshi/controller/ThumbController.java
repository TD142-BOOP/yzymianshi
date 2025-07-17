package com.tudou.tudoumianshi.controller;

import com.tudou.tudoumianshi.common.BaseResponse;
import com.tudou.tudoumianshi.common.ErrorCode;
import com.tudou.tudoumianshi.common.ResultUtils;
import com.tudou.tudoumianshi.model.dto.thumb.DoThumbRequest;
import com.tudou.tudoumianshi.service.ThumbService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 点赞记录表接口
 *

 */
@RestController
@RequestMapping("/thumb")
@Slf4j
public class ThumbController {
    private final Counter successCounter;
    private final Counter failureCounter;

    public ThumbController(MeterRegistry registry) {
        this.successCounter = Counter.builder("thumb.success.count")
                .description("Total successful thumb")
                .register(registry);
        this.failureCounter = Counter.builder("thumb.failure.count")
                .description("Total failed thumb")
                .register(registry);
    }

    @Resource
    private ThumbService thumbService;
//
//    @PostMapping("/do")
//    public BaseResponse<Boolean> doThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request) {
//        Boolean success = thumbService.doThumb(doThumbRequest, request);
//        return ResultUtils.success(success);
//    }
    @PostMapping("/do")
    public BaseResponse<Boolean> doThumb(@RequestBody DoThumbRequest doThumbRequest,
                                HttpServletRequest request) {
        try {
            boolean result = thumbService.doThumb(doThumbRequest, request);
            if (result) {
                // 记录成功计数
                successCounter.increment();
                return ResultUtils.success(true);
            } else {
                // 记录失败计数
                failureCounter.increment();
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
            }
        } catch (Exception e) {
            // 记录失败计数
            failureCounter.increment();
            return ResultUtils.error(ErrorCode.valueOf(e.getMessage()));
        }
    }

    @PostMapping("/undo")
    public BaseResponse<Boolean> undoThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request) {
        Boolean success = thumbService.undoThumb(doThumbRequest, request);
        return ResultUtils.success(success);
    }

}
