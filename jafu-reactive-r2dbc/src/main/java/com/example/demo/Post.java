package com.example.demo;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@Table("posts")
class Post {

    private Integer id;

    private String title;

    private String content;

    public Post(String title, String content) {
        this.title = title;
        this.content = content;
    }
}
