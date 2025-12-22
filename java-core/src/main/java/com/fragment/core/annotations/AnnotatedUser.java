package com.fragment.core.annotations;

import java.util.List;

@RepeatableTag("domain:user")
@RepeatableTag("tier:free")
@Role("admin")
public class AnnotatedUser {
    @Min(1)
    private long id;

    private List<@TypeUseAnno String> tags;

    public AnnotatedUser(@Min(1) long id, List<@TypeUseAnno String> tags) {
        this.id = id;
        this.tags = tags;
    }

    public long getId() { return id; }
    public List<String> getTags() { return tags; }
}
