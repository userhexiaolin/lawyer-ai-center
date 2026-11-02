package org.ruoyi.service.legal;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.vo.legal.SearchReportView.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Explicit owner + tenant predicates also apply to administrators. No team/shared report access. */
@Repository
@RequiredArgsConstructor
public class SearchReportRepository {
    private final JdbcTemplate jdbc;
    private static final String OWNED = " WHERE id=? AND tenant_id=? AND user_id=? AND del_flag=0";
    private static LocalDateTime time(ResultSet r, String key) throws SQLException {
        var value = r.getTimestamp(key); return value == null ? null : value.toLocalDateTime();
    }
    private static final RowMapper<Report> REPORT = (r, n) -> new Report(r.getLong("id"), r.getLong("user_id"),
        r.getString("title"), r.getString("status"), r.getString("background"), r.getString("conclusion"),
        time(r,"created_at"), time(r,"updated_at"), time(r,"generated_at"), r.getObject("file_oss_id", Long.class));
    private static final RowMapper<Item> ITEM = (r, n) -> new Item(r.getLong("id"), r.getLong("report_id"),
        r.getString("item_type"), r.getString("ref_id"), r.getString("section_key"), r.getString("snapshot_title"),
        r.getString("snapshot_content"), r.getString("law_status"), r.getString("law_name"), r.getString("article_no"),
        r.getString("source_url"), r.getInt("sort_order"), time(r,"created_at"));
    public Report owned(Owner owner, Long id, boolean lock) {
        var rows = jdbc.query("SELECT * FROM legal_search_report" + OWNED + (lock ? " FOR UPDATE" : ""),
            REPORT, id, owner.tenantId(), owner.userId());
        if (rows.size() != 1) throw new ServiceException("检索报告不存在或无访问权限", 404);
        return rows.get(0);
    }
    public void create(Owner owner, Long id, String title, LocalDateTime now) {
        jdbc.update("INSERT INTO legal_search_report(id,tenant_id,user_id,title,status,created_at,updated_at,del_flag) VALUES(?,?,?,?,'draft',?,?,0)",
            id, owner.tenantId(), owner.userId(), title, now, now);
    }
    public TableDataInfo<Report> list(Owner owner, int page, int size) {
        int boundedSize = Math.max(1, Math.min(100, size)); long offset = (long)(Math.max(1, page)-1) * boundedSize;
        String filter = " WHERE tenant_id=? AND user_id=? AND del_flag=0";
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM legal_search_report" + filter, Long.class, owner.tenantId(), owner.userId());
        var rows = jdbc.query("SELECT * FROM legal_search_report" + filter + " ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?",
            REPORT, owner.tenantId(), owner.userId(), boundedSize, offset);
        return new TableDataInfo<>(rows, count == null ? 0 : count);
    }
    public List<Item> items(Long reportId) {
        return jdbc.query("SELECT * FROM legal_search_report_item WHERE report_id=? ORDER BY sort_order,id", ITEM, reportId);
    }
    public void update(Owner owner, Report previous, String title, String background, String conclusion, LocalDateTime now) {
        jdbc.update("UPDATE legal_search_report SET title=?,background=?,conclusion=?,status='draft',updated_at=?" + OWNED,
            title, background, conclusion, now, previous.id(), owner.tenantId(), owner.userId());
    }
    public void delete(Owner owner, Long id, LocalDateTime now) {
        jdbc.update("UPDATE legal_search_report SET del_flag=2,updated_at=?" + OWNED, now, id, owner.tenantId(), owner.userId());
    }
    public void draft(Owner owner, Long id, LocalDateTime now) {
        jdbc.update("UPDATE legal_search_report SET status='draft',updated_at=?" + OWNED, now, id, owner.tenantId(), owner.userId());
    }
    public Item duplicateLaw(Long reportId, String lawKey) {
        if (lawKey == null) return null;
        var rows = jdbc.query("SELECT * FROM legal_search_report_item WHERE report_id=? AND law_key=?", ITEM, reportId, lawKey);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public void insert(Item item, String lawKey) {
        jdbc.update("INSERT INTO legal_search_report_item(id,report_id,item_type,ref_id,section_key,snapshot_title,snapshot_content,law_status,law_name,article_no,source_url,law_key,sort_order,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            item.id(), item.reportId(), item.itemType(), item.refId(), item.sectionKey(), item.snapshotTitle(), item.snapshotContent(),
            item.lawStatus(), item.lawName(), item.articleNo(), item.sourceUrl(), lawKey, item.sortOrder(), item.createdAt());
    }
    public void order(Long reportId, Long itemId, int order) {
        jdbc.update("UPDATE legal_search_report_item SET sort_order=? WHERE report_id=? AND id=?", order, reportId, itemId);
    }
    public boolean deleteItem(Long reportId, Long itemId) {
        return jdbc.update("DELETE FROM legal_search_report_item WHERE report_id=? AND id=?", reportId, itemId) == 1;
    }
    public void generated(Owner owner, Long id, Long ossId, LocalDateTime now) {
        if (jdbc.update("UPDATE legal_search_report SET status='generated',file_oss_id=?,generated_at=?,updated_at=?" + OWNED,
            ossId, now, now, id, owner.tenantId(), owner.userId()) != 1) throw new ServiceException("报告生成失败：保存生成结果失败", 500);
    }
}
