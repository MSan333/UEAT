package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import javax.websocket.Session;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2. 不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        // 3. 符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 4. 保存验证码到session / redis
        // session.setAttribute("code", code);
        // set key value ex 120
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 5. 发送验证码
        log.debug("发信短信验证码成功，验证码:{}", code);
        // 6. 返回OK
        return Result.ok(code);
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式错误");
        }
        // 2. 校验验证码
        // 从redis中获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        // String cacheCode = (String)session.getAttribute("code");
        // 3. 不一致返回
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        // 4. 一致 根据手机号查询用户
        User user = query().eq("phone", loginForm.getPhone()).one();
        // 5. 判断用户是否存在
        if (user == null) {
            // 6. 不存在则创建用户
            user = createUserWithPhone(loginForm.getPhone());
        }
        // 7. 保存用户信息
        // session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        // 不需要返回参数信息 因为登录是基于Session的 而Session是基于Cookie的 每一个Session会有一个unique session id
        // 用户访问 tomcat 的时候就会自动写入cookie中
        // 7. 保存到redis
        // 7.1 随机生成token生成令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2 对象转为hashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 由于id为long，转为map存储到redis时会报错，因为用的stringRedis要求k-v都是string
        // 通过filedValueEditor将value类型都转换为string
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().
                        setIgnoreNullValue(true).
                        setFieldValueEditor((fieldName, fieldValue)-> fieldValue.toString()));
        // 7.3 存储用户
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        log.info("存储 token 到 Redis: {}, 用户信息: {}", tokenKey, userMap);
        // 7/4 设置有效期(每次访问都更新有效期)
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        // 8. 返回token
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        // userId + bitmap
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 获取日期
        LocalDateTime now = LocalDateTime.now();
        // 拼接key : sign:userId:yyyyMM
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;
        // 获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();
        // 写入redis setbit key offset(从第几位开始) 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 获取日期
        LocalDateTime now = LocalDateTime.now();
        // 拼接key : sign:userId:yyyyMM
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;
        // 获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();
        // 获取本月截至今天为止的所有签到记录，返回的是一个十进制数字 BITFIELD sign:1010:202411 get u18 0
        // bitfield可以同时做多个子命令，所以返回的是集合
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 循环遍历
        int count = 0;
        while (true) {
            // 让这个数字与1相与，得到最后记录最后一个bit位
            if ((num & 1) == 0) {
                // 判断该bit位是否为0，则结束
                break;
            } else {
                // 不为0，计数器加1，记录右移1位，抛弃最后一位
                count ++;
            }
            num >>>= 1; // num = num>>>1 无符号右移是三个>
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        // 1. 创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        // 2. 保存用户
        save(user);
        return user;
    }
}
