package org.javaup.servicelock.aspect;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.javaup.constant.LockInfoType;
import org.javaup.lockinfo.LockInfoHandle;
import org.javaup.lockinfo.factory.LockInfoHandleFactory;
import org.javaup.servicelock.LockType;
import org.javaup.servicelock.ServiceLocker;
import org.javaup.servicelock.annotion.ServiceLock;
import org.javaup.servicelock.factory.ServiceLockFactory;
import org.springframework.core.annotation.Order;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * @description: 切面
 **/
@Slf4j
@Aspect
@Order(-10)
@AllArgsConstructor
public class ServiceLockAspect {
    
    private final LockInfoHandleFactory lockInfoHandleFactory;
    
    private final ServiceLockFactory serviceLockFactory;
    

    @Around("@annotation(servicelock)")
    public Object around(ProceedingJoinPoint joinPoint, ServiceLock servicelock) throws Throwable {
//       拿到一个负责生成所信息的handle处理器
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.SERVICE_LOCK);
//        生成锁名称
        String lockName = lockInfoHandle.getLockName(joinPoint, servicelock.name(),servicelock.keys());
//        拿到其它的参数值
        LockType lockType = servicelock.lockType();
        long waitTime = servicelock.waitTime();
        TimeUnit timeUnit = servicelock.timeUnit();
//        工厂模式生成具体锁
        ServiceLocker lock = serviceLockFactory.getLock(lockType);
//        尝试上锁，最多等待时间waitTime
        boolean result = lock.tryLock(lockName, timeUnit, waitTime);
//      加锁成功
        if (result) {
            try {
//              核心：现在执行原来的业务方法
                return joinPoint.proceed();
            }finally{
                lock.unlock(lockName);
            }
        }else { //上锁超时
            log.warn("Timeout while acquiring serviceLock:{}",lockName);
//            允许用户在自定义的超时策略中选择
            String customLockTimeoutStrategy = servicelock.customLockTimeoutStrategy();
            if (StrUtil.isNotEmpty(customLockTimeoutStrategy)) {
                return handleCustomLockTimeoutStrategy(customLockTimeoutStrategy, joinPoint);
            }else{
//                如果调用注解时没有制定超时策略，执行info子目录下的默认策略
                servicelock.lockTimeoutStrategy().handler(lockName);
            }
//            加锁失败仍然执行原业务方法
            return joinPoint.proceed();
        }
    }

    public Object handleCustomLockTimeoutStrategy(String customLockTimeoutStrategy,JoinPoint joinPoint) {
        // prepare invocation context
//        通过反射实现功能：target.lockTimeout(原方法参数)
        Method currentMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
//        将原业务入口作为反射入口
        Object target = joinPoint.getTarget();
        Method handleMethod = null;
        try {
//          获取原业务所在类，并找到制定策略对应的函数
            handleMethod = target.getClass().getDeclaredMethod(customLockTimeoutStrategy, currentMethod.getParameterTypes());
            handleMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Illegal annotation param customLockTimeoutStrategy :" + customLockTimeoutStrategy,e);
        }
        Object[] args = joinPoint.getArgs();

        // invoke
        Object result;
        try {
//            反射调用获取到的目标函数、传参
            result = handleMethod.invoke(target, args);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Fail to illegal access custom lock timeout handler: " + customLockTimeoutStrategy ,e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Fail to invoke custom lock timeout handler: " + customLockTimeoutStrategy ,e);
        }
        return result;
    }
}
