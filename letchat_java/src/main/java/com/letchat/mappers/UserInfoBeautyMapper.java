package com.letchat.mappers;

import org.apache.ibatis.annotations.Param;

/**
 * 靓号表 数据库操作接口
 */
public interface UserInfoBeautyMapper<T, P> extends BaseMapper<T, P> {

    /**
     * 根据Id更新
     */
    Integer updateById(@Param("bean") T t, @Param("id") Integer id);


    /**
     * 根据Id删除
     */
    Integer deleteById(@Param("id") Integer id);


    /**
     * 根据Id获取对象
     */
    T selectById(@Param("id") Integer id);


    /**
     * 根据Email更新
     */
    Integer updateByEmail(@Param("bean") T t, @Param("email") String email);


    /**
     * 根据Email删除
     */
    Integer deleteByEmail(@Param("email") String email);


    /**
     * 根据Email获取对象
     */
    T selectByEmail(@Param("email") String email);


    T selectByUserId(@Param("userId") String userId);
}
