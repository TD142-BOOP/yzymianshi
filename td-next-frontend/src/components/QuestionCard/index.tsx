"use client";
import { useState } from "react";
import { Card } from "antd";
import Title from "antd/es/typography/Title";
import TagList from "@/components/TagList";
import MdViewer from "@/components/MdViewer";
import useAddUserSignInRecord from "@/hooks/useAddUserSignInRecord";
import { Button, message } from "antd";
import { LikeOutlined, LikeFilled } from "@ant-design/icons";
import { doThumbUsingPost, undoThumbUsingPost } from "@/api/thumbController";
import "./index.css";

interface Props {
  question: API.QuestionVO;
}

/**
 * 题目卡片
 * @param props
 * @constructor
 */
const QuestionCard = (props: Props) => {
  const { question } = props;

  // 签到
  useAddUserSignInRecord();

  // 状态
  const [thumbCount, setThumbCount] = useState(question.thumbCount ?? 0);
  const [hasThumb, setHasThumb] = useState(question.hasThumb ?? false);

  // 点击点赞按钮的处理函数
  const handleThumbClick = async () => {
    try {
      if (hasThumb) {
        await undoThumbUsingPost({ questionId: question.id! });
        setThumbCount((count) => count - 1);
        setHasThumb(false);
        message.success("取消点赞成功");
      } else {
        await doThumbUsingPost({ questionId: question.id! });
        setThumbCount((count) => count + 1);
        setHasThumb(true);
        message.success("点赞成功");
      }
    } catch (error: any) {
      message.error("操作失败：" + error.message);
    }
  };

  return (
    <div className="question-card">
      <Card
        extra={
          <Button
            type="text"
            onClick={handleThumbClick}
            icon={hasThumb ? <LikeFilled style={{ color: "#1890ff" }} /> : <LikeOutlined />}
          >
            {thumbCount}
          </Button>
        }
      >
        <Title level={1} style={{ fontSize: 24 }}>
          {question.title}
        </Title>
        <TagList tagList={question.tagList} />
        <div style={{ marginBottom: 16 }} />
        <MdViewer value={question.content} />
      </Card>
      <div style={{ marginBottom: 16 }} />
      <Card title="推荐答案">
        <MdViewer value={question.answer} />
      </Card>
    </div>
  );
};

export default QuestionCard;
