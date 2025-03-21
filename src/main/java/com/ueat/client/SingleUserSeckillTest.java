package com.ueat.client;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import redis.clients.jedis.Jedis;

import java.nio.charset.StandardCharsets;

public class SingleUserSeckillTest {

    private static final String SEND_CODE_URL = "http://localhost:8081/user/code";
    private static final String LOGIN_URL = "http://localhost:8081/user/login";
    private static final String ADD_SECKILL_VOUCHER_URL = "http://localhost:8081/voucher/seckill";
    private static final String PHONE = "13300000000"; // 固定测试手机号
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;

    public static void main(String[] args) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT);

        try {
            // 第一步：添加秒杀券
            Long voucherId = addSeckillVoucher(httpClient);
            if (voucherId == null) {
                System.out.println("添加秒杀券失败，程序退出");
                return;
            }
            String SECKILL_URL = "http://localhost:8081/voucher-order/seckill/" + voucherId;

            // 第二步：进行秒杀
            // 1. 发送验证码
            String code = sendCodeAndGetVerificationCode(httpClient, jedis);
            if (code == null) {
                System.out.println("发送验证码失败，程序退出");
                return;
            }

            // 2. 登录获取 token
            String token = loginAndGetToken(httpClient, code);
            if (token == null) {
                System.out.println("登录失败，程序退出");
                return;
            }

            // 3. 发送秒杀请求
            sendSeckillRequest(httpClient, token, SECKILL_URL);

        } finally {
            jedis.close();
            try {
                httpClient.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static Long addSeckillVoucher(CloseableHttpClient httpClient) {
        HttpPost post = new HttpPost(ADD_SECKILL_VOUCHER_URL);
        post.setHeader("Content-Type", "application/json;charset=UTF-8");

        // 构造秒杀券数据（与 AddSeckillVoucherTest 一致）
        JSONObject voucher = new JSONObject();
        voucher.put("shopId", 1L);
        voucher.put("title", "测试秒杀券");
        voucher.put("subTitle", "测试用");
        voucher.put("rules", "无限制");
        voucher.put("payValue", 1000L);
        voucher.put("actualValue", 2000L);
        voucher.put("type", 1);
        voucher.put("stock", 100);
        voucher.put("beginTime", "2025-03-08T00:00:00"); // 修改为 ISO 格式
        voucher.put("endTime", "2025-12-31T23:59:59");   // 修改为 ISO 格式

        try {
            StringEntity entity = new StringEntity(voucher.toJSONString(), StandardCharsets.UTF_8);
            entity.setContentEncoding("UTF-8");
            post.setEntity(entity);
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                System.out.println("添加秒杀券响应: " + responseBody + ", 状态码: " + statusCode);

                if (statusCode == 200) {
                    JSONObject json = JSONObject.parseObject(responseBody);
                    if (json.getBooleanValue("success")) {
                        Long voucherId = json.getLong("data");
                        System.out.println("添加秒杀券成功，优惠券 ID: " + voucherId);
                        return voucherId;
                    } else {
                        System.out.println("添加秒杀券失败，错误信息: " + json.getString("errorMsg"));
                        return null;
                    }
                } else {
                    System.out.println("添加秒杀券请求失败，状态码: " + statusCode + ", 响应: " + responseBody);
                    return null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String sendCodeAndGetVerificationCode(CloseableHttpClient httpClient, Jedis jedis) {
        HttpPost sendCodePost = new HttpPost(SEND_CODE_URL + "?phone=" + PHONE);
        try {
            try (CloseableHttpResponse response = httpClient.execute(sendCodePost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("发送验证码响应: " + responseBody + ", 状态码: " + statusCode);
                if (statusCode == 200) {
                    JSONObject json = JSONObject.parseObject(responseBody);
                    if (json.getBoolean("success")) {
                        String redisKey = "login:code:" + PHONE;
                        String code = jedis.get(redisKey);
                        if (code != null) {
                            System.out.println("从 Redis 获取验证码: " + code);
                            return code;
                        } else {
                            System.out.println("从 Redis 获取验证码失败，phone: " + PHONE);
                            return null;
                        }
                    } else {
                        System.out.println("发送验证码失败，响应: " + responseBody);
                        return null;
                    }
                } else {
                    System.out.println("发送验证码失败，状态码: " + statusCode + ", 响应: " + responseBody);
                    return null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String loginAndGetToken(CloseableHttpClient httpClient, String code) {
        HttpPost loginPost = new HttpPost(LOGIN_URL);
        loginPost.setHeader("Content-Type", "application/json");
        String jsonBody = "{\"phone\":\"" + PHONE + "\",\"code\":\"" + code + "\"}";
        try {
            loginPost.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
            try (CloseableHttpResponse response = httpClient.execute(loginPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("登录响应: " + responseBody + ", 状态码: " + statusCode);
                if (statusCode == 200) {
                    JSONObject json = JSONObject.parseObject(responseBody);
                    String token = json.getString("data");
                    if (token != null) {
                        System.out.println("获取 token: " + token);
                        return token;
                    } else {
                        System.out.println("登录响应中无 data 字段");
                        return null;
                    }
                } else {
                    System.out.println("登录失败，状态码: " + statusCode + ", 响应: " + responseBody);
                    return null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void sendSeckillRequest(CloseableHttpClient httpClient, String token, String seckillUrl) {
        HttpPost seckillPost = new HttpPost(seckillUrl);
        seckillPost.setHeader("authorization", "Bearer " + token);
        try {
            try (CloseableHttpResponse response = httpClient.execute(seckillPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("秒杀响应: " + responseBody + ", 状态码: " + statusCode);
                if (statusCode == 200) {
                    JSONObject json = JSONObject.parseObject(responseBody);
                    if (json.getBooleanValue("success")) {
                        Long orderId = json.getLong("data");
                        System.out.println("秒杀成功，订单 ID: " + orderId);
                    } else {
                        System.out.println("秒杀失败: " + json.getString("errorMsg"));
                    }
                } else {
                    System.out.println("秒杀请求失败，状态码: " + statusCode + ", 响应: " + responseBody);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}