package com.hmdp.client;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;

public class AddSeckillVoucherTest {

    private static final String ADD_SECKILL_VOUCHER_URL = "http://localhost:8081/voucher/seckill";

    public static void main(String[] args) {
        CloseableHttpClient httpClient = HttpClients.createDefault();

        try {
            // 添加秒杀券
            Long voucherId = addSeckillVoucher(httpClient);
            if (voucherId != null) {
                System.out.println("添加秒杀券成功，优惠券 ID: " + voucherId);
            } else {
                System.out.println("添加秒杀券失败");
            }
        } finally {
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
                        return json.getLong("data");
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
}