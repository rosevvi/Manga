package com.manga.repository;

import com.manga.entity.CreativeEvent;
import com.manga.mapper.CreativeEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 封装 Creative Event 追加日志的持久化访问。
 */
@Repository
@RequiredArgsConstructor
public class CreativeEventRepository {

    private final CreativeEventMapper mapper;

    /** 追加一条已分配序号的事件。 */
    public CreativeEvent create(CreativeEvent event) {
        mapper.insert(event);
        return event;
    }

    /** 查询指定游标后的已提交事件。 */
    public List<CreativeEvent> findAfter(String runId, long afterSequence) {
        return mapper.findAfter(runId, afterSequence);
    }
}
