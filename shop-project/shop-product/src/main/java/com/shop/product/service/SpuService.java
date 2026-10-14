//package com.shop.product.service;
//
//import com.baomidou.mybatisplus.core.toolkit.IdWorker;
//import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.shop.product.dto.SkuDTO;
//import com.shop.product.dto.SpuDTO;
//import com.shop.product.dto.SpuQuery;
//import com.shop.product.entity.Sku;
//import com.shop.product.entity.Spu;
//import com.shop.product.mapper.SkuMapper;
//import com.shop.product.mapper.SpuMapper;
//import com.shop.product.vo.SpuDetailVO;
//import exception.BizAssert;
//import org.springframework.beans.BeanUtils;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.util.StringUtils;
//import result.PageResult;
//import result.ResultCode;
//import util.JsonUtils;
//
//import java.util.ArrayList;
//import java.util.List;
//
//@Service
//public class SpuService {
//
//    private final SpuMapper spuMapper;
//    private final SkuMapper skuMapper;
//
//    public SpuService(SpuMapper spuMapper, SkuMapper skuMapper) {
//        this.spuMapper = spuMapper;
//        this.skuMapper = skuMapper;
//    }
//
//    public PageResult<Spu> page(SpuQuery query) {
//        Page<Spu> page = new Page<>(query.getPageNum(), query.getPageSize());
//        List<Spu> records = spuMapper.selectPage(page, query);
//        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
//    }
//
//    public SpuDetailVO detail(Long id) {
//        Spu spu = spuMapper.selectById(id);
//        BizAssert.notNull(spu, ResultCode.SPU_NOT_FOUND);
//        SpuDetailVO vo = new SpuDetailVO();
//        BeanUtils.copyProperties(spu, vo);
//        vo.setImages(parseImages(spu.getImages()));
//        vo.setSkus(skuMapper.selectBySpuId(id));
//        return vo;
//    }
//
//    @Transactional(rollbackFor = Exception.class)
//    public Long add(SpuDTO dto) {
//        long spuId = IdWorker.getId();
//        List<Sku> skus = buildSkus(spuId, dto.getSkus());
//
//        Spu spu = new Spu();
//        spu.setId(spuId);
//        spu.setSpuCode("SPU" + spuId);
//        applyFields(spu, dto, skus);
//        spuMapper.insert(spu);
//        skuMapper.insertBatch(skus);
//        return spuId;
//    }
//
//    @Transactional(rollbackFor = Exception.class)
//    public void update(SpuDTO dto) {
//        BizAssert.notNull(dto.getId(), ResultCode.PARAM_ERROR);
//        BizAssert.notNull(spuMapper.selectById(dto.getId()), ResultCode.SPU_NOT_FOUND);
//
//        List<Sku> skus = buildSkus(dto.getId(), dto.getSkus());
//        Spu spu = new Spu();
//        spu.setId(dto.getId());
//        applyFields(spu, dto, skus);
//        spuMapper.update(spu);
//
//        // 简化策略：先逻辑删旧 SKU，再插入新的（历史订单有快照，不受影响）
//        skuMapper.deleteBySpuId(dto.getId());
//        skuMapper.insertBatch(skus);
//    }
//
//    @Transactional(rollbackFor = Exception.class)
//    public void updateStatus(Long id, Integer status) {
//        BizAssert.notNull(spuMapper.selectById(id), ResultCode.SPU_NOT_FOUND);
//        spuMapper.updateStatus(id, status);
//        skuMapper.updateStatusBySpuId(id, status);
//    }
//
//    @Transactional(rollbackFor = Exception.class)
//    public void delete(Long id) {
//        BizAssert.notNull(spuMapper.selectById(id), ResultCode.SPU_NOT_FOUND);
//        spuMapper.deleteById(id);
//        skuMapper.deleteBySpuId(id);
//    }
//
//    private void applyFields(Spu spu, SpuDTO dto, List<Sku> skus) {
//        spu.setName(dto.getName());
//        spu.setCategoryId(dto.getCategoryId());
//        spu.setBrandId(dto.getBrandId());
//        spu.setMainImage(dto.getMainImage());
//        spu.setImages(dto.getImages() == null ? "[]" : JsonUtils.toJson(dto.getImages()));
//        spu.setDetail(dto.getDetail());
//        spu.setUnit(dto.getUnit());
//        spu.setIsColdChain(dto.getIsColdChain() == null ? 0 : dto.getIsColdChain());
//        spu.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
//        spu.setPriceMin(skus.stream().mapToLong(Sku::getPrice).min().orElse(0L));
//        spu.setPriceMax(skus.stream().mapToLong(Sku::getPrice).max().orElse(0L));
//    }
//
//    private List<Sku> buildSkus(Long spuId, List<SkuDTO> skuDTOS) {
//        BizAssert.notEmpty(skuDTOS, ResultCode.PARAM_ERROR);
//        List<Sku> list = new ArrayList<>();
//        for (SkuDTO d : skuDTOS) {
//            long skuId = IdWorker.getId();
//            Sku sku = new Sku();
//            sku.setId(skuId);
//            sku.setSpuId(spuId);
//            sku.setSkuCode("SKU" + skuId);
//            sku.setName(d.getName());
//            sku.setSpecJson(d.getSpecJson());
//            sku.setImage(d.getImage());
//            sku.setPrice(d.getPrice());
//            sku.setStatus(d.getStatus() == null ? 1 : d.getStatus());
//            list.add(sku);
//        }
//        return list;
//    }
//
//    private List<String> parseImages(String images) {
//        if (!StringUtils.hasText(images)) {
//            return List.of();
//        }
//        return JsonUtils.parse(images, new TypeReference<List<String>>() {});
//    }
//}


package com.shop.product.service;

import cache.MultiLevelCache;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.shop.product.dto.SkuDTO;
import com.shop.product.dto.SpuDTO;
import com.shop.product.dto.SpuQuery;
import com.shop.product.entity.Sku;
import com.shop.product.entity.Spu;
import com.shop.product.mapper.SkuMapper;
import com.shop.product.mapper.SpuMapper;
import com.shop.product.vo.SpuDetailVO;
import exception.BizAssert;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import result.PageResult;
import result.ResultCode;
import util.JsonUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class SpuService {

    private static final String CACHE_KEY_DETAIL = "spu:detail:";
    private static final Duration REDIS_TTL = Duration.ofMinutes(10);

    private final SpuMapper spuMapper;
    private final SkuMapper skuMapper;

    //引入多级缓存
    private final MultiLevelCache cache;

    public SpuService(SpuMapper spuMapper, SkuMapper skuMapper, MultiLevelCache cache) {
        this.spuMapper = spuMapper;
        this.skuMapper = skuMapper;
        this.cache = cache;
    }

    public PageResult<Spu> page(SpuQuery query) {
        Page<Spu> page = new Page<>(query.getPageNum(), query.getPageSize());
        List<Spu> records = spuMapper.selectPage(page, query);
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    /** 详情：走多级缓存 */
    public SpuDetailVO detail(Long id) {
        return cache.get(CACHE_KEY_DETAIL + id, SpuDetailVO.class, REDIS_TTL, () -> loadDetail(id));
    }

    private SpuDetailVO loadDetail(Long id) {
        Spu spu = spuMapper.selectById(id);
        BizAssert.notNull(spu, ResultCode.SPU_NOT_FOUND);
        SpuDetailVO vo = new SpuDetailVO();
        BeanUtils.copyProperties(spu, vo);
        vo.setImages(parseImages(spu.getImages()));
        vo.setSkus(skuMapper.selectBySpuId(id));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(SpuDTO dto) {
        long spuId = IdWorker.getId();
        List<Sku> skus = buildSkus(spuId, dto.getSkus());

        Spu spu = new Spu();
        spu.setId(spuId);
        spu.setSpuCode("SPU" + spuId);
        applyFields(spu, dto, skus);
        spuMapper.insert(spu);
        skuMapper.insertBatch(skus);
        return spuId;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SpuDTO dto) {
        BizAssert.notNull(dto.getId(), ResultCode.PARAM_ERROR);
        BizAssert.notNull(spuMapper.selectById(dto.getId()), ResultCode.SPU_NOT_FOUND);

        List<Sku> skus = buildSkus(dto.getId(), dto.getSkus());
        Spu spu = new Spu();
        spu.setId(dto.getId());
        applyFields(spu, dto, skus);
        spuMapper.update(spu);

        skuMapper.deleteBySpuId(dto.getId());
        skuMapper.insertBatch(skus);


        // 注册事务回调：事务提交成功之后，才执行缓存删除
        Long spuId = spu.getId();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 事务提交成功后执行删除缓存
                    cache.evict(CACHE_KEY_DETAIL + dto.getId());   // 失效并广播
                }
            });
        }


    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        BizAssert.notNull(spuMapper.selectById(id), ResultCode.SPU_NOT_FOUND);
        spuMapper.updateStatus(id, status);
        skuMapper.updateStatusBySpuId(id, status);

        // 注册事务回调：事务提交成功之后，才执行缓存删除

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 事务提交成功后执行删除缓存
                    cache.evict(CACHE_KEY_DETAIL + id);            // 多级缓存
                }
            });
        }


    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizAssert.notNull(spuMapper.selectById(id), ResultCode.SPU_NOT_FOUND);
        spuMapper.deleteById(id);
        skuMapper.deleteBySpuId(id);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 事务提交成功后执行删除缓存
                    cache.evict(CACHE_KEY_DETAIL + id);            // 多级缓存
                }
            });
        }
    }

    private void applyFields(Spu spu, SpuDTO dto, List<Sku> skus) {
        spu.setName(dto.getName());
        spu.setCategoryId(dto.getCategoryId());
        spu.setBrandId(dto.getBrandId());
        spu.setMainImage(dto.getMainImage());
        spu.setImages(dto.getImages() == null ? "[]" : JsonUtils.toJson(dto.getImages()));
        spu.setDetail(dto.getDetail());
        spu.setUnit(dto.getUnit());
        spu.setIsColdChain(dto.getIsColdChain() == null ? 0 : dto.getIsColdChain());
        spu.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        spu.setPriceMin(skus.stream().mapToLong(Sku::getPrice).min().orElse(0L));
        spu.setPriceMax(skus.stream().mapToLong(Sku::getPrice).max().orElse(0L));
    }

    private List<Sku> buildSkus(Long spuId, List<SkuDTO> skuDTOS) {
        BizAssert.notEmpty(skuDTOS, ResultCode.PARAM_ERROR);
        List<Sku> list = new ArrayList<>();
        for (SkuDTO d : skuDTOS) {
            long skuId = IdWorker.getId();
            Sku sku = new Sku();
            sku.setId(skuId);
            sku.setSpuId(spuId);
            sku.setSkuCode("SKU" + skuId);
            sku.setName(d.getName());
            sku.setSpecJson(d.getSpecJson());
            sku.setImage(d.getImage());
            sku.setPrice(d.getPrice());
            sku.setStatus(d.getStatus() == null ? 1 : d.getStatus());
            list.add(sku);
        }
        return list;
    }

    private List<String> parseImages(String images) {
        if (!StringUtils.hasText(images)) {
            return List.of();
        }
        return JsonUtils.parse(images, new TypeReference<List<String>>() {});
    }
}