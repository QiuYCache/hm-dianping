package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }
    public void  setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisData.setData(value);
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    public <R,ID> R queryWithPassThrough(
            String preFix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){
        String key=preFix+id;
        //1.从redis查询商户缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(json)){
            //3.存在，直接返回
            return JSONUtil.toBean(json,type);
        }
        if(json!=null){
            return null;
        }

        //4.不存在，则根据id查询数据库
        R r = dbFallback.apply(id);
        if(r==null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //5.若数据库中不存在该商户,返回失败
            return null;
        }
        //6.存在，则写入redis
        this.set(key,r,time,unit);
        //7.返回
        return r;
    }


    private static  final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

    public <R,ID> R queryWithLogicalExpire(
            String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time, TimeUnit unit){
        String key=keyPrefix+id;
        //1.从redis查询商户缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否命中
        if(StrUtil.isBlank(json)){
            //3.未命中，返回空
            return null;
        }
        //4.命中，把json反序列化为Shop对象
        com.hmdp.entity.RedisData redisData = JSONUtil.toBean(json, com.hmdp.entity.RedisData.class);
        R r=JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1未过期，直接返回店铺信息
            return r;
        }
        //5.2已过期，寻要进行缓存重建
        //6.缓存重建
        //6.1获取互斥锁
        String lockKey=LOCK_SHOP_KEY+id;
        boolean isLock=tryLock(lockKey);
        //6.2判断是否获取锁成功
        if (isLock){
            //再次检查缓存是否过期
            if(expireTime.isAfter(LocalDateTime.now())){
                //未过期，直接返回店铺信息
                return r;
            }
            //已过期
            //6.3成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    //存入redis
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //6.3.1释放互斥锁
                    unlock(lockKey);
                }
            });

        }
        //6.4失败，返回过期的店铺信息
        return r;
    }

    private boolean tryLock(String key){
        Boolean falg = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(falg);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
