package org.ruoyi.service.lawyer;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.entity.lawyer.LawyerCase;
import org.ruoyi.mapper.lawyer.LawyerCaseMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CaseAccess {
    private final LawyerCaseMapper mapper;
    public LawyerCase owned(Long id) { return owned(id,false); }
    /** Mutation paths take this row lock to serialize case deletion and new associations. */
    public LawyerCase owned(Long id, boolean lock) {
        if(id==null||id<=0)throw new ServiceException("案件不可用",404);
        var query=Wrappers.<LawyerCase>lambdaQuery().eq(LawyerCase::getId,id)
            .eq(LawyerCase::getUserId,LoginHelper.getUserId()).eq(LawyerCase::getTenantId,LoginHelper.getTenantId());
        if(lock)query.last("FOR UPDATE");
        var value=mapper.selectOne(query);
        if(value==null)throw new ServiceException("案件不可用",404);
        return value;
    }
}
