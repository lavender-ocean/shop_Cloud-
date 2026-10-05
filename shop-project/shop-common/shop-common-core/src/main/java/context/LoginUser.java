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
    private List<String> permissions;
}