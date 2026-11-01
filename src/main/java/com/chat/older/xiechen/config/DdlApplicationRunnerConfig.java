package com.chat.older.xiechen.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DdlApplicationRunnerConfig {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Bean(name = "customDdlApplicationRunner")
    public ApplicationRunner customDdlApplicationRunner() {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) throws Exception {
                initCommunityNewsData();
                System.out.println("customDdlApplicationRunner executed successfully");
            }
        };
    }

    private void initCommunityNewsData() {
        try {
            System.out.println("开始检查社区新闻数据...");
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM community_news", Integer.class);
            System.out.println("当前社区新闻数量: " + count);
            
            if (count != null && count == 0) {
                System.out.println("开始插入社区新闻测试数据...");
                String insertNews = "INSERT INTO community_news " +
                    "(community_id, title, content, category, cover_image, view_count, like_count, status, publish_time, publisher_id) VALUES " +
                    "(1, '希望社区2026年春节联欢晚会圆满举办', '亲爱的居民朋友们：\\n\\n2026年1月28日晚，希望社区在社区活动中心成功举办了新春联欢晚会。本次晚会吸引了超过200位居民参与，节目丰富多彩，包括歌舞、小品、戏曲等精彩表演。\\n\\n晚会现场气氛热烈，居民们欢聚一堂，共同迎接新春的到来。社区主任在晚会上致辞，感谢居民们一年来对社区工作的支持，并祝愿大家新年快乐、阖家幸福。\\n\\n特此公告。', '社区公告', '/news/community1.jpg', 156, 23, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 10)," +
                    "(1, '希望社区开展老年人健康体检活动', '为关爱老年人健康，希望社区将于2026年2月20日至2月25日开展免费健康体检活动。\\n\\n体检项目包括：\\n1. 血压、血糖测量\\n2. 心电图检查\\n3. B超检查\\n4. 血常规检查\\n\\n请65岁以上老年人携带身份证到社区服务中心登记报名。名额有限，先到先得！', '健康科普', '/news/community2.jpg', 89, 15, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 10)," +
                    "(1, '希望社区党员学习活动通知', '各位党员同志：\\n\\n根据上级党组织安排，定于2026年2月18日下午2点在社区会议室召开党员学习会议。\\n\\n请各位党员准时参加，如有特殊情况请提前请假。', '党建动态', '/news/community3.jpg', 67, 8, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR), 10)," +
                    "(1, '希望社区春季运动会报名开始', '亲爱的居民朋友们：\\n\\n希望社区春季运动会将于2026年3月15日举行，现开始报名！\\n\\n比赛项目：乒乓球、羽毛球、趣味运动会、广场舞比赛\\n\\n报名截止时间：2026年3月10日\\n报名地点：社区服务中心\\n\\n欢迎广大居民踊跃报名参加！', '活动通知', '/news/community4.jpg', 45, 12, 1, DATE_SUB(NOW(), INTERVAL 1 HOUR), 10)," +
                    "(2, '平安社区消防安全演练活动', '为提高居民消防安全意识，平安社区于2026年2月10日组织了消防安全演练活动。\\n\\n活动内容包括：消防知识讲座、灭火器使用演示、疏散逃生演练、消防器材展示\\n\\n共有100余名居民参与了此次活动，大家纷纷表示受益匪浅。', '社区公告', '/news/community5.jpg', 134, 28, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 11)," +
                    "(2, '平安社区老年人智能手机培训班开班', '为帮助社区老年人适应数字化生活，平安社区将于2026年2月22日起开设智能手机培训班。\\n\\n培训内容：手机基本操作、微信使用技巧、手机支付安全、健康码使用\\n\\n培训时间：每周二、四下午2:00-4:00\\n培训地点：社区活动中心\\n\\n欢迎60岁以上老年人报名参加！', '活动通知', '/news/community6.jpg', 78, 19, 1, DATE_SUB(NOW(), INTERVAL 5 HOUR), 11)," +
                    "(2, '平安社区垃圾分类宣传活动', '亲爱的居民朋友们：\\n\\n为推进垃圾分类工作，平安社区将于本周六开展垃圾分类宣传活动。\\n\\n活动时间：2026年2月17日上午9:00-11:00\\n活动地点：社区广场\\n\\n参与活动可获得精美礼品一份，欢迎大家踊跃参加！', '社区公告', '/news/community7.jpg', 56, 14, 1, DATE_SUB(NOW(), INTERVAL 2 HOUR), 11)";
                
                jdbcTemplate.update(insertNews);
                System.out.println("社区新闻测试数据已初始化完成！");
            } else {
                System.out.println("社区新闻数据已存在，跳过初始化");
            }

            Integer userCommunityCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_community WHERE user_id = 1 AND status = 1", Integer.class);
            System.out.println("用户1的社区关联数量(status=1): " + userCommunityCount);
            
            if (userCommunityCount != null && userCommunityCount == 0) {
                Integer existingCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_community WHERE user_id = 1", Integer.class);
                System.out.println("用户1的所有社区关联数量: " + existingCount);
                
                if (existingCount != null && existingCount > 0) {
                    jdbcTemplate.update(
                        "UPDATE user_community SET status = 1, move_out_time = NULL WHERE user_id = 1 ORDER BY move_in_time DESC LIMIT 1");
                    System.out.println("已将用户1的最新社区关联状态更新为居住中");
                } else {
                    jdbcTemplate.update(
                        "INSERT INTO user_community (user_id, community_id, status, move_in_time) VALUES (1, 1, 1, NOW())");
                    System.out.println("已为用户1添加社区关联（希望社区）");
                }
            }
        } catch (Exception e) {
            System.out.println("初始化社区新闻数据时出错: " + e.getMessage());
        }
    }
}
