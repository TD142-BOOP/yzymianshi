package com.tudou.tudoumianshi.model.vo;

import com.tudou.tudoumianshi.model.entity.Thumb;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 点赞记录表视图
 *

 */
@Data
public class ThumbVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 标签列表
     */
    private List<String> tagList;

    /**
     * 创建用户信息
     */
    private UserVO user;

    /**
     * 封装类转对象
     *
     * @param thumbVO
     * @return
     */
    public static Thumb voToObj(ThumbVO thumbVO) {
        if (thumbVO == null) {
            return null;
        }
        Thumb thumb = new Thumb();
        BeanUtils.copyProperties(thumbVO, thumb);
        return thumb;
    }

    /**
     * 对象转封装类
     *
     * @param thumb
     * @return
     */
    public static ThumbVO objToVo(Thumb thumb) {
        if (thumb == null) {
            return null;
        }
        ThumbVO thumbVO = new ThumbVO();
        BeanUtils.copyProperties(thumb, thumbVO);
        return thumbVO;
    }
}
