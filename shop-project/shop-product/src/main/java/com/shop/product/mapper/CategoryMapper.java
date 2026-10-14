package com.shop.product.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.product.entity.Category;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CategoryMapper {

    /** 分页：MP 的 PaginationInnerInterceptor 会自动加 LIMIT，XML 里不用写 */
    List<Category> selectPage(IPage<Category> page, @Param("name") String name);

    //找所有的种类
    List<Category> selectAll();
    //根据id找到对应的种类
    Category selectById(@Param("id") Long id);
    //统计某父分类下子分类的数量
    int countByParentId(@Param("parentId") Long parentId);
    //插入某个种类
    int insert(Category category);
    //更新种类
    int update(Category category);
    //删除种类 逻辑删除
    int deleteById(@Param("id") Long id);
}