package com.tudou.tudoumianshi.mapper;

import com.tudou.tudoumianshi.model.entity.Question;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/**
* @author 86147
* @description 针对表【question(题目)】的数据库操作Mapper
* @createDate 2024-10-12 14:23:15
* @Entity generator.domain.Question
*/
public interface QuestionMapper extends BaseMapper<Question> {
    @Select("select * from question where updateTime >= #{minUpdateTime}")
    List<Question> listQuestionWithDelete(Date minUpdateTime);
}




