package com.shop.order.service;


import com.shop.api.dto.InventoryDTO;
import com.shop.api.dto.SkuInfoDTO;
import com.shop.api.dto.UserProfileDTO;
import com.shop.api.feign.InventoryClient;
import com.shop.api.feign.ProductClient;
import com.shop.api.feign.UserClient;
import com.shop.order.entity.OrderInfo;
import com.shop.order.entity.OrderItem;
import com.shop.order.vo.OrderAggregateVO;
import com.shop.order.vo.OrderDetailVO;
import com.shop.order.vo.OrderItemVO;
import context.UserContext;
import exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import result.Result;
import result.ResultCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.springframework.web.servlet.function.ServerResponse.async;

//订单聚合详情：并行拉取 用户资料 + 每个 SKU 的最新信息与库存
//要点：线程池隔离 + 超时控制 + 单点降级 + TTL/MDC 透传
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderAggregateService {

    // 单个远程调用的超时（毫秒），超时即降级，不拖垮整体
    private static final long REMOTE_TIMEOUT_MS = 800;

    //继续构造注入
    private final OrderService orderService;
    private final UserClient userClient;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;


    private final ExecutorService executor;

    public OrderAggregateVO aggregate(String orderNo, String mode){
        long start = System.currentTimeMillis();

        //1.本地数据 + 越权校验
        OrderDetailVO detailVO = orderService.detail(orderNo);

        //订单主表
        OrderInfo order = detailVO.getOrder();
        //订单详细
        List<OrderItem> items = detailVO.getItems();

        boolean serial = "serial".equalsIgnoreCase(mode);

        UserProfileDTO user = new UserProfileDTO();
        //LinkedHashMap 继承 HashMap，底层 = HashMap 哈希表 + 双向链表
        //HashMap 是无序；LinkedHashMap 可以保持插入顺序，也可以开启访问顺序（LRU 淘汰）

        //接口返回需要 固定顺序的 key‑value，HashMap 返回乱序，可以用 LinkedHashMap 组装

//        这里用 `LinkedHashMap`：保证存入顺序和订单 items 顺序一模一样，后续遍历不会乱序。
        Map<Long, SkuInfoDTO> skuMap = new LinkedHashMap<>();
        Map<Long, InventoryDTO> stockMap = new LinkedHashMap<>();


        //serial ----- 串行
        if (serial) {
            // ---- 串行：总耗时 是 各次调用之和（对照组）----

            //所有 Feign 调用排队执行

/// /////////////////////////////////////////////////////////////////
//          串行没有降级逻辑！失败直接抛出异常！！！！！！！！！！！！！！！！！！！！！！！

            //xxxClient.xxx() 就是去远程调用对应的业务逻辑
            //先去调用这个用户的信息
            user = callAndUnwrap(() -> userClient.getProfile(order.getUserId()), null);
            for (OrderItem item : items) {
                //再去获取商品的信息
                skuMap.put(item.getSkuId(),
                        callAndUnwrap(() -> productClient.getSku(item.getSkuId()), null));
                //再去获取库存的信息
                stockMap.put(item.getSkuId(),
                        callAndUnwrap(() -> inventoryClient.getStock(item.getSkuId()), null));
            }
        } else {
            // ---- 并行：总耗时 是 最慢的那次 ----

            //并行模式：把 获取用户、批量 SKU、批量库存 交给自定义线程池并发执行
            // 1. 捕获 MDC（TTL 只管 UserContext，MDC 需手动带）

//          TTL 只处理 TransmittableThreadLocal (UserContext 用户 ID)
//          MDC（traceId 链路追踪）底层是普通 ThreadLocal，TTL 不会自动拷贝！必须手动快照 + 回放

            //手动带上MDC 做追踪

            // 捕获主线程MDC快照！ ////////////////////////////////////////////////////
            Map<String, String> mdc = MDC.getCopyOfContextMap();

            //CompletableFuture异步任务
            CompletableFuture<UserProfileDTO> userFuture =
                    submitFuture(
                            () -> userClient.getProfile(order.getUserId())
                            , mdc);






            //这个神了， 直接放在Map里面去

            //skuFutures这个 Map 存的是什么？
            //是Map<skuId, CompletableFuture<返回的DTO>>

            //Map 的 value 不是最终数据，是「未来结果的凭证对象 CompletableFuture」
            //
            //循环每一个订单商品 `OrderItem
            //每循环一次，调用 submitFuture(...)
            //调用 submitFuture 的一瞬间，任务就已经提交到线程池开始跑了，不是等到 join 才跑！
            //submitFuture 返回一个 CompletableFuture 凭证，put 进 Map，key 是 skuId，做绑定。

            // 通俗理解：
            // submitFuture → 给工地递交工单，工人已经开始干活；返回一张取件小票（Future）
            // Map 里面存放一堆「小票」，key 标记这个小票对应哪个 sku
            //
            //这里用 LinkedHashMap：保证存入顺序和订单 items 顺序一模一样，后续遍历不会乱序
            Map<Long, CompletableFuture<SkuInfoDTO>> skuFutures = new LinkedHashMap<>();
            Map<Long, CompletableFuture<InventoryDTO>> stockFutures = new LinkedHashMap<>();



            // 循环阶段：全部任务一次性批量提交给线程池，并发同时跑
            //就是这里的 submitFuture去调用线程池
            for (OrderItem item : items) {
                Long skuId = item.getSkuId();

                //神了， 直接在put里面去做
                skuFutures.put(skuId, submitFuture(
                        () -> productClient.getSku(skuId)
                        , mdc));


                //只要调用 `submitFuture`：

                //1. 任务立刻丢进我们 TTL 包装好的`executor`线程池执行
                //2. 返回`CompletableFuture`凭证
                //3. 内部已经做好：超时控制、异常降级，就算远程调用炸了，这个 Future 也不会抛异常，join 拿到的是`null`

                //所以后面 `f.join()` 不会抛异常！异常已经被`exceptionally`吞掉，降级返回 null
                stockFutures.put(skuId, submitFuture(
                        () -> inventoryClient.getStock(skuId)
                        , mdc));
            }


            //join () 等待全部任务执行完毕
            user = userFuture.join();                       // 已 exceptionally → 失败为 null

            // 遍历Future凭证Map，对每一个future执行join()阻塞等待，把真实结果放到 skuMap
            skuFutures.forEach(

                    (k, f) -> skuMap.put(k, f.join())
            );

            //- `f.join()`：**阻塞当前 Tomcat 主线程，等待这个异步任务完成，拿到返回值**。
            //- `skuFutures` 的 key 是 skuId，
            // `f.join()`得到 SkuInfoDTO；把 `skuId → DTO` 存入 skuMap（这个 Map 存真正业务数据）。
            //- skuMap、stockMap 也是`LinkedHashMap`，保持顺序和订单明细一致。
            //
            //
            //> 注意：forEach 循环 join，不是并行执行任务，
            // **任务早就在上面 for 循环已经全部启动了**；这里只是挨个等待拿结果。

            stockFutures.forEach(

                    (k, f) -> stockMap.put(k, f.join())
            );

        }

        // 组装
        List<OrderItemVO> itemVOs = new ArrayList<>();
        for (OrderItem item : items) {
            OrderItemVO vo = new OrderItemVO();
            vo.setSnapshot(item);
            vo.setLatestSku(skuMap.get(item.getSkuId()));
            InventoryDTO stock = stockMap.get(item.getSkuId());
            vo.setAvailableStock(stock == null ? null : stock.getAvailableStock());
            itemVOs.add(vo);
        }

        OrderAggregateVO result = new OrderAggregateVO();
        result.setOrder(order);
        result.setItems(itemVOs);
        result.setUser(user);
        result.setMode(serial ? "serial" : "parallel");
        result.setCostMs(System.currentTimeMillis() - start);
        return result;


    }


    /** 异步执行：TTL 透传 UserContext + 手动回放 MDC + 超时 + 降级为 null */
    private <T> CompletableFuture<T> submitFuture(Supplier<Result<T>> call, Map<String, String> mdc) {

        //选择supplyAsync ------ 有返回值的
        return CompletableFuture

                //必须传入我们自己的`executor`！
                // 如果不传，CompletableFuture 默认使用`ForkJoinPool.commonPool`，
                // **这个是 JVM 全局共享线程池，没有 TTL 包装
                // ，UserContext 会丢失！这是高频 bug 点**

                //这个源码还是挺好看的
                //    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier,
                //                                                       Executor executor) {


                .supplyAsync(() -> {
                    if (mdc != null) {

                        //手动放置MDC 因为TTL只处理我设置的 TransmittableThreadLocal (UserContext 用户 ID)
                        //但是MDC的底层是 ThreadLocal 不是 TransmittableThreadLocal TTL 不会处理MDC
                        //需要手动去快照 + 回放

                        MDC.setContextMap(mdc);
                    }
                    try {
                        // 验证 TTL：子线程里应能拿到 userId
                        log.info("[异步编排] 子线程 userId={}, traceId={}, thread={}",
                                UserContext.getUserId()
                                , MDC.get("traceId")
                                , Thread.currentThread().getName());

                        //在这里去做的是串行业务 --- 也就是这个单个任务的实质的 业务
                        //但是这个是并行的代码，所以这里只有一个call ！！！！！！！！！！！！！
                        //也就是单个 业务逻辑
                        //并行 ------ 多个单个并行 ！！！！！！！！！！！！！！！

                        return callAndUnwrap(call, null);
                    } finally {
                        // 清理MDC，防止线程池复用污染
                        MDC.clear();
                    }
                }, executor)

                //executor  ----  指定「把这个任务交给哪一个线程池去执行」

                //这里就是用到了 AsyncExecutorConfig 的 配置好的 线程池 的！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！

                //单个异步任务超过 800ms，直接抛出TimeoutException，进入 exceptionally 降级
                .orTimeout(REMOTE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                //不管是 Feign 调用报错、业务异常、超时异常，全部捕获，打印警告日志，返回 null 做降级
                .exceptionally(ex -> {
                    log.warn("[异步编排] 远程调用失败/超时，降级为 null: {}", ex.getMessage());
                    return null;
                });
    }

    /** 拆 Result：非 0 抛异常；tag 用于日志区分 */
    private <T> T callAndUnwrap(Supplier<Result<T>> call, String tag) {
        //去运行这个传过来的业务逻辑先
        Result<T> r = call.get();
        //处理异常
        if (r == null) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "远程无响应");
        }
        //规定Result的CODE 是 0 才是成功
        if (r.getCode() != 0) {
            throw new BizException(r.getCode(), r.getMessage());
        }
        //返回业务执行成功的数据
        return r.getData();
    }






}
