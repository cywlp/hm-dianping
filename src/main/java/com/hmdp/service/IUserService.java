package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 珠代
 * @since 2022-6-20
 */
public interface IUserService extends IService<User> {

    Result sentCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);
}
