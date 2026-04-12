package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result show() {
        String key="SHOP_TYPE";
        //从redis缓存中找商户类型
        String shopType = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if(StrUtil.isNotBlank(shopType)){
            //若存在，返回商户类型列表
            List<ShopType> shopTypeList= JSONUtil.toList(shopType,ShopType.class);
            return Result.ok(shopTypeList);
        }
        //若不存在,查询数据库
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        //如果数据库中没找到，直接返回
        if(shopTypes.isEmpty()){
            return Result.fail("未查找到商户信息");
        }
        //若数据库中找到，缓存到redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopTypes));
        return Result.ok(shopTypes);


    }
}
