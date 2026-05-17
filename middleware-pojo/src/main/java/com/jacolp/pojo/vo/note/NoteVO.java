package com.jacolp.pojo.vo.note;

import java.io.Serializable;

import com.jacolp.pojo.entity.NoteEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true) // 显式声明：比较时包含父类字段
public class NoteVO extends NoteEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private String topicName;
}