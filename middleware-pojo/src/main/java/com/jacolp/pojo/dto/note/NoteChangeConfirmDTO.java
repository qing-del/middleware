package com.jacolp.pojo.dto.note;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteChangeConfirmDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * true: 确认修改；false: 取消修改
     */
    private Boolean confirm;
}