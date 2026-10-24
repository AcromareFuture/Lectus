package com.heng.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heng.config.BizException;
import com.heng.domain.VPushRecord;
import com.heng.service.VPushRecordService;
import com.heng.vo.PageResult;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final VPushRecordService pushRecordService;

    public MessageController(VPushRecordService pushRecordService) {
        this.pushRecordService = pushRecordService;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(@RequestAttribute("viridac.memberId") Long memberId,
                                                            @RequestParam(required = false) Boolean unread,
                                                            @RequestParam(defaultValue = "1") long page,
                                                            @RequestParam(defaultValue = "20") long size) {
        var query = pushRecordService.lambdaQuery().eq(VPushRecord::getMemberId, memberId)
                .orderByDesc(VPushRecord::getPushId);
        if (Boolean.TRUE.equals(unread)) query.isNull(VPushRecord::getReadTime);
        Page<VPushRecord> result = query.page(new Page<>(com.heng.vo.PageBounds.page(page), com.heng.vo.PageBounds.size(size)));
        List<Map<String, Object>> list = result.getRecords().stream().map(this::toView).toList();
        return ApiResponse.ok(new PageResult<>(list, result.getTotal(), result.getCurrent(),
                result.getSize(), result.hasNext()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount(@RequestAttribute("viridac.memberId") Long memberId) {
        long count = pushRecordService.lambdaQuery().eq(VPushRecord::getMemberId, memberId)
                .isNull(VPushRecord::getReadTime).count();
        return ApiResponse.ok(Map.of("unread", count));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> read(@RequestAttribute("viridac.memberId") Long memberId, @PathVariable Long id) {
        VPushRecord record = ownRecord(memberId, id);
        record.setReadTime(new Date());
        pushRecordService.updateById(record);
        return ApiResponse.ok(null);
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> readAll(@RequestAttribute("viridac.memberId") Long memberId) {
        pushRecordService.lambdaUpdate().eq(VPushRecord::getMemberId, memberId)
                .isNull(VPushRecord::getReadTime).set(VPushRecord::getReadTime, new Date()).update();
        return ApiResponse.ok(null);
    }

    private VPushRecord ownRecord(Long memberId, Long id) {
        VPushRecord record = pushRecordService.lambdaQuery().eq(VPushRecord::getPushId, id)
                .eq(VPushRecord::getMemberId, memberId).one();
        if (record == null) throw new BizException("消息不存在");
        return record;
    }

    private Map<String, Object> toView(VPushRecord record) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("pushId", record.getPushId());
        view.put("pushType", record.getPushType());
        view.put("title", record.getTitle());
        view.put("content", record.getContent());
        view.put("bizId", record.getBizId());
        view.put("read", record.getReadTime() != null);
        view.put("createTime", record.getCreateTime());
        return view;
    }
}
