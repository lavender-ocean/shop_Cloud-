package context;


import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class LoginUser implements Serializable {

    private Long userId;
    private String username;
    /** 1 普通用户 2 管理员 */
    private Integer userType;
    private List<String> roles;
    /** 当前用户的权限码（来自 token / 网关透传） */
    private List<String> permissions;


}