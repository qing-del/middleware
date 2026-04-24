package com.jacolp.pojo.dto.note;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteVisibleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
}