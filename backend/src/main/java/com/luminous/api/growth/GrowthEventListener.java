package com.luminous.api.growth;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 强制加入现有业务事务，避免没有事务的事件发布只提交部分数据。 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class GrowthEventListener {
    private final GrowthService service;
    public GrowthEventListener(GrowthService service) { this.service = service; }
    @EventListener public void registered(GrowthEvent.Registered event) { service.initialize(event.userId(), 0); }
    @EventListener public void joined(GrowthEvent.Joined event) { service.initialize(event.userId(), event.communityId()); }
    @EventListener public void checkedIn(GrowthEvent.CheckedIn event) { service.checkIn(event.userId(), event.communityId(), event.date()); }
    @EventListener public void created(GrowthEvent.Created event) { service.created(event.contentType(), event.contentId()); }
    @EventListener public void liked(GrowthEvent.Liked event) { service.liked(event.contentType(), event.contentId(), event.actorId()); }
    @EventListener public void removed(GrowthEvent.Removed event) { service.removed(event.contentType(), event.contentId(), event.moderated(), event.reason()); }
}
