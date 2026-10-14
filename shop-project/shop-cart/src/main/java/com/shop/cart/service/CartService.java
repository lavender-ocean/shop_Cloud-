

package com.shop.cart.service;

import com.shop.api.dto.SkuInfoDTO;
import com.shop.api.feign.ProductClient;
import com.shop.cart.dto.AddCartDTO;
import com.shop.cart.dto.CartItem;
import com.shop.cart.dto.UpdateQuantityDTO;
import com.shop.cart.dto.UpdateSelectedDTO;
import com.shop.cart.vo.CartItemVO;
import com.shop.cart.vo.CartVO;
import constant.RedisKeyConstants;
import context.UserContext;
import exception.BizAssert;
import exception.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import result.Result;
import result.ResultCode;
import util.JsonUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购物车：Redis Hash
 *   key   cart:{userId}
 *   field skuId
 *   value CartItem —— 只存 skuId / quantity / selected / addTime
 *
 * 设计：**购物车不冗余商品数据**。名称/图片/规格/价格/上下架在 list 时
 * 一次性批量回源 shop-product 实时拼装，因此天然与 DB 一致，无需任何同步链路。
 */
@Service
public class CartService {

    private static final int MAX_QUANTITY = 99;
    private static final String INVALID_REASON = "商品已下架或删除";
    private static final String PLACEHOLDER_NAME = "(已失效商品)";

    private final StringRedisTemplate redisTemplate;
    private final ProductClient productClient;

    public CartService(StringRedisTemplate redisTemplate, ProductClient productClient) {
        this.redisTemplate = redisTemplate;
        this.productClient = productClient;
    }

    /** 加购：只落"引用 + 用户意图"，商品数据一概不落 */
    public void add(AddCartDTO dto) {
        String key = cartKey();
        String field = String.valueOf(dto.getSkuId());

        Object exist = redisTemplate.opsForHash().get(key, field);
        if (exist != null) {
            CartItem item = JsonUtils.parse(exist.toString(), CartItem.class);
            item.setQuantity(Math.min(item.getQuantity() + dto.getQuantity(), MAX_QUANTITY));
            redisTemplate.opsForHash().put(key, field, JsonUtils.toJson(item));
            return;
        }

        // 加购校验（存在 + 在架），校验完即丢，不回写快照
        SkuInfoDTO sku = requireOnSaleSku(dto.getSkuId());

        CartItem item = new CartItem();
        item.setSkuId(sku.getId());
        item.setQuantity(Math.min(dto.getQuantity(), MAX_QUANTITY));
        item.setSelected(true);
        item.setAddTime(System.currentTimeMillis());
        redisTemplate.opsForHash().put(key, field, JsonUtils.toJson(item));
    }

    /** 购物车列表：Redis 用户意图 + 商品服务实时数据拼装 */
    public CartVO list() {
        List<CartItem> stored = redisTemplate.opsForHash().entries(cartKey()).values().stream()
                .map(v -> JsonUtils.parse(v.toString(), CartItem.class))
                .sorted(Comparator.comparing(CartItem::getAddTime))
                .toList();

        CartVO vo = new CartVO();
        if (stored.isEmpty()) {
            vo.setItems(List.of());
            return vo;
        }

        Map<Long, SkuInfoDTO> skuMap = batchLoadSku(stored.stream().map(CartItem::getSkuId).toList());

        int totalQuantity = 0;
        int selectedQuantity = 0;
        long selectedAmount = 0L;
        List<CartItemVO> items = new ArrayList<>(stored.size());

        for (CartItem c : stored) {
            CartItemVO item = new CartItemVO();
            item.setSkuId(c.getSkuId());
            item.setQuantity(c.getQuantity());
            item.setSelected(c.getSelected());
            item.setAddTime(c.getAddTime());

            SkuInfoDTO sku = skuMap.get(c.getSkuId());
            boolean onSale = sku != null && sku.getPrice() != null
                    && sku.getStatus() != null && sku.getStatus() == 1;

            if (onSale) {
                item.setSpuId(sku.getSpuId());
                item.setSpuName(sku.getSpuName());
                item.setSkuName(sku.getName());
                item.setImage(sku.getImage());
                item.setSpecJson(sku.getSpecJson());
                item.setPrice(sku.getPrice());
                item.setValid(true);
            } else {
                item.setSkuName(PLACEHOLDER_NAME);
                item.setPrice(0L);
                item.setValid(false);
                item.setInvalidReason(INVALID_REASON);
            }

            items.add(item);

            // 失效商品不计入数量与金额，避免"失效商品也在凑合计"的误导
            if (!onSale) {
                continue;
            }
            totalQuantity += item.getQuantity();
            if (Boolean.TRUE.equals(item.getSelected())) {
                selectedQuantity += item.getQuantity();
                selectedAmount += item.getPrice() * item.getQuantity();
            }
        }

        vo.setItems(items);
        vo.setTotalQuantity(totalQuantity);
        vo.setSelectedQuantity(selectedQuantity);
        vo.setSelectedAmount(selectedAmount);
        return vo;
    }

    public void updateQuantity(UpdateQuantityDTO dto) {
        CartItem item = requireItem(dto.getSkuId());
        item.setQuantity(Math.min(dto.getQuantity(), MAX_QUANTITY));
        redisTemplate.opsForHash().put(cartKey()
                , String.valueOf(dto.getSkuId()), JsonUtils.toJson(item));
    }

    public void updateSelected(UpdateSelectedDTO dto) {
        CartItem item = requireItem(dto.getSkuId());
        item.setSelected(dto.getSelected());
        redisTemplate.opsForHash().put(cartKey()
                , String.valueOf(dto.getSkuId()), JsonUtils.toJson(item));
    }

    public void remove(Long skuId) {
        redisTemplate.opsForHash().delete(cartKey(), String.valueOf(skuId));
    }

    public void clear() {
        redisTemplate.delete(cartKey());
    }

    /* ==================== 内部方法 ==================== */

    /** 购物车列表强依赖商品数据，故降级时直接报错，而非返回一份无意义的列表 */
    private Map<Long, SkuInfoDTO> batchLoadSku(List<Long> skuIds) {
        Result<List<SkuInfoDTO>> result = productClient.batchGetSku(skuIds);
        if (result.getCode() != 0 || result.getData() == null) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "商品服务暂不可用，请稍后重试");
        }
        return result.getData().stream()
                .collect(Collectors.toMap(SkuInfoDTO::getId, Function.identity(), (a, b) -> a));
    }

    private SkuInfoDTO requireOnSaleSku(Long skuId) {
        Result<SkuInfoDTO> result = productClient.getSku(skuId);
        BizAssert.isTrue(result.getCode() == 0 && result.getData() != null
                , ResultCode.SKU_NOT_FOUND);
        SkuInfoDTO sku = result.getData();
        BizAssert.isTrue(sku.getStatus() != null && sku.getStatus() == 1
                , ResultCode.SKU_OFF_SHELF);
        return sku;
    }

    private CartItem requireItem(Long skuId) {
        Object exist = redisTemplate.opsForHash().get(cartKey(), String.valueOf(skuId));
        BizAssert.isTrue(exist != null, ResultCode.NOT_FOUND, "购物车中无此商品");
        return JsonUtils.parse(exist.toString(), CartItem.class);
    }

    private String cartKey() {
        Long userId = UserContext.getUserId();
        BizAssert.notNull(userId, ResultCode.UNAUTHORIZED);
        return RedisKeyConstants.CART + userId;
    }
}
































































//package com.shop.cart.service;
//
//import com.shop.api.dto.SkuInfoDTO;
//import com.shop.api.feign.ProductClient;
//import com.shop.cart.dto.AddCartDTO;
//import com.shop.cart.dto.CartItem;
//import com.shop.cart.dto.UpdateQuantityDTO;
//import com.shop.cart.dto.UpdateSelectedDTO;
//import com.shop.cart.vo.CartVO;
//import constant.RedisKeyConstants;
//import context.UserContext;
//import exception.BizAssert;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.stereotype.Service;
//import result.Result;
//import result.ResultCode;
//import util.JsonUtils;
//
//import java.util.Comparator;
//import java.util.List;
//import java.util.Map;
//
//
////这次购物车直接使用redis---购物车属于会话型数据，允许丢失，丢失可以重新添加；订单才落库
//
////Redis Hash
////- key：cart:{userId} 例：cart:10086
////- hash‑field：skuId
////- hash‑value：CartItem 对象 JSON 序列化字符串
////CartItem 存：skuId、spuId、商品名称、图片、规格、价格、数量、是否选中、加入时间。
////> 注意：**价格存的是当时加入购物车的快照，不是实时查商品**。后续商品改价，购物车不会自动更新。
//
//@Service
//public class CartService {
//
//    //设定每人购物车中的每个商品固定99个
//    private static final int MAX_QUANTITY = 99;
//
//    private final StringRedisTemplate redisTemplate;
//    private final ProductClient productClient;
//
//    public CartService(StringRedisTemplate redisTemplate, ProductClient productClient) {
//        this.redisTemplate = redisTemplate;
//        this.productClient = productClient;
//    }
//
//    //新增商品进入购物车
//    public void add(AddCartDTO dto) {
//        //先拿到这个用户的购物车的key
//        String key = cartKey();
//        //判断当前的购物车中是否有这个商品
//        String field = String.valueOf(dto.getSkuId());
//        Object exist = redisTemplate.opsForHash().get(key, field);
//
//        CartItem item;
//        if (exist != null) {
//            //如果当前的购物车中已经有了这个商品，那就只增加数量
//            item = JsonUtils.parse(exist.toString(), CartItem.class);
//            //同样的最大数量99件
//            item.setQuantity(Math.min(item.getQuantity() + dto.getQuantity(), MAX_QUANTITY));
//        } else {
//            //如果当前购物车中没有这个商品
//            //1.从db中取出来
//            Result<SkuInfoDTO> result = productClient.getSku(dto.getSkuId());
//            //判断是否成功--db是否能拿到这个商品
//            BizAssert.isTrue(result.getCode() == 0 && result.getData() != null
//                    , ResultCode.SKU_NOT_FOUND);
//            SkuInfoDTO sku = result.getData();
//            //校验这个商品还有没有了 卖完了？！
//            BizAssert.isTrue(sku.getStatus() != null && sku.getStatus() == 1
//                    , ResultCode.SKU_OFF_SHELF);
//
//            item = new CartItem();
//            item.setSkuId(sku.getId());
//            item.setSpuId(sku.getSpuId());
//            item.setSpuName(sku.getSpuName());
//            item.setSkuName(sku.getName());
//            item.setImage(sku.getImage());
//            item.setSpecJson(sku.getSpecJson());
//            item.setPrice(sku.getPrice());
//            item.setQuantity(dto.getQuantity());
//            item.setSelected(true);
//            item.setAddTime(System.currentTimeMillis());
//        }
//        //写回redis中
//        redisTemplate.opsForHash().put(key, field, JsonUtils.toJson(item));
//    }
//
//
//    //查询我的购物车列表
//    public CartVO list() {
//        //先拿到当前用户对应的购物车key
//        String key = cartKey();
//        //去取
//        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
//        List<CartItem> items = entries.values().stream()
//                .map(v -> JsonUtils.parse(v.toString(), CartItem.class))
//                //按加入时间升序，最早加入排在前面
//                .sorted(Comparator.comparing(CartItem::getAddTime))
//                .toList();
//
//        //计算总商品数量
//        int totalQuantity = 0;
//        //计算选中商品数量
//        int selectedQuantity = 0;
//        //计算选中商品的金额
//        long selectedAmount = 0L;
//        for (CartItem item : items) {
//            totalQuantity += item.getQuantity();
//            if (Boolean.TRUE.equals(item.getSelected())) {
//                selectedQuantity += item.getQuantity();
//                selectedAmount += item.getPrice() * item.getQuantity();
//            }
//        }
//
//        CartVO vo = new CartVO();
//        vo.setItems(items);
//        vo.setTotalQuantity(totalQuantity);
//        vo.setSelectedQuantity(selectedQuantity);
//        vo.setSelectedAmount(selectedAmount);
//        return vo;
//    }
//
//    //修改购物车的商品数量
//    public void updateQuantity(UpdateQuantityDTO dto) {
//        CartItem item = requireItem(dto.getSkuId());
//        //上限99
//        item.setQuantity(Math.min(dto.getQuantity(), MAX_QUANTITY));
//        redisTemplate.opsForHash().put(cartKey()
//                , String.valueOf(dto.getSkuId()), JsonUtils.toJson(item));
//    }
//
//    //修改选中的状态
//    public void updateSelected(UpdateSelectedDTO dto) {
//        CartItem item = requireItem(dto.getSkuId());
//        item.setSelected(dto.getSelected());
//        redisTemplate.opsForHash().put(cartKey()
//                , String.valueOf(dto.getSkuId()), JsonUtils.toJson(item));
//    }
//
//    //删去购物车中的某一个商品
//    public void remove(Long skuId) {
//        redisTemplate.opsForHash().delete(cartKey(), String.valueOf(skuId));
//    }
//
//    //清除购物车 ---- 直接删除redis中的东西
//    public void clear() {
//        redisTemplate.delete(cartKey());
//    }
//
//    //查询购物车中的商品
//    private CartItem requireItem(Long skuId) {
//
//        Object exist = redisTemplate.opsForHash().get(cartKey(), String.valueOf(skuId));
//        BizAssert.isTrue(exist != null, ResultCode.NOT_FOUND, "购物车中无此商品");
//        return JsonUtils.parse(exist.toString(), CartItem.class);
//    }
//
//    //
//    private String cartKey() {
//        //拿到这个人的userid 生成属于这个人的购物车
//        Long userId = UserContext.getUserId();
//        //判断是否登陆！！！！！！！！！！！！！
//        BizAssert.notNull(userId, ResultCode.UNAUTHORIZED);
//        return RedisKeyConstants.CART + userId;
//    }
//}