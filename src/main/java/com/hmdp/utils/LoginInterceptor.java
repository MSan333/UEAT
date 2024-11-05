package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 两个拦截器 一个负责刷新token 一个负责拦截
 */
public class LoginInterceptor implements HandlerInterceptor {

//    private StringRedisTemplate stringRedisTemplate;
//
//    // LoginInterceptor类的对象不是spring创建的，因此不归spring管，需要用构造对象来创建redis对象
//    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }



    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 1. 获取 session
//        // HttpSession session = request.getSession();
//        // 获取请求头中的token
//        String token = request.getHeader("authorization");
//        // 2. 获取 session 中的用户
//
//        // UserDTO user = (UserDTO)session.getAttribute("user");
//        // 基于token获取redis中的用户
//        if (StrUtil.isBlank(token)) {
//            response.setStatus(401);
//            return false;
//        }
//        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
//        // 3. 判断用户是否存在
//        if (userMap.isEmpty()) {
//            // 4. 不存在拦截 401:未授权
//            response.setStatus(401);
//            return false;
//        }
//        // 5. 将查询到的hash数据转换为userDTO对象
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//        // 5. 存在 保存在ThreadLocal 隔离用户信息
//        UserHolder.saveUser(userDTO);
//        // 刷新token有效期
//        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
//        // 6. 放行


        // 判断是否需要做拦截（threadlocal有无用户）
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }
        // 有用户 则放行
        return true;
    }

//    @Override
////    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
////        // 移除用户
////        // 线程复用，不删除可能会调用到其他user的信息
////        // 不清除 可能会导致用户对象在内存中持续存在，尤其是在高并发的情况下，这可能会导致内存占用不断增加
////        // 即内存泄漏：jvm内存被threadLocal占用却没有被释放（使用），导致jvm内存不断上升
////        UserHolder.removeUser();
////    }
}
