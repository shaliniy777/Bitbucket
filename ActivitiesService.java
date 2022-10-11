/*
 * Copyright (c) Experian, 2021. All rights reserved.
 */


package com.experian.eda.casemanagement.service;

import com.experian.eda.casemanagement.channel.ChannelsUtil;
import com.experian.eda.casemanagement.channel.bps.config.FilterConfigManager;
import com.experian.eda.casemanagement.channel.bps.v0.BpsClient;
import com.experian.eda.casemanagement.channel.bps.v0.model.BpsGetNotesDataResponse;
import com.experian.eda.casemanagement.channel.bps.v0.model.BpsHistoryDataResponse;
import com.experian.eda.casemanagement.channel.bps.v0.model.BundleType;
import com.experian.eda.casemanagement.channel.bps.v0.model.HistoryDataDto;
import com.experian.eda.casemanagement.channel.bps.v0.model.HistoryDataUtil;
import com.experian.eda.casemanagement.channel.bps.v0.model.document.DocumentDataBpsResponse;
import com.experian.eda.casemanagement.generated.model.Activity;
import com.experian.eda.casemanagement.generated.model.ActivityValue;
import com.experian.eda.casemanagement.generated.model.CaseviewActivitiesResponse;
import com.experian.eda.casemanagement.generated.model.CaseviewActivityModel;
import com.experian.eda.casemanagement.generated.model.CommentWithAttachmentsData;
import com.experian.eda.casemanagement.service.mapper.activities.ActivitiesMapper;
import com.experian.saas.service.wrapper.core.rest.RestResponse;
import io.micrometer.core.annotation.Timed;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * The service that for single log activities
 */
@Service
@CustomLog
@RequiredArgsConstructor
public class ActivitiesService {

    private final BpsClient bpsClient;

    private final ActivitiesMapper activitiesMapper;

    private final CommentActivitiesService commentActivitiesService;

    private final FilterConfigManager filterConfigManager;

    private static final String NEW = BundleType.NEW.toString();
    private static final String SEARCH_AND_UPDATE = BundleType.SEARCH_AND_UPDATE.toString();
    private static final String ROBOT_SEARCH_AND_UPDATE = BundleType.ROBOT_SEARCH_AND_UPDATE.toString();
    private static final List<String> allowedTypes = Collections.unmodifiableList(Arrays.asList(NEW, SEARCH_AND_UPDATE, ROBOT_SEARCH_AND_UPDATE));

    /**
     * Get single log activities service method
     *
     * @param caseviewId         application id
     * @param updateBpsServiceId bps service id which perform update operation
     * @return CaseviewActivitiesResponse object
     */
    @Timed("e1.ActivitiesService.getSingleLogActivitiesforCase")
    public CaseviewActivitiesResponse getSingleLogActivitiesforCase(String caseviewId, String updateBpsServiceId) {
        CompletableFuture<RestResponse<DocumentDataBpsResponse>> bpsGetDocumentsDataResponse
                = bpsClient.executeGetDocumentAsync(caseviewId, "");
        CompletableFuture<RestResponse<BpsGetNotesDataResponse>> bpsGetNotesDataResponse
                = bpsClient.executeGetNotesAsync(caseviewId);
        CompletableFuture<RestResponse<BpsHistoryDataResponse>> bpsHistoryDataResponse
                = bpsClient.executeGetHistoryAsync(caseviewId);

        List<CommentWithAttachmentsData> comments
                = commentActivitiesService.convertToComment(ChannelsUtil.getCompletedResultFuture(bpsGetNotesDataResponse).body());

        List<Activity> activities = convertToActivity(ChannelsUtil.getCompletedResultFuture(bpsHistoryDataResponse).body().getHistory(),
                updateBpsServiceId);

        List<CaseviewActivityModel> combinedList = new ArrayList<>();

        List<CaseviewActivityModel> caseViewListForDocument = activitiesMapper.documentToCaseViewActivityModels(ChannelsUtil.getCompletedResultFuture(bpsGetDocumentsDataResponse).body().getData());
        List<CaseviewActivityModel> caseViewListForComment = activitiesMapper.commentToCaseViewActivityModels(comments);
        List<CaseviewActivityModel> caseViewListForActivity = activitiesMapper.historyToCaseviewActivityModels(activities);

        if(!CollectionUtils.isEmpty(caseViewListForDocument)) {
            combinedList.addAll(caseViewListForDocument);
        }
        if(!CollectionUtils.isEmpty(caseViewListForComment)) {
            combinedList.addAll(caseViewListForComment);
        }
        if(!CollectionUtils.isEmpty(caseViewListForActivity)) {
            combinedList.addAll(caseViewListForActivity);
        }
        combinedList.sort(Comparator.comparing(CaseviewActivityModel::getDateTime).reversed());

        return new CaseviewActivitiesResponse(combinedList);
    }

    private List<Activity> convertToActivity(List<HistoryDataDto> history, String updateBpsServiceId) {
        List<Activity> activities;
        if (history != null && !history.isEmpty()) {
            List<HistoryDataDto> historySanitised = HistoryDataUtil.sanitiseHistory(history);
            activities = historySanitised.stream()
                    // If the BPS audit entry is "SEARCH" or "SEARCHPAGE", we are not interested because our audits
                    // should be made by "NEW", "SEARCH AND UPDATE" and "ROBOT SEARCH AND UPDATE" services only.
                    .filter(hd -> allowedTypes.contains(hd.getType()))
                    .map(hdd -> createActivity(hdd, updateBpsServiceId))
                    .collect(Collectors.toList());
        } else {
            activities = Collections.emptyList();
        }
        return activities;
    }

    private Activity createActivity(HistoryDataDto from, String updateBpsServiceId) {
        return new Activity(from.getUserId(),
                from.getCompleted(),
                convertActivityType(from, updateBpsServiceId),
                convertCharacteristics(from));
    }

    private Activity.TypeEnum convertActivityType(HistoryDataDto from, String updateBpsServiceId) {
        if (!from.getType().equals(NEW) && !filterConfigManager.isRegisteredServiceId(from.getServiceId())) {
            return Activity.TypeEnum.EXTERNAL_ACTIVITY;
        }
        Activity.TypeEnum activityType;
        if (from.getType().equals(NEW)) {
            activityType = Activity.TypeEnum.CREATE;
        } else {
            if (updateBpsServiceId.equals(from.getServiceId())) {
                activityType = Activity.TypeEnum.UPDATE;
            } else {
                activityType = Activity.TypeEnum.READ;
            }
        }
        return activityType;
    }

    private static List<ActivityValue> convertCharacteristics(HistoryDataDto from) {
        return from.getCharacteristics()
                .entrySet()
                .stream()
                .filter(entry -> !Objects.equals(entry.getValue().getCurrentValue(), entry.getValue().getPreviousValue()))
                .map(entry -> new ActivityValue(entry.getKey(),
                        ServicesUtil.toStrongTypedCharacteristics(entry.getValue().getCurrentValue(), entry.getValue().getDataType()),
                        ServicesUtil.toStrongTypedCharacteristics(entry.getValue().getPreviousValue(), entry.getValue().getDataType()))
                )
                .collect(Collectors.toList());
    }

}
