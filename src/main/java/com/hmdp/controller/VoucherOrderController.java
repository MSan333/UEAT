package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.impl.VoucherServiceImpl;
import jodd.util.ArraysUtil;
import jodd.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.util.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }




//        @PostMapping("/{voucherId}")
//        public Mono<ResponseEntity<Result>> seckillVoucher(
//                @PathVariable("voucherId") Long voucherId,
//                @RequestHeader("X-Request-Id") String requestId,
//                @RequestHeader("Authorization") String token) {
//            return Mono.fromCallable(() -> authenticateUser(token))
//                    .flatMap(userId -> {
//                        if (userId == null) {
//                            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//                                    .body(Result.fail("未登录")));
//                        }
//                        UserHolder.setUser(new User(userId));
//                        return Mono.fromCallable(() -> voucherOrderService.seckillVoucher(voucherId, requestId))
//                                .map(ResponseEntity::ok)
//                                .doFinally(signal -> UserHolder.remove()); // 清理 ThreadLocal
//                    })
//                    .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                            .body(Result.fail(e.getMessage()))));
//        }

}
