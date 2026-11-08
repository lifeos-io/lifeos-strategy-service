package com.lifeos.strategy.knowledge.mapper;

import com.lifeos.strategy.knowledge.dto.KnowledgeItemResponse;
import com.lifeos.strategy.knowledge.entity.KnowledgeItem;
import com.lifeos.strategy.knowledge.event.KnowledgeItemChangedV1;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeItemMapper {

    public KnowledgeItemResponse toResponse(KnowledgeItem item) {
        return new KnowledgeItemResponse(item.getId(), item.getGoalId(), item.getStatement(), item.getKind(),
                item.getStatus(), item.isExternal(), item.getSource(), item.getHowToVerify(), item.getVerifiedAt(),
                item.getVersion(), item.getCreatedAt(), item.getUpdatedAt());
    }

    public KnowledgeItemChangedV1 toChangedEvent(KnowledgeItem item) {
        return new KnowledgeItemChangedV1(item.getId(), item.getGoalId(), item.getStatement(), item.getKind(),
                item.getStatus(), item.isExternal(), item.getSource(), item.getHowToVerify(), item.getVerifiedAt(),
                item.getVersion());
    }
}
