package com.tudou.tudoumianshi.mapper;

import com.tudou.tudoumianshi.model.entity.Thumb;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author 86147
* @description 针对表【thumb】的数据库操作Mapper
* @createDate 2025-04-18 10:41:39
* @Entity generator.domain.Thumb
*/
public interface ThumbMapper extends BaseMapper<Thumb> {
    
    /**
     * 查询点赞过某题目的所有用户ID
     * @param questionId 题目ID
     * @return 用户ID列表
     */
    @Select("SELECT userId FROM thumb WHERE questionId = #{questionId}")
    List<Long> getThumbUserIdsByQuestionId(Long questionId);
}




