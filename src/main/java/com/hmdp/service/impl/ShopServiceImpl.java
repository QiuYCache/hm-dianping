package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;


    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
//        Shop shop = cacheClient
//                .queryWithPassThrough(CACHE_SHOP_KEY,id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        Shop shop = cacheClient
                .queryWithLogicalExpire(CACHE_SHOP_KEY,id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);
        if (shop==null){
            return Result.fail("商户不存在");
        }
        return Result.ok(shop);
    }


    private static  final ExecutorService CACHE_REBUILD_EXECUTOR=Executors.newFixedThreadPool(10);

    /*public Shop queryWithLogicalExpire(Long id){
        String key=CACHE_SHOP_KEY+id;
        //1.从redis查询商户缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断缓存是否命中
        if(StrUtil.isBlank(json)){
            //3.未命中，返回空
            return null;
        }
        //4.命中，把json反序列化为Shop对象
        RedisData redisData = JSONUtil.toBean(json,RedisData.class);
        Shop shop=JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1未过期，直接返回店铺信息
            return shop;
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
                return shop;
            }
            //已过期
            //6.3成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //6.3.1释放互斥锁
                    unlock(lockKey);
                }
            });

        }
        //6.4失败，返回过期的店铺信息

        return shop;
    }*/

    /*public Shop queryWithMutex(Long id){
        String key=CACHE_SHOP_KEY+id;
        //1.从redis查询商户缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson,Shop.class);
        }
        if(shopJson!=null){
            return null;
        }
        //4实现缓存重建
        //4.1获取互斥锁
        String lockKey=LOCK_SHOP_KEY+id;
        //4.2判断是否获取到锁
        Shop shop = null;
        try {
            boolean isLock=tryLock(lockKey);
            if(!isLock){
                //4.3若获取锁失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //4.4获取锁成功，则根据id查询数据库
            shop = getById(id);
            //模拟重建的延时
            Thread.sleep(200);
            if(shop==null){
                //将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                //5.若数据库中不存在该商户,返回失败
                return null;
            }
            //6.存在，则写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //7.释放互斥锁
            unlock(lockKey);
        }
        //8.返回
        return shop;
    }*/

    /*public Shop queryWithPassThrough(Long id){
        String key=CACHE_SHOP_KEY+id;
        //1.从redis查询商户缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson,Shop.class);
        }
        if(shopJson!=null){
            return null;
        }

        //4.不存在，则根据id查询数据库
        Shop shop = getById(id);
        if(shop==null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //5.若数据库中不存在该商户,返回失败
            return null;
        }
        //6.存在，则写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7.返回
        return shop;
    }*/

    /*private boolean tryLock(String key){
        Boolean falg = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(falg);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }


    public void  saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        //1.查询店铺数据
        Shop shop = getById(id);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redisData.setData(shop);
        //3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(redisData));
    }*/

    @Override
    @Transactional
    public Result update(Shop shop) {
        if(shop.getId()==null){
            return Result.fail("商户id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除redis缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }
}
