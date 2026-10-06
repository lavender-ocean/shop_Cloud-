package config;


import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import handler.MyMetaObjectHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import util.IdGenerator;

/**
 * MyBatis-Plus 自动装配：三大拦截器 + 自动填充。
 */
@AutoConfiguration
public class ShopMybatisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 1. 分页（限制单页最大条数，防恶意大分页）
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        // 2. 乐观锁（配合 @Version）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 3. 防全表更新 / 删除（拦截没有 where 的 update/delete）
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }
    @Bean
    @ConditionalOnMissingBean
    public MetaObjectHandler metaObjectHandler() {
        return new MyMetaObjectHandler();
    }


//    @Bean
//    @ConditionalOnMissingBean
//    public IdGenerator idGenerator(@Value("${shop.datacenter-id:1}") long datacenterId,
//                                   @Value("${shop.worker-id:1}") long workerId) {
//        return new IdGenerator(datacenterId, workerId);
//    }

}
