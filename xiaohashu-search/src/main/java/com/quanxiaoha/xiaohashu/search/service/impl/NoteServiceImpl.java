package com.quanxiaoha.xiaohashu.search.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.quanxiaoha.framework.common.constant.DateConstants;
import com.quanxiaoha.framework.common.response.PageResponse;
import com.quanxiaoha.framework.common.util.DateUtils;
import com.quanxiaoha.framework.common.util.NumberUtils;
import com.quanxiaoha.xiaohashu.search.enums.NotePublishTimeRangeEnum;
import com.quanxiaoha.xiaohashu.search.enums.NoteSortTypeEnum;
import com.quanxiaoha.xiaohashu.search.index.NoteIndex;
import com.quanxiaoha.xiaohashu.search.model.vo.SearchNoteReqVO;
import com.quanxiaoha.xiaohashu.search.model.vo.SearchNoteRspVO;
import com.quanxiaoha.xiaohashu.search.service.NoteService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.functionscore.FieldValueFactorFunctionBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 笔记搜索业务
 */
@Service
@Slf4j
public class NoteServiceImpl implements NoteService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    /**
     * 搜索笔记
     */
    @Override
    public PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO) {
        // 查询关键词
        String keyword = searchNoteReqVO.getKeyword();
        // 查询页码
        Integer pageNo = searchNoteReqVO.getPageNo();
        // 获取笔记类型
        Integer type = searchNoteReqVO.getType();
        // 获取排序方式
        Integer sort = searchNoteReqVO.getSort();
        // 获取时间范围
        Integer publishTimeRange = searchNoteReqVO.getPublishTimeRange();

        // 构建 SearchRequest，指定索引
        SearchRequest searchRequest = new SearchRequest(NoteIndex.NAME);

        // 创建查询构建器
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 创建查询条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery().must(
                QueryBuilders.multiMatchQuery(keyword)
                        .field(NoteIndex.FIELD_NOTE_TITLE, 2.0f) // 手动设置笔记标题的权重值为 2.0
                        .field(NoteIndex.FIELD_NOTE_TOPIC) // 不设置，权重默认为 1.0
        );

        // 若勾选了笔记类型，添加过滤条件
        if (Objects.nonNull(type)) {
            boolQueryBuilder.filter(QueryBuilders.termQuery(NoteIndex.FIELD_NOTE_TYPE, type));
        }

        // 按发布时间范围过滤
        NotePublishTimeRangeEnum notePublishTimeRangeEnum = NotePublishTimeRangeEnum.valueOf(publishTimeRange);

        if (Objects.nonNull(notePublishTimeRangeEnum)) {
            // 结束时间
            String endTime = DateUtils.localDateTime2String(LocalDateTime.now());
            // 开始时间
            String startTime = null;
            switch (notePublishTimeRangeEnum) {
                case DAY -> startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusDays(1));   // 一天之前的时间
                case WEEK -> startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusWeeks(1));   // 一周之前的时间
                case HALF_YEAR ->
                        startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusMonths(6));   // 半年内之前的时间
            }
            // 添加发布时间范围过滤条件
            boolQueryBuilder.filter(QueryBuilders.rangeQuery(NoteIndex.FIELD_NOTE_CREATE_TIME)
                    .gte(startTime) // 大于等于开始时间
                    .lte(endTime)); // 小于等于结束时间
        }

        // 设置排序
        // "sort": [
        //     {
        //       "_score": {
        //         "order": "desc"
        //       }
        //     }
        //   ]
        NoteSortTypeEnum noteSortTypeEnum = NoteSortTypeEnum.valueOf(sort);
        if (Objects.nonNull(noteSortTypeEnum)) {
            switch (noteSortTypeEnum) {
                // 按笔记发布时间降序
                case LATEST ->
                        sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_CREATE_TIME).order(SortOrder.DESC));
                // 按点赞数降序
                case MOST_LIKE ->
                        sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_LIKE_TOTAL).order(SortOrder.DESC));
                // 按评论数降序
                case MOST_COMMENT ->
                        sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_COMMENT_TOTAL).order(SortOrder.DESC));
                // 按收藏数降序
                case MOST_COLLECT ->
                        sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_COLLECT_TOTAL).order(SortOrder.DESC));
            }
            // 设置查询
            sourceBuilder.query(boolQueryBuilder);
        } else {
            // 综合排序，自定义评分，并按 _score 评分降序
            sourceBuilder.sort(new FieldSortBuilder("_score").order(SortOrder.DESC));

            // 创建 FilterFunctionBuilder 数组
            // "functions": [
            //         {
            //           "field_value_factor": {
            //             "field": "like_total",
            //             "factor": 0.5,
            //             "modifier": "sqrt",
            //             "missing": 0
            //           }
            //         },
            //         {
            //           "field_value_factor": {
            //             "field": "collect_total",
            //             "factor": 0.3,
            //             "modifier": "sqrt",
            //             "missing": 0
            //           }
            //         },
            //         {
            //           "field_value_factor": {
            //             "field": "comment_total",
            //             "factor": 0.2,
            //             "modifier": "sqrt",
            //             "missing": 0
            //           }
            //         }
            //       ],
            FunctionScoreQueryBuilder.FilterFunctionBuilder[] filterFunctionBuilders = new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                    // function 1
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_LIKE_TOTAL)
                                    .factor(0.5f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)
                    ),
                    // function 2
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COLLECT_TOTAL)
                                    .factor(0.3f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)
                    ),
                    // function 3
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COMMENT_TOTAL)
                                    .factor(0.2f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)
                    )
            };

            // 构建 function_score 查询
            // "score_mode": "sum",
            // "boost_mode": "sum"
            FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(boolQueryBuilder,
                            filterFunctionBuilders)
                    .scoreMode(FunctionScoreQuery.ScoreMode.SUM) // score_mode 为 sum
                    .boostMode(CombineFunction.SUM); // boost_mode 为 sum

            // 设置查询
            sourceBuilder.query(functionScoreQueryBuilder);
        }

        // 设置分页，from 和 size
        int pageSize = 10; // 每页展示数据量
        int from = (pageNo - 1) * pageSize; // 偏移量
        sourceBuilder.from(from);
        sourceBuilder.size(pageSize);

        // 设置高亮字段
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field(NoteIndex.FIELD_NOTE_TITLE)
                .preTags("<strong>") // 设置包裹标签
                .postTags("</strong>");
        sourceBuilder.highlighter(highlightBuilder);

        // 将构建的查询条件设置到 SearchRequest 中
        searchRequest.source(sourceBuilder);

        // 返参 VO 集合
        List<SearchNoteRspVO> searchNoteRspVOS = null;
        // 总文档数，默认为 0
        long total = 0;
        try {
            log.info("==> SearchRequest: {}", searchRequest.source().toString());

            // 执行搜索
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            // 处理搜索结果
            total = searchResponse.getHits().getTotalHits().value;
            log.info("==> 命中文档总数, hits: {}", total);

            searchNoteRspVOS = new ArrayList<>();

            SearchHit[] hits = searchResponse.getHits().getHits();
            for (SearchHit hit : hits) {
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                Long noteId = (Long) sourceAsMap.get(NoteIndex.FIELD_NOTE_ID);
                String cover = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_COVER);
                String title = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_TITLE);
                String avatar = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_AVATAR);
                String nickname = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_NICKNAME);
                LocalDateTime updateTime = LocalDateTime.parse((String) sourceAsMap.get(NoteIndex.FIELD_NOTE_UPDATE_TIME), DateConstants.DATE_FORMAT_Y_M_D_H_M_S);

                // 对更新时间进行优化，eg: “10分钟前”、“3小时前”、 “昨天 20:12”、“2天前”
                String finalUpdateTime = null;
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

                if (updateTime.isAfter(startOfDay)) {   // 如果为同一天，需要判断是 1 小时前还是 1 小时以内
                    if (updateTime.isBefore(now.minusHours(1))) {   // 如果为 1 小时内前，格式化为 “1小时前”
                        // 时间相减，获取小时数
                        long hours = Duration.between(updateTime, now).toHours();
                        finalUpdateTime = hours + "小时前";
                    }
                }


                Integer likeTotal = (Integer) sourceAsMap.get(NoteIndex.FIELD_NOTE_LIKE_TOTAL);

                // 获取高亮字段
                String highlightTitle = null;
                if (CollUtil.isNotEmpty(hit.getHighlightFields())
                        && hit.getHighlightFields().containsKey(NoteIndex.FIELD_NOTE_TITLE)) {
                    highlightTitle = hit.getHighlightFields().get(NoteIndex.FIELD_NOTE_TITLE).fragments()[0].string();
                }

                SearchNoteRspVO searchNoteRspVO = SearchNoteRspVO.builder()
                        .noteId(noteId)
                        .cover(cover)
                        .title(title)
                        .highlightTitle(highlightTitle)
                        .avatar(avatar)
                        .nickname(nickname)
                        .updateTime(DateUtils.localDateTime2String(updateTime))
                        .likeTotal(NumberUtils.formatNumberString(likeTotal))
                        .build();
                searchNoteRspVOS.add(searchNoteRspVO);
            }
        } catch (Exception e) {
            log.error("==> 查询 Elasticserach 异常: ", e);
        }

        return PageResponse.success(searchNoteRspVOS, pageNo, total);
    }
}
