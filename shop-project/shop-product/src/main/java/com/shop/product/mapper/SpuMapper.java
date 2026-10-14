package com.shop.product.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.product.dto.SpuQuery;
import com.shop.product.entity.Spu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;



@Mapper
//Spu包括Sku
public interface SpuMapper {
    //分页查找
    List<Spu> selectPage(IPage<Spu> page, @Param("q") SpuQuery query);
    //根据id查找spu
    Spu selectById(@Param("id") Long id);
    //插入记录
    int insert(Spu spu);
    //更新
    int update(Spu spu);
    //更新状态 上下架
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
    //逻辑删除
    int deleteById(@Param("id") Long id);
}