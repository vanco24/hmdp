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
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

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

    /**
     * 使用redis替代session实现登陆验证
     * @param phone
     * @param session
     * @return
     */

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    //发送验证码
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            //2.如果不符合
            return Result.fail("手机号格式错误!");
        }
        //3.如果符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //4.保存验证码到session
//        session.setAttribute("code", code);

        //4.保存验证码到redis，设置过期时间2分钟
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //5.发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);//打印日志模拟即可
        //6.返回ok状态码
        return Result.ok();
    }

    @Override
    //这是通过短信验证码登录
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误！");
        }

        //2.校验验证码
//        Object cacheCode = session.getAttribute("code");

        //从redis取出验证码进行校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        if (cacheCode==null || !cacheCode.equals(code)){
            //3.不一致，报错
            return Result.fail("验证码错误！");
        }

        //4.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();

        //5.判断用户是否存在
        if (user == null){
            //6.不存在,创建新用户并保存
            user = createUserWithPhone(phone);
        }

        //7.保存用户信息到session
//        session.setAttribute("user", user);
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));//缓解内存压力，并减少敏感信息

        //7.保存用户DTO对象(用户信息)到redis
        //7.1随机生成token，作为登陆令牌
        String token = UUID.randomUUID().toString(true);
        //7.2将User对象转为hash进行存储(之所以用hash存储是因为将来修改更灵活，并且存储空间更小)
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        //又因为stringRedisTemplate要求key和value都是String类型，而UserDTO里的id是long类型，所以需要利用工具类将其转为String类型
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create().
                setIgnoreNullValue(true).
                setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //7.3设置有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);

        //7.3返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_"+RandomUtil.randomString(10));
        //2.保存用户
        save(user);

        return user;
    }
}
