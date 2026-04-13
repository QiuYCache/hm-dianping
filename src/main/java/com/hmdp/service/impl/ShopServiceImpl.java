package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

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


    @Override
    public Result queryById(Long id) {
        String key=CACHE_SHOP_KEY+id;
        //1.从redis查询商户缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //3.存在，直接返回
            Shop shop= JSONUtil.toBean(shopJson,Shop.class);
            return Result.ok(shop);
        }
        if(shopJson!=null){
            return Result.fail("商户不存在");
        }

        //4.不存在，则根据id查询数据库
        Shop shop = baseMapper.selectById(id);
        if(shop==null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //5.若数据库中不存在该商户,返回失败
            return Result.fail("商户不存在");
        }
        //6.存在，则写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7.返回
        return Result.ok(shop);

    }

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
