package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.model.User;
import com.chat.older.xiechen.service.MedicineDraftService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medicine-draft")
public class MedicineDraftController {

    @Autowired
    private MedicineDraftService draftService;

    @PostMapping("/create")
    public Result createDraft(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        if (params.get("medicineName") == null || params.get("medicineName").toString().isEmpty()) {
            return Result.error("请提供药品名称");
        }
        
        return draftService.createDraft(user.getId(), params);
    }

    @GetMapping("/pending")
    public Result getPendingDrafts(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        List<Map<String, Object>> drafts = draftService.getPendingDraftsForFamily(user.getId());
        return Result.success(drafts);
    }

    @GetMapping("/all")
    public Result getAllDrafts(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        List<Map<String, Object>> drafts = draftService.getAllDraftsForFamily(user.getId());
        return Result.success(drafts);
    }

    @GetMapping("/detail/{id}")
    public Result getDraftDetail(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        Map<String, Object> draft = draftService.getDraftDetail(id);
        if (draft == null) {
            return Result.error("草稿不存在");
        }
        return Result.success(draft);
    }

    @PostMapping("/approve/{id}")
    public Result approveDraft(@PathVariable Long id, 
                               @RequestBody(required = false) Map<String, String> params,
                               HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String reviewNote = params != null ? params.get("reviewNote") : null;
        return draftService.approveDraft(user.getId(), id, reviewNote);
    }

    @PostMapping("/approve-with-edit/{id}")
    public Result approveDraftWithEdit(@PathVariable Long id,
                                        @RequestBody Map<String, Object> params,
                                        HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> modifiedData = (Map<String, Object>) params.get("modifiedData");
        String reviewNote = (String) params.get("reviewNote");
        
        if (modifiedData == null || modifiedData.isEmpty()) {
            return Result.error("请提供修改后的数据");
        }
        
        return draftService.approveDraftWithEdit(user.getId(), id, modifiedData, reviewNote);
    }

    @PostMapping("/reject/{id}")
    public Result rejectDraft(@PathVariable Long id,
                              @RequestBody Map<String, String> params,
                              HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String reviewNote = params.get("reviewNote");
        if (reviewNote == null || reviewNote.isEmpty()) {
            return Result.error("请提供驳回原因");
        }
        
        return draftService.rejectDraft(user.getId(), id, reviewNote);
    }

    @PostMapping("/delete/{id}")
    public Result deleteDraft(@PathVariable Long id,
                              @RequestBody(required = false) Map<String, String> params,
                              HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String reviewNote = params != null ? params.get("reviewNote") : "误录入删除";
        return draftService.deleteDraft(user.getId(), id, reviewNote);
    }

    @GetMapping("/my-drafts")
    public Result getMyDrafts(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        List<Map<String, Object>> drafts = draftService.getDraftsByElder(user.getId());
        return Result.success(drafts);
    }

    @GetMapping("/my-pending-drafts")
    public Result getMyPendingDrafts(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        List<Map<String, Object>> drafts = draftService.getPendingDraftsByElder(user.getId());
        return Result.success(drafts);
    }

    @GetMapping("/pending-count")
    public Result getPendingDraftCount(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        int count = draftService.getPendingDraftCountForFamily(user.getId());
        return Result.success(count);
    }

    @PostMapping("/request-update/{medicineId}")
    public Result requestUpdateMedicine(@PathVariable Integer medicineId,
                                         @RequestBody Map<String, Object> params,
                                         HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        return draftService.createUpdateDraft(user.getId(), medicineId, params);
    }

    @PostMapping("/request-delete/{medicineId}")
    public Result requestDeleteMedicine(@PathVariable Integer medicineId,
                                         @RequestBody(required = false) Map<String, String> params,
                                         HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String reason = params != null ? params.get("reason") : null;
        return draftService.createDeleteDraft(user.getId(), medicineId, reason);
    }
}
