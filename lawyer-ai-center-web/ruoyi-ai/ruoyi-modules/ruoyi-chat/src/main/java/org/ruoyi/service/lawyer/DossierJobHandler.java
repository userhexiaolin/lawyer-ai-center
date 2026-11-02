package org.ruoyi.service.lawyer;

import org.ruoyi.domain.entity.lawyer.CaseParseJob;

/** Modules share the durable queue and fenced worker, never a second in-memory task system. */
public interface DossierJobHandler {
    boolean supports(String type);
    void execute(CaseParseJob job, String lease);
}
