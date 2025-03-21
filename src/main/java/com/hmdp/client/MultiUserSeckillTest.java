package com.hmdp.client;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MultiUserSeckillTest {

    private static final String SEND_CODE_URL = "http://localhost:8081/user/code";
    private static final String LOGIN_URL = "http://localhost:8081/user/login";
    private static final String ADD_SECKILL_VOUCHER_URL = "http://localhost:8081/voucher/seckill";
    private static final int USER_COUNT = 500; // 用户数
    private static final long PHONE_START = 13300000000L; // 手机号起始
    private static final Set<String> SUCCESSFUL_USERS = Collections.synchronizedSet(new HashSet<>()); // 成功用户
    private static final Set<String> FAILED_USERS = Collections.synchronizedSet(new HashSet<>()); // 失败用户

    public static void main(String[] args) throws InterruptedException {
        CloseableHttpClient httpClient = HttpClients.createDefault();

        try {
            // 第一步：添加秒杀券，库存为用户数一半
            Long voucherId = addSeckillVoucher(httpClient, USER_COUNT / 2 );
            if (voucherId == null) {
                System.out.println("添加秒杀券失败，程序退出");
                return;
            }
            String SECKILL_URL = "http://localhost:8081/voucher-order/seckill/" + voucherId;

            // 第二步：启动多线程模拟高并发秒杀
            ExecutorService executorService = new ThreadPoolExecutor(
                    USER_COUNT, USER_COUNT, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(), new ThreadPoolExecutor.AbortPolicy());

            CountDownLatch latch = new CountDownLatch(USER_COUNT);

            for (int i = 0; i < USER_COUNT; i++) {
                String phone = String.valueOf(PHONE_START + i);
                executorService.submit(() -> {
                    try (CloseableHttpClient client = HttpClients.createDefault()) {
                        // 1. 发送验证码请求并获取验证码
                        String code = sendCodeAndGetVerificationCode(client, phone);
                        if (code == null) {
                            System.out.println("用户 " + phone + " 发送验证码失败");
                            FAILED_USERS.add(phone);
                            return;
                        }

                        // 2. 使用验证码登录获取 token
                        String token = loginAndGetToken(client, code, phone);
                        if (token == null) {
                            System.out.println("用户 " + phone + " 登录失败");
                            FAILED_USERS.add(phone);
                            return;
                        }

                        // 3. 发送秒杀请求
                        sendSeckillRequest(client, token, SECKILL_URL, phone);
                    } catch (Exception e) {
                        e.printStackTrace();
                        FAILED_USERS.add(phone);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // 输出结果
            System.out.println("所有线程执行完毕");
            System.out.println("成功秒杀的用户 (" + SUCCESSFUL_USERS.size() + "): " + SUCCESSFUL_USERS);
            System.out.println("未成功秒杀的用户 (" + FAILED_USERS.size() + "): " + FAILED_USERS);

        } finally {
            try {
                httpClient.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static Long addSeckillVoucher(CloseableHttpClient httpClient, int stock) {
        HttpPost post = new HttpPost(ADD_SECKILL_VOUCHER_URL);
        post.setHeader("Content-Type", "application/json;charset=UTF-8");

        JSONObject voucher = new JSONObject();
        voucher.put("shopId", 1L);
        voucher.put("title", "测试秒杀券");
        voucher.put("subTitle", "测试用");
        voucher.put("rules", "无限制");
        voucher.put("payValue", 1000L);
        voucher.put("actualValue", 2000L);
        voucher.put("type", 1);
        voucher.put("stock", stock);
        voucher.put("beginTime", "2025-03-08T00:00:00");
        voucher.put("endTime", "2025-12-31T23:59:59");

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
                        System.out.println("添加秒杀券成功，优惠券 ID: " + voucherId + "，库存: " + stock);
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

    private static String sendCodeAndGetVerificationCode(CloseableHttpClient httpClient, String phone) {
        HttpPost sendCodePost = new HttpPost(SEND_CODE_URL + "?phone=" + phone);
        try {
            try (CloseableHttpResponse response = httpClient.execute(sendCodePost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("用户 " + phone + " 发送验证码响应: " + responseBody + ", 状态码: " + statusCode);
                if (statusCode == 200) {
                    JSONObject json = JSONObject.parseObject(responseBody);
                    if (json.getBooleanValue("success")) {
                        String code = json.getString("data");
                        if (code != null) {
                            System.out.println("用户 " + phone + " 获取验证码: " + code);
                            return code;
                        } else {
                            System.out.println("用户 " + phone + " 验证码为空");
                            return null;
                        }
                    } else {
                        System.out.println("用户 " + phone + " 发送验证码失败，错误信息: " + json.getString("errorMsg"));
                        return null;
                    }
                } else {
                    System.out.println("用户 " + phone + " 发送验证码失败，状态码: " + statusCode + ", 响应: " + responseBody);
                    return null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String loginAndGetToken(CloseableHttpClient httpClient, String code, String phone) {
        HttpPost loginPost = new HttpPost(LOGIN_URL);
        loginPost.setHeader("Content-Type", "application/json");
        String jsonBody = "{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}";
        try {
            loginPost.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
            try (CloseableHttpResponse response = httpClient.execute(loginPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("用户 " + phone + " 登录响应: " + responseBody + ", 状态码: " + statusCode);
                if (statusCode == 200) {
                    JSONObject json = JSONObject.parseObject(responseBody);
                    if (json.getBooleanValue("success")) {
                        String token = json.getString("data");
                        if (token != null) {
                            System.out.println("用户 " + phone + " 获取 token: " + token);
                            return token;
                        } else {
                            System.out.println("用户 " + phone + " 登录响应中无 token");
                            return null;
                        }
                    } else {
                        System.out.println("用户 " + phone + " 登录失败，错误信息: " + json.getString("errorMsg"));
                        return null;
                    }
                } else {
                    System.out.println("用户 " + phone + " 登录失败，状态码: " + statusCode + ", 响应: " + responseBody);
                    return null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void sendSeckillRequest(CloseableHttpClient httpClient, String token, String seckillUrl, String phone) {
        HttpPost seckillPost = new HttpPost(seckillUrl);
        seckillPost.setHeader("authorization", "Bearer " + token);
        try {
            try (CloseableHttpResponse response = httpClient.execute(seckillPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("用户 " + phone + " 秒杀响应: " + responseBody + ", 状态码: " + statusCode);
                if (statusCode == 200) {
                    JSONObject json = JSONObject.parseObject(responseBody);
                    if (json.getBooleanValue("success")) {
                        Long orderId = json.getLong("data");
                        SUCCESSFUL_USERS.add(phone);
                        System.out.println("用户 " + phone + " 秒杀成功，订单 ID: " + orderId);
                    } else {
                        FAILED_USERS.add(phone);
                        System.out.println("用户 " + phone + " 秒杀失败: " + json.getString("errorMsg"));
                    }
                } else {
                    FAILED_USERS.add(phone);
                    System.out.println("用户 " + phone + " 秒杀请求失败，状态码: " + statusCode + ", 响应: " + responseBody);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            FAILED_USERS.add(phone);
        }
    }
}