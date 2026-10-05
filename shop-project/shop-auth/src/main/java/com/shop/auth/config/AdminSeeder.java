package com.shop.auth.config;


import cn.hutool.crypto.digest.BCrypt;
import com.shop.auth.entity.SysUser;
import com.shop.auth.mapper.SysUserMapper;
import com.shop.auth.mapper.SysUserRoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import util.IdGenerator;

//@Slf4j
//@Component
//public class AdminSeeder implements CommandLineRunner {
//
//    private final SysUserMapper sysUserMapper;
//    private final IdGenerator idGenerator = new IdGenerator(1, 1);
//
//    public AdminSeeder(SysUserMapper sysUserMapper) {
//        this.sysUserMapper = sysUserMapper;
//    }
//
//    @Override
//    public void run(String... args) {
//        if (sysUserMapper.selectByUsername("admin") != null) {
//            return;
//        }
//        SysUser admin = new SysUser();
//        admin.setId(idGenerator.nextId());
//        admin.setUsername("admin");
//        admin.setPassword(BCrypt.hashpw("admin123"));
//        admin.setNickname("超级管理员");
//        admin.setUserType(2);
//        admin.setStatus(1);
//        sysUserMapper.insert(admin);
//        log.info("已初始化管理员账号: admin / admin123");
//    }
//}


@Slf4j
@Component
public class AdminSeeder implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final IdGenerator idGenerator = new IdGenerator(1, 1);

    public AdminSeeder(SysUserMapper sysUserMapper, SysUserRoleMapper sysUserRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    @Override
    public void run(String... args) {
        SysUser admin = sysUserMapper.selectByUsername("admin");
        if (admin == null) {
            admin = new SysUser();
            admin.setId(idGenerator.nextId());
            admin.setUsername("admin");
            admin.setPassword(BCrypt.hashpw("admin123"));
            admin.setNickname("超级管理员");
            admin.setUserType(2);
            admin.setStatus(1);
            sysUserMapper.insert(admin);
            admin = sysUserMapper.selectByUsername("admin");
            log.info("已初始化管理员账号: admin / admin123");
        }
        // 绑定 ROLE_ADMIN(id=1)
        sysUserRoleMapper.insertIgnore(idGenerator.nextId(), admin.getId(), 1L);
    }
}