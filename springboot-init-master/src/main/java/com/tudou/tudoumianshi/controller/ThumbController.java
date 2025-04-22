package com.tudou.tudoumianshi.controller;

import com.tudou.tudoumianshi.common.BaseResponse;
import com.tudou.tudoumianshi.common.ResultUtils;
import com.tudou.tudoumianshi.model.dto.thumb.DoThumbRequest;
import com.tudou.tudoumianshi.service.ThumbService;
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

    @Resource
    private ThumbService thumbService;

    @PostMapping("/do")
    public BaseResponse<Boolean> doThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request) {
        Boolean success = thumbService.doThumb(doThumbRequest, request);
        return ResultUtils.success(success);
    }
    @PostMapping("/undo")
    public BaseResponse<Boolean> undoThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request) {
        Boolean success = thumbService.undoThumb(doThumbRequest, request);
        return ResultUtils.success(success);
    }

}
