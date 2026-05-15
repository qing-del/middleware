package com.jacolp.pojo.vo.note;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.jacolp.pojo.entity.NoteEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteVO extends NoteEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private String topicName;
}