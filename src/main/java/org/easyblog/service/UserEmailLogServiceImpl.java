package org.easyblog.service;

import org.easyblog.bean.UserMailLog;
import org.easyblog.mapper.UserMailLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserEmailLogServiceImpl implements IUserEmailLogService {

    @Autowired
    private UserMailLogMapper userMailLogMapper;

    @Override
    public void saveSendCaptchaCode2User(String email, String content) {
        userMailLogMapper.save(new UserMailLog(email,content));
    }


}
