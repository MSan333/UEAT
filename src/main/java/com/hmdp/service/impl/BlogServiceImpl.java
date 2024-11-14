package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户及点赞信息
        records.forEach(blog ->{
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        // records.forEach(this::queryBlogUser);
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(String id) {
        // 查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        // 查询blog有关的用户
        queryBlogUser(blog);
        // 查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        // 1.获取当前用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return; // 用户未登录，无需查询点赞信息
        }
        Long userId = UserHolder.getUser().getId();
        // 2.判断当前用户是否点赞
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2.判断当前用户是否点赞
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        // 根据zset中的score返回值来判断key是否存在，不存在的key的score为null
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        // 3.未点赞
        if (score == null) {
            // 3.1 数据库点赞+1
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            if (success) {
                // 3.2 保存用户到Redis的set中 zadd key value score
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4.已点赞
            // 4.1 数据库点赞-1
            boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2 把用户从redis的set中删除
            if (success) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(String id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        // 查询top5点赞用户 zrange key 0 4 前五个
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            // 避免空指针
            return Result.ok(Collections.emptyList());
        }
        // 解析出用户id : where id in (5,1) 用in查询时返回结果并不按照给定顺序 会先5再1
        // 因此需要添加一个条件 order by field(id,5,1)
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        // 根据用户id查询
        String idStr = StrUtil.join(",", ids);
        // 使用自定义sql语句（query），最后一条语句不支持语法，直接用last()手动拼接上order by field 的sql语句，idstr拼接好id
        List<Object> userDTOs = userService.query().
                in("id",ids).last("order by field(id,"+idStr+")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 返回
        return Result.ok(userDTOs);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
