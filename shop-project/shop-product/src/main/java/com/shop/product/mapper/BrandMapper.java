package com.shop.product.mapper;

import com.shop.product.entity.Brand;
import java.util.List;

public interface BrandMapper {
    //找到所有的品牌
    List<Brand> selectAll();
}