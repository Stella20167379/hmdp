package org.javaup.config;

import org.javaup.constant.LockInfoType;
import org.javaup.core.ManageLocker;
import org.javaup.lockinfo.LockInfoHandle;
import org.javaup.lockinfo.factory.LockInfoHandleFactory;
import org.javaup.lockinfo.impl.ServiceLockInfoHandle;
import org.javaup.servicelock.aspect.ServiceLockAspect;
import org.javaup.servicelock.factory.ServiceLockFactory;
import org.javaup.util.ServiceLockTool;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;

/**
 * @description: 配置
 **/
public class ServiceLockAutoConfiguration {
    
    @Bean(LockInfoType.SERVICE_LOCK)
    public LockInfoHandle serviceLockInfoHandle(){
//        用于反射获取注解锁信息
        return new ServiceLockInfoHandle();
    }
    
    @Bean
    public ManageLocker manageLocker(RedissonClient redissonClient){
//        用hashmap保存不同类型的redisson锁代理对象，并提供对象调用接口
        return new ManageLocker(redissonClient);
    }
    
    @Bean
    public ServiceLockFactory serviceLockFactory(ManageLocker manageLocker){
//        调用ManageLocker生产制定类型的锁对象
        return new ServiceLockFactory(manageLocker);
    }
    
    @Bean
    public ServiceLockAspect serviceLockAspect(LockInfoHandleFactory lockInfoHandleFactory,ServiceLockFactory serviceLockFactory){
//        分布式锁注解，自动添加上锁、锁失败处理代码
        return new ServiceLockAspect(lockInfoHandleFactory,serviceLockFactory);
    }
    
    @Bean
    public ServiceLockTool serviceLockTooL(LockInfoHandleFactory lockInfoHandleFactory,ServiceLockFactory serviceLockFactory){
//        提供手动获取redisson分布式锁对象及加锁解锁的方法
        return new ServiceLockTool(lockInfoHandleFactory,serviceLockFactory);
    }
}
