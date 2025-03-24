package com.tudou.tudoumianshi.aop;

import com.jd.platform.hotkey.client.callback.JdHotKeyStore;
import com.tudou.tudoumianshi.common.ErrorCode;
import com.tudou.tudoumianshi.common.ResultUtils;
import com.tudou.tudoumianshi.exception.ThrowUtils;
import com.tudou.tudoumianshi.model.dto.questionBank.QuestionBankQueryRequest;
import com.tudou.tudoumianshi.model.vo.QuestionBankVO;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Pointcut;

import javax.servlet.http.HttpServletRequest;

;

//@Aspect
//@Component
public class HotKeyAspect {


    @Pointcut("@annotation(com.tudou.tudoumianshi.annotation.HotKeyValid)")
    public void controllerMethods() {
    }

    @Around("controllerMethods() && args(questionBankQueryRequest,request)")
    public Object beforeMethod(ProceedingJoinPoint joinPoint,QuestionBankQueryRequest questionBankQueryRequest, HttpServletRequest request) throws Throwable {
        ThrowUtils.throwIf(questionBankQueryRequest==null, ErrorCode.PARAMS_ERROR);
        Long id = questionBankQueryRequest.getId();
        ThrowUtils.throwIf(id==null||id<=0, ErrorCode.PARAMS_ERROR);
        //TODO
        String key="bank_detail_" + id;
        QuestionBankVO result=(QuestionBankVO) JdHotKeyStore.get(key);
        if (result!= null) {
            return ResultUtils.success(result);
        }
        return joinPoint.proceed();
    }
}

