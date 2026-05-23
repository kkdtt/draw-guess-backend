package org.ctt.draw_guess.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@TableName("game_word")
@Schema(description = "词库信息实体") // 使用 @Schema 替代 @ApiModel
public class GameWord {
    private String id;          //
    private String wordText;        //
    @Schema(description = "种类")
    private String category;
    @Schema(description = "难度(1简单, 2中等, 3困难)")
    private Integer difficulty;
    private Date createTime;
}
