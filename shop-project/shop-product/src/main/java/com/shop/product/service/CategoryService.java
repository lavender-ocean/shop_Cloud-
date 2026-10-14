package com.shop.product.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.product.dto.CategoryDTO;
import com.shop.product.dto.CategoryQuery;
import com.shop.product.entity.Category;
import com.shop.product.mapper.CategoryMapper;
import com.shop.product.vo.CategoryVO;
import exception.BizAssert;
import org.springframework.stereotype.Service;
import result.PageResult;
import result.ResultCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    /** 分页（MP 分页插件） */
    public PageResult<Category> page(CategoryQuery query) {
        Page<Category> page = new Page<>(query.getPageNum(), query.getPageSize());
        List<Category> records = categoryMapper.selectPage(page, query.getName());
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    /** 树（用户端 + 管理端） */
    public List<CategoryVO> tree() {
        List<Category> all = categoryMapper.selectAll();
        Map<Long, List<Category>> byParent = all.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? 0L : c.getParentId()));
        return build(0L, byParent);
    }

    public void add(CategoryDTO dto) {
        // 只有"新建"才要求 parentId（0 表示一级类目）
        BizAssert.notNull(dto.getParentId(), ResultCode.PARAM_ERROR);
        // 顺带修掉审计里报的"可以造出孤儿节点"：父级必须真实存在
        if (dto.getParentId() != 0L) {
            BizAssert.notNull(categoryMapper.selectById(dto.getParentId()),
                    ResultCode.PARAM_ERROR);
        }

        Category category = new Category();
        category.setId(IdWorker.getId());                 // MP 雪花
        category.setParentId(dto.getParentId());
        category.setName(dto.getName());
        category.setLevel(dto.getParentId() == 0L ? 1 : 2);   // parentId 已在上面保证非 null，可省掉判空
        category.setSort(dto.getSort() == null ? 0 : dto.getSort());
        category.setIcon(dto.getIcon());
        category.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        categoryMapper.insert(category);
    }

    public void update(CategoryDTO dto) {
        BizAssert.notNull(dto.getId(), ResultCode.PARAM_ERROR);
        BizAssert.notNull(categoryMapper.selectById(dto.getId()), ResultCode.NOT_FOUND);
        Category category = new Category();
        category.setId(dto.getId());
        category.setName(dto.getName());
        category.setSort(dto.getSort());
        category.setIcon(dto.getIcon());
        category.setStatus(dto.getStatus());
        categoryMapper.update(category);
    }

    public void delete(Long id) {
        BizAssert.notNull(categoryMapper.selectById(id), ResultCode.NOT_FOUND);
        BizAssert.isTrue(categoryMapper.countByParentId(id) == 0, ResultCode.PARAM_ERROR, "存在子类目，无法删除");
        categoryMapper.deleteById(id);
    }

    private List<CategoryVO> build(Long parentId, Map<Long, List<Category>> byParent) {
        List<CategoryVO> result = new ArrayList<>();
        for (Category c : byParent.getOrDefault(parentId, List.of())) {
            CategoryVO vo = new CategoryVO();
            vo.setId(c.getId());
            vo.setParentId(c.getParentId());
            vo.setName(c.getName());
            vo.setLevel(c.getLevel());
            vo.setSort(c.getSort());
            vo.setIcon(c.getIcon());
            vo.setStatus(c.getStatus());
            vo.setChildren(build(c.getId(), byParent));
            result.add(vo);
        }
        return result;
    }
}