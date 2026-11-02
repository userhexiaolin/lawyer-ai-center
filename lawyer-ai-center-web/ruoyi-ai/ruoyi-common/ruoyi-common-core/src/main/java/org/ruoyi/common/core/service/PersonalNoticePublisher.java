package org.ruoyi.common.core.service;

/** Publishes into the existing system notice inbox; never broadcasts personal case data. */
public interface PersonalNoticePublisher {
    record Delivery(String tenantId,Long userId,String key,String title,String content,Long caseId) {}
    /** Returns false only when this recipient/business key was already delivered. */
    boolean publish(Delivery delivery);
}
