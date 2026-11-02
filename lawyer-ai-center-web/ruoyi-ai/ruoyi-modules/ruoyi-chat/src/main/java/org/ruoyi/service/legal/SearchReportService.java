package org.ruoyi.service.legal;

import cn.hutool.core.util.IdUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.legal.SearchReportInput.*;
import org.ruoyi.domain.vo.legal.SearchReportView;
import org.ruoyi.domain.vo.legal.SearchReportView.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import static org.ruoyi.service.legal.SearchReportSources.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchReportService {
    private final SearchReportRepository reports;
    private final SearchReportSources sources;
    private final SearchReportWord word;
    private final SearchReportFiles files;
    private static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")); }

    @Transactional(rollbackFor = Exception.class)
    public Report create(Owner owner, Create input) {
        requireText(input.title(), 200, "请填写报告标题（最长200字）");
        Long id = IdUtil.getSnowflakeNextId(); reports.create(owner, id, input.title().strip(), now());
        return reports.owned(owner, id, false);
    }
    @Transactional(readOnly = true)
    public TableDataInfo<Report> list(Owner owner, int page, int size) { return reports.list(owner, page, size); }
    @Transactional(readOnly = true)
    public Detail detail(Owner owner, Long id) {
        var report = reports.owned(owner, id, false); return new Detail(report, reports.items(report.id()));
    }
    @Transactional(rollbackFor = Exception.class)
    public Report update(Owner owner, Long id, Update input) {
        var report = reports.owned(owner, id, true);
        String title = input.title() == null ? report.title() : input.title().strip();
        String background = input.background() == null ? report.background() : input.background();
        String conclusion = input.conclusion() == null ? report.conclusion() : input.conclusion();
        requireText(title, 200, "报告标题不能为空且不得超过200字");
        bounded(background, 50000, "案情背景最长50000字"); bounded(conclusion, 50000, "检索结论最长50000字");
        if (!Objects.equals(title, report.title()) || !Objects.equals(background, report.background()) || !Objects.equals(conclusion, report.conclusion()))
            reports.update(owner, report, title, background, conclusion, now());
        return reports.owned(owner, id, false);
    }
    @Transactional(rollbackFor = Exception.class)
    public void delete(Owner owner, Long id) { reports.owned(owner, id, true); reports.delete(owner, id, now()); }

    @Transactional(rollbackFor = Exception.class)
    public Added add(Owner owner, Long id, AddItem input) {
        // Serializes add, deduplication, reorder, delete and generation, including across JVMs.
        reports.owned(owner, id, true);
        var snapshot = sources.resolve(input);
        var duplicate = reports.duplicateLaw(id, snapshot.lawKey());
        if (duplicate != null) return new Added(duplicate, true);
        var items = reports.items(id);
        if (items.size() >= 200) throw bad("每份报告最多200个条目");
        long total = items.stream().mapToLong(item -> item.snapshotContent().length()).sum() + snapshot.content().length();
        if (total > 1000000) throw bad("报告快照正文合计不得超过1000000字");
        int order = items.stream().mapToInt(Item::sortOrder).max().orElse(-1) + 1;
        var item = new Item(IdUtil.getSnowflakeNextId(), id, snapshot.type(), snapshot.refId(), snapshot.sectionKey(), snapshot.title(),
            snapshot.content(), snapshot.lawStatus(), snapshot.lawName(), snapshot.articleNo(), snapshot.sourceUrl(), order, now());
        reports.insert(item, snapshot.lawKey()); reports.draft(owner, id, now()); return new Added(item, false);
    }
    @Transactional(rollbackFor = Exception.class)
    public void order(Owner owner, Long id, Order input) {
        reports.owned(owner, id, true); var items = reports.items(id); var ids = input.itemIds();
        if (ids == null || ids.size() != items.size() || ids.size() > 200 || ids.stream().anyMatch(Objects::isNull)
            || new HashSet<>(ids).size() != ids.size() || !new HashSet<>(ids).equals(new HashSet<>(items.stream().map(Item::id).toList())))
            throw bad("排序必须包含当前报告的全部条目，且不得重复或混入其他报告条目");
        if (ids.equals(items.stream().map(Item::id).toList())) return;
        for (int i = 0; i < ids.size(); i++) reports.order(id, ids.get(i), i);
        reports.draft(owner, id, now());
    }
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Owner owner, Long id, Long itemId) {
        reports.owned(owner, id, true);
        if (!reports.deleteItem(id, itemId)) throw new ServiceException("报告条目不存在", 404);
        reports.draft(owner, id, now());
    }

    @Transactional(rollbackFor = Exception.class)
    public Generated generate(Owner owner, Long id) {
        var report = reports.owned(owner, id, true); var items = reports.items(id);
        if (items.isEmpty()) throw bad("请先添加报告条目再生成 Word");
        LocalDateTime generatedAt = now();
        byte[] document = word.generate(report, items, generatedAt);
        Path directory = null, file = null; Long uploadedId = null; boolean cleanupRegistered = false;
        try {
            directory = Files.createTempDirectory("legal-search-report-");
            file = directory.resolve("legal-search-report-" + id + ".docx"); Files.write(file, document);
            uploadedId = files.upload(file.toFile()).getOssId();
            Long asset = uploadedId;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) discard(asset);
                        else if (status != STATUS_COMMITTED) log.warn("search_report_commit status=UNKNOWN reportId={} ossId={}", id, asset);
                    }
                });
                cleanupRegistered = true;
            }
            reports.generated(owner, id, uploadedId, generatedAt);
            return new Generated(id, "generated", SearchReportView.downloadUrl(id));
        } catch (ServiceException e) {
            if (uploadedId != null && !cleanupRegistered) discard(uploadedId);
            throw e;
        } catch (Exception e) {
            if (uploadedId != null && !cleanupRegistered) discard(uploadedId);
            log.warn("search_report_generate status=FAILED reportId={} errorType={}", id, e.getClass().getSimpleName());
            throw new ServiceException("报告生成失败：保存文档或生成结果失败，请重试", 500);
        } finally {
            try { if (file != null) Files.deleteIfExists(file); if (directory != null) Files.deleteIfExists(directory); }
            catch (IOException e) { log.warn("search_report_temp_cleanup status=FAILED reportId={}", id); }
        }
    }
    private void discard(Long ossId) {
        try { files.discard(ossId); }
        catch (Exception e) { log.warn("search_report_rollback_cleanup status=FAILED ossId={}", ossId); }
    }
    public void download(Owner owner, Long id, HttpServletResponse response) throws IOException {
        var report = reports.owned(owner, id, false);
        if (!"generated".equals(report.status()) || report.fileOssId() == null)
            throw new ServiceException("报告尚未生成或已修改，请重新生成 Word", 409);
        try { files.download(report.fileOssId(), response); }
        catch (ServiceException e) { throw new ServiceException("报告下载失败：文件不存在或存储暂不可用，请重新生成或稍后重试", 503); }
    }
}
