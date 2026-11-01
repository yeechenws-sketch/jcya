package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.DraftReviewLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DraftReviewLogMapper {
    
    @Select("SELECT l.*, u.nickname as reviewer_name, e.nickname as elder_name " +
            "FROM draft_review_log l " +
            "LEFT JOIN users u ON l.reviewer_id = u.id " +
            "LEFT JOIN medicine_draft d ON l.draft_id = d.id " +
            "LEFT JOIN users e ON d.elder_user_id = e.id " +
            "WHERE l.draft_id = #{draftId} ORDER BY l.create_time DESC")
    List<DraftReviewLog> selectByDraftId(@Param("draftId") Long draftId);
    
    @Insert("INSERT INTO draft_review_log (draft_id, reviewer_id, reviewer_name, elder_id, elder_name, action, original_data, modified_data, review_note) " +
            "VALUES (#{draftId}, #{reviewerId}, #{reviewerName}, #{elderId}, #{elderName}, #{action}, #{originalData}, #{modifiedData}, #{reviewNote})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DraftReviewLog log);
    
    @Select("SELECT l.*, u.nickname as reviewer_name, e.nickname as elder_name " +
            "FROM draft_review_log l " +
            "LEFT JOIN users u ON l.reviewer_id = u.id " +
            "LEFT JOIN medicine_draft d ON l.draft_id = d.id " +
            "LEFT JOIN users e ON d.elder_user_id = e.id " +
            "WHERE l.reviewer_id = #{reviewerId} ORDER BY l.create_time DESC LIMIT #{limit}")
    List<DraftReviewLog> selectByReviewerId(@Param("reviewerId") Long reviewerId, @Param("limit") int limit);

    @Select("SELECT l.*, u.nickname as reviewer_name, e.nickname as elder_name " +
            "FROM draft_review_log l " +
            "LEFT JOIN users u ON l.reviewer_id = u.id " +
            "LEFT JOIN medicine_draft d ON l.draft_id = d.id " +
            "LEFT JOIN users e ON d.elder_user_id = e.id " +
            "WHERE l.elder_id = #{elderId} ORDER BY l.create_time DESC LIMIT #{limit}")
    List<DraftReviewLog> selectByElderId(@Param("elderId") Long elderId, @Param("limit") int limit);
}
