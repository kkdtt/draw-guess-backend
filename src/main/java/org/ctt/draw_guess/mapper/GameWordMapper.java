package org.ctt.draw_guess.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.ctt.draw_guess.entity.GameWord;

import java.util.List;
// ...
@Mapper
public interface GameWordMapper extends BaseMapper<GameWord> {

    // 直接让 MySQL 随机排序并限制返回 10 条，性能最高！
    @Select("SELECT * FROM game_word ORDER BY RAND() LIMIT #{limit}")
    List<GameWord> getRandomWords(int limit);
}