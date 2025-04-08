"use client";
import { Button, Flex, Menu } from "antd";
import { getQuestionBankVoByIdUsingGet } from "@/api/questionBankController";
import { getQuestionVoByIdUsingGet } from "@/api/questionController";
import Sider from "antd/es/layout/Sider";
import { Content } from "antd/es/layout/layout";
import Link from "next/link";
import "./index.css";
import { useEffect, useState } from "react";
import Title from "antd/es/typography/Title";
import QuestionCard from "@/components/QuestionCard";

/**
 * 题库题目详情页
 * @constructor
 */
export default function BankQuestionPage({ params }: any) {
  const { questionBankId, questionId } = params;

  // 用于存储题库详情
  const [bank, setBank]: any = useState(null);
  // 用于存储题目详情
  const [question, setQuestion]: any = useState(null);
  // 用于表示数据是否正在加载中
  const [loading, setLoading] = useState(true);
  // 用于存储可能发生的错误信息
  const [error, setError] = useState(null);

  useEffect(() => {
    setLoading(true);

    // 获取题库详情
    getQuestionBankVoByIdUsingGet({
      id: questionBankId,
      needQueryQuestionList: true,
      pageSize: 200
    })
        .then((res: any) => {
          setBank(res.data);
        })
        .catch((e) => {
          console.error("获取题库列表失败，" + e.message);
          setError("获取题库详情失败，请稍后重试。");
        })
        .finally(() => {
          // 获取题目详情
          getQuestionVoByIdUsingGet({
            id: questionId
          })
              .then((res: any) => {
                setQuestion(res.data);
              })
              .catch((e) => {
                console.error("获取题目详情失败，" + e.message);
                setError("获取题目详情失败，请稍后重试。");
              })
              .finally(() => {
                setLoading(false);
              });
        });
  }, [questionBankId, questionId]);

  if (loading) {
    return <div>加载中...</div>;
  }

  if (error) {
    return <div>{error}</div>;
  }

  if (!bank || !question) {
    return <div>获取数据失败，请刷新重试</div>;
  }

  const id = parseInt(question.id, 10);
  const records = bank.questionPage?.records;
  // 题目菜单列表
  const questionMenuItemList = (records || []).map((questionItem: any) => {
    return {
      label: (
          <Link
              href={`/bank/${questionBankId}/question/${questionItem.id}`}
              prefetch={false}
          >
            {questionItem.title}
          </Link>
      ),
      key: questionItem.id
    };
  });

  return (
    <div id="bankQuestionPage">
      <Flex gap={24}>
        <Sider width={240} theme="light" style={{ padding: "24px 0" }}>
          <Title level={4} style={{ padding: "0 20px" }}>
            {bank.title}
          </Title>
          <Menu items={questionMenuItemList} selectedKeys={[question.id]} />
        </Sider>
        <Content>
          <QuestionCard question={question} />
        </Content>
      </Flex>
    </div>
  );
}
