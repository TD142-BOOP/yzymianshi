package com.tudou.tudoumianshi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tudou.tudoumianshi.model.dto.thumb.DoThumbRequest;
import com.tudou.tudoumianshi.model.entity.Thumb;

import javax.servlet.http.HttpServletRequest;

/**
 * 点赞记录表服务
 *

 */
public interface ThumbService extends IService<Thumb> {


    /**
     * 取消点赞
     * @param doThumbRequest
     * @param request
     * @return {@link Boolean }
     */
    Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);


    /**
     * 点赞
     * @param doThumbRequest
     * @param request
     * @return {@link Boolean }
     */
    Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    Boolean hasThumb(Long questionId, Long userId);


}
