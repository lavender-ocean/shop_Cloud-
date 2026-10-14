package com.shop.product.mapper;

import com.shop.api.dto.SkuInfoDTO;
import com.shop.product.entity.Sku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;


@Mapper
//Spu包括Sku
public interface SkuMapper {
    //查询这个spu下的所有的sku
    List<Sku> selectBySpuId(@Param("spuId") Long spuId);
    //批量插入sku
    int insertBatch(@Param("list") List<Sku> list);
    //删除sku 逻辑删除
    int deleteBySpuId(@Param("spuId") Long spuId);
    //更改sku的上架/下架状态
    int updateStatusBySpuId(@Param("spuId") Long spuId, @Param("status") Integer status);

    //根据id查找sku
    Sku selectById(@Param("id") Long id);


    /** 批量查 SKU + spuName（一次 join，供内部接口用） */
    List<SkuInfoDTO> selectInfoByIds(@Param("ids") List<Long> ids);

}