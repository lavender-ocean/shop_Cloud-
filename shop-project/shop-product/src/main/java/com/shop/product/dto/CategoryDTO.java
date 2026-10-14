package com.shop.product.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryDTO {
    /** 编辑时传 */
    private Long id;

    /**
     * 父级 id（0 = 一级类目）。
     * 仅【新建】必填 —— 更新不允许改父级，故不能加 @NotNull（否则编辑类目会被误拦）。
     * 新建时的必填校验在 CategoryService.add() 里做。
     */
    private Long parentId;

    @NotBlank(message = "类目名称不能为空")
    private String name;

    private Integer sort;
    private String icon;
    private Integer status;
}