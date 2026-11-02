package org.ruoyi.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.ruoyi.common.core.utils.MapstructUtils;
import org.ruoyi.system.domain.SysNotice;
import org.ruoyi.system.domain.bo.SysNoticeBo;
import org.ruoyi.system.mapper.*;
import org.ruoyi.system.service.impl.SysNoticeServiceImpl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Announcement management must not expose or mutate recipient-specific case notifications. */
@Tag("dev")
class NoticeAnnouncementPrivacyTest {
    SysNoticeMapper mapper=mock(SysNoticeMapper.class);SysNoticeServiceImpl service=new SysNoticeServiceImpl(mapper,mock(SysUserMapper.class));
    @BeforeAll static void metadata(){TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"notice-test"),SysNotice.class);}
    static boolean publicOnly(Wrapper<?> wrapper){return wrapper.getSqlSegment().contains("recipient_user_id IS NULL");}
    @Test void announcementListAndDetailAlwaysExcludeRecipientRows(){
        service.selectNoticeById(11L);verify(mapper).selectVoOne(argThat(NoticeAnnouncementPrivacyTest::publicOnly));
        service.selectNoticeList(new SysNoticeBo());verify(mapper).selectVoList(argThat(NoticeAnnouncementPrivacyTest::publicOnly));
    }
    @Test void announcementEditsAndBothDeletePathsCannotTargetPrivateRows(){
        var input=new SysNoticeBo();input.setNoticeId(11L);var notice=new SysNotice();notice.setNoticeId(11L);
        // MapstructUtils resolves its converter at class initialization, even when statically mocked.
        try(var spring=mockStatic(cn.hutool.extra.spring.SpringUtil.class)){
            spring.when(()->cn.hutool.extra.spring.SpringUtil.getBean(io.github.linpeilie.Converter.class)).thenReturn(mock(io.github.linpeilie.Converter.class));
            try(var convert=mockStatic(MapstructUtils.class)){
                convert.when(()->MapstructUtils.convert(input,SysNotice.class)).thenReturn(notice);
                service.updateNotice(input);verify(mapper).update(eq(notice),argThat(NoticeAnnouncementPrivacyTest::publicOnly));
            }
        }
        service.deleteNoticeById(11L);service.deleteNoticeByIds(new Long[]{11L,12L});verify(mapper,times(2)).delete(argThat(NoticeAnnouncementPrivacyTest::publicOnly));
        assertEquals(0,service.deleteNoticeByIds(new Long[0]));
    }
}
