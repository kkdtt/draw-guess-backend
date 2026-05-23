package org.ctt.draw_guess.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.ctt.draw_guess.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper // 告诉Spring，这是一个 MyBatis 的数据库接口，赶紧帮我把它变成对象放进容器里
public interface SysUserMapper extends BaseMapper<SysUser> {
    // 💡 注意：你现在连一句 SQL 都不用写！BaseMapper 已经帮我们写好了所有基础增删改查方法！
}