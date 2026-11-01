package com.chat.older.xiechen.service;

import com.chat.older.xiechen.dao.CommunityNewsMapper;
import com.chat.older.xiechen.dao.CommunityNewsReadMapper;
import com.chat.older.xiechen.dao.NewsStatusChangeLogMapper;
import com.chat.older.xiechen.model.CommunityNews;
import com.chat.older.xiechen.model.NewsStatusChangeLog;
import com.chat.older.xiechen.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommunityNewsServiceTest {

    @Mock
    private CommunityNewsMapper communityNewsMapper;

    @Mock
    private CommunityNewsReadMapper communityNewsReadMapper;

    @Mock
    private NewsStatusChangeLogMapper newsStatusChangeLogMapper;

    @Mock
    private HttpSession session;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private CommunityNewsService communityNewsService;

    private User testUser;
    private CommunityNews testNews;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setNickname("测试管理员");

        testNews = new CommunityNews();
        testNews.setId(100L);
        testNews.setTitle("测试新闻标题");
        testNews.setContent("测试新闻内容");
        testNews.setCommunityId(1);
        testNews.setStatus(1);
    }

    @Test
    @DisplayName("测试：已发布状态变更为草稿状态时，已读记录被正确删除")
    void testPublishedToDraft_ShouldDeleteReadRecords() {
        when(session.getAttribute("currUser")).thenReturn(testUser);
        when(communityNewsMapper.getByIdForAdmin(100L)).thenReturn(testNews);
        when(communityNewsReadMapper.countByNewsId(100L)).thenReturn(5);
        when(communityNewsReadMapper.deleteByNewsId(100L)).thenReturn(5);
        when(communityNewsMapper.update(any(CommunityNews.class))).thenReturn(1);
        when(newsStatusChangeLogMapper.insert(any(NewsStatusChangeLog.class))).thenReturn(1);
        when(request.getHeader(anyString())).thenReturn("127.0.0.1");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        Map<String, Object> params = Map.of(
                "title", "修改后的标题",
                "content", "修改后的内容",
                "category", "社区公告",
                "coverImage", "",
                "pubType", "draft"
        );

        Map<String, Object> result = communityNewsService.updateNewsWithStatusChange(100L, params, session, request);

        assertEquals(200, result.get("code"));
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals(5, data.get("clearedReadCount"));
        assertEquals(1, data.get("oldStatus"));
        assertEquals(0, data.get("newStatus"));

        verify(communityNewsReadMapper, times(1)).countByNewsId(100L);
        verify(communityNewsReadMapper, times(1)).deleteByNewsId(100L);
        verify(newsStatusChangeLogMapper, times(1)).insert(any(NewsStatusChangeLog.class));
    }

    @Test
    @DisplayName("测试：未发布状态变更时，不执行已读记录删除操作")
    void testUnpublishedToDraft_ShouldNotDeleteReadRecords() {
        testNews.setStatus(0);

        when(session.getAttribute("currUser")).thenReturn(testUser);
        when(communityNewsMapper.getByIdForAdmin(100L)).thenReturn(testNews);
        when(communityNewsMapper.update(any(CommunityNews.class))).thenReturn(1);
        when(request.getHeader(anyString())).thenReturn("127.0.0.1");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        Map<String, Object> params = Map.of(
                "title", "修改后的标题",
                "content", "修改后的内容",
                "category", "社区公告",
                "coverImage", "",
                "pubType", "draft"
        );

        Map<String, Object> result = communityNewsService.updateNewsWithStatusChange(100L, params, session, request);

        assertEquals(200, result.get("code"));
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals(0, data.get("clearedReadCount"));

        verify(communityNewsReadMapper, never()).countByNewsId(anyLong());
        verify(communityNewsReadMapper, never()).deleteByNewsId(anyLong());
    }

    @Test
    @DisplayName("测试：定时发布状态变更为草稿状态时，不删除已读记录")
    void testScheduledToDraft_ShouldNotDeleteReadRecords() {
        testNews.setStatus(2);

        when(session.getAttribute("currUser")).thenReturn(testUser);
        when(communityNewsMapper.getByIdForAdmin(100L)).thenReturn(testNews);
        when(communityNewsMapper.update(any(CommunityNews.class))).thenReturn(1);
        when(request.getHeader(anyString())).thenReturn("127.0.0.1");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        Map<String, Object> params = Map.of(
                "title", "修改后的标题",
                "content", "修改后的内容",
                "category", "社区公告",
                "coverImage", "",
                "pubType", "draft"
        );

        Map<String, Object> result = communityNewsService.updateNewsWithStatusChange(100L, params, session, request);

        assertEquals(200, result.get("code"));
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals(0, data.get("clearedReadCount"));

        verify(communityNewsReadMapper, never()).deleteByNewsId(anyLong());
    }

    @Test
    @DisplayName("测试：已发布状态保持不变时，不删除已读记录")
    void testPublishedToPublished_ShouldNotDeleteReadRecords() {
        testNews.setStatus(1);

        when(session.getAttribute("currUser")).thenReturn(testUser);
        when(communityNewsMapper.getByIdForAdmin(100L)).thenReturn(testNews);
        when(communityNewsMapper.update(any(CommunityNews.class))).thenReturn(1);
        when(request.getHeader(anyString())).thenReturn("127.0.0.1");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        Map<String, Object> params = Map.of(
                "title", "修改后的标题",
                "content", "修改后的内容",
                "category", "社区公告",
                "coverImage", "",
                "pubType", "now"
        );

        Map<String, Object> result = communityNewsService.updateNewsWithStatusChange(100L, params, session, request);

        assertEquals(200, result.get("code"));
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals(0, data.get("clearedReadCount"));

        verify(communityNewsReadMapper, never()).deleteByNewsId(anyLong());
    }

    @Test
    @DisplayName("测试：删除新闻时同时删除已读记录")
    void testDeleteNews_ShouldDeleteReadRecords() {
        when(session.getAttribute("currUser")).thenReturn(testUser);
        when(communityNewsMapper.getByIdForAdmin(100L)).thenReturn(testNews);
        when(communityNewsReadMapper.countByNewsId(100L)).thenReturn(3);
        when(communityNewsReadMapper.deleteByNewsId(100L)).thenReturn(3);
        when(communityNewsMapper.deleteById(100L)).thenReturn(1);

        Map<String, Object> result = communityNewsService.deleteNews(100L, session);

        assertEquals(200, result.get("code"));
        verify(communityNewsReadMapper, times(1)).countByNewsId(100L);
        verify(communityNewsReadMapper, times(1)).deleteByNewsId(100L);
        verify(communityNewsMapper, times(1)).deleteById(100L);
    }

    @Test
    @DisplayName("测试：并发操作下的数据一致性验证")
    void testConcurrentUpdate_ShouldMaintainConsistency() throws InterruptedException {
        when(session.getAttribute("currUser")).thenReturn(testUser);
        when(communityNewsMapper.getByIdForAdmin(100L)).thenReturn(testNews);
        when(communityNewsReadMapper.countByNewsId(100L)).thenReturn(10);
        when(communityNewsReadMapper.deleteByNewsId(100L)).thenReturn(10);
        when(communityNewsMapper.update(any(CommunityNews.class))).thenReturn(1);
        when(newsStatusChangeLogMapper.insert(any(NewsStatusChangeLog.class))).thenReturn(1);
        when(request.getHeader(anyString())).thenReturn("127.0.0.1");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Map<String, Object> params = Map.of(
                            "title", "并发测试标题",
                            "content", "并发测试内容",
                            "category", "社区公告",
                            "coverImage", "",
                            "pubType", "draft"
                    );

                    Map<String, Object> result = communityNewsService.updateNewsWithStatusChange(100L, params, session, request);
                    if ((Integer) result.get("code") == 200) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertTrue(successCount.get() > 0, "至少有一个请求应该成功");
    }

    @Test
    @DisplayName("测试：未登录用户操作返回401")
    void testUnauthenticatedUser_ShouldReturn401() {
        when(session.getAttribute("currUser")).thenReturn(null);

        Map<String, Object> params = Map.of(
                "title", "测试标题",
                "content", "测试内容",
                "category", "社区公告",
                "coverImage", "",
                "pubType", "draft"
        );

        Map<String, Object> result = communityNewsService.updateNewsWithStatusChange(100L, params, session, request);

        assertEquals(401, result.get("code"));
        assertEquals("未登录", result.get("msg"));
    }

    @Test
    @DisplayName("测试：新闻不存在时返回404")
    void testNonExistentNews_ShouldReturn404() {
        when(session.getAttribute("currUser")).thenReturn(testUser);
        when(communityNewsMapper.getByIdForAdmin(999L)).thenReturn(null);

        Map<String, Object> params = Map.of(
                "title", "测试标题",
                "content", "测试内容",
                "category", "社区公告",
                "coverImage", "",
                "pubType", "draft"
        );

        Map<String, Object> result = communityNewsService.updateNewsWithStatusChange(999L, params, session, request);

        assertEquals(404, result.get("code"));
        assertEquals("新闻不存在", result.get("msg"));
    }

    @Test
    @DisplayName("测试：操作日志正确记录")
    void testOperationLog_ShouldBeRecordedCorrectly() {
        when(session.getAttribute("currUser")).thenReturn(testUser);
        when(communityNewsMapper.getByIdForAdmin(100L)).thenReturn(testNews);
        when(communityNewsReadMapper.countByNewsId(100L)).thenReturn(7);
        when(communityNewsReadMapper.deleteByNewsId(100L)).thenReturn(7);
        when(communityNewsMapper.update(any(CommunityNews.class))).thenReturn(1);
        when(newsStatusChangeLogMapper.insert(any(NewsStatusChangeLog.class))).thenAnswer(invocation -> {
            NewsStatusChangeLog log = invocation.getArgument(0);
            assertEquals(100L, log.getNewsId());
            assertEquals(1, log.getOldStatus());
            assertEquals(0, log.getNewStatus());
            assertEquals(7, log.getClearedReadCount());
            assertEquals("测试管理员", log.getOperatorName());
            return 1;
        });
        when(request.getHeader(anyString())).thenReturn("192.168.1.100");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        Map<String, Object> params = Map.of(
                "title", "修改后的标题",
                "content", "修改后的内容",
                "category", "社区公告",
                "coverImage", "",
                "pubType", "draft"
        );

        communityNewsService.updateNewsWithStatusChange(100L, params, session, request);

        verify(newsStatusChangeLogMapper, times(1)).insert(any(NewsStatusChangeLog.class));
    }
}
