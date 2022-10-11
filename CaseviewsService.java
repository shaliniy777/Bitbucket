/*
 * Copyright (c) Experian, 2020. All rights reserved.
 */

package com.experian.eda.casemanagement.service;

import com.experian.eda.casemanagement.channel.ChannelsUtil;
import com.experian.eda.casemanagement.channel.bps.v0.BpsClient;
import com.experian.eda.casemanagement.channel.bps.v0.model.BpsLockResponse;
import com.experian.eda.casemanagement.channel.bps.v0.model.BpsTotalCountResponse;
import com.experian.eda.casemanagement.channel.bps.v0.model.CaseViewBpsResponse;
import com.experian.eda.casemanagement.channel.bps.v0.model.CaseViewListBpsResponse;
import com.experian.eda.casemanagement.common.util.SecurityUtil;
import com.experian.eda.casemanagement.exception.ErrorCode;
import com.experian.eda.casemanagement.exception.ResourceBadRequestException;
import com.experian.eda.casemanagement.exception.ResourceLockedException;
import com.experian.eda.casemanagement.exception.ResourceUnknownException;
import com.experian.eda.casemanagement.exception.ResponseStatus;
import com.experian.eda.casemanagement.generated.model.ActionType;
import com.experian.eda.casemanagement.generated.model.CaseViewListModel;
import com.experian.eda.casemanagement.generated.model.CaseViewListResponse;
import com.experian.eda.casemanagement.generated.model.CaseViewModel;
import com.experian.eda.casemanagement.generated.model.CaseViewResponse;
import com.experian.eda.casemanagement.service.mapper.caseviews.CaseViewMapper;
import com.experian.saas.service.wrapper.core.rest.RestResponse;
import lombok.AllArgsConstructor;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * The service that backs the resource for handling the business logic of the caseviews.
 */
@Service
@CustomLog
@RequiredArgsConstructor
public class CaseviewsService {

    /**
     * Contains the data related to the X-External-User header.
     */
    @AllArgsConstructor
    @Data
    public static class ExternalUserConfigDto {
        /** The name of the characteristic to add to the BPS payload if defined in the filter-config for the
         * filerId being processed - if not defined the Optional is empty. */
        private Optional<String> xExternalUserCharacteristicOpt;
        /** The value of the X-External-User header parameter. */
        private String xExternalUserValue;
    }

    private final BpsClient bpsClient;
    private final CaseViewMapper caseViewsMapper;

    /**
     * Given the service id, runs the BPS service with the supplied data and returns the response data in
     * the format defined in the case-management-svc API.
     *
     * @param serviceId           the BPS service id.
     * @param bpsBusinessKey      The business key for the BPS use-case being accessed.
     * @param caseviewId          the id of the case being accessed.
     * @param retainLock          retainLock flag to pass for the BPS
     * @param dataElementsToPatch The data elements (characteristics) that need to be patched.
     * @param format              format to indicate is flat or hierachical json
     * @param externalUserConfigDto  X-External-User header value that contains external user id together with optional characteristic to inject it into.
     */
    public void runPatchCase(String serviceId, String bpsBusinessKey, String caseviewId, boolean retainLock, Map<String, Object> dataElementsToPatch, String format, ExternalUserConfigDto externalUserConfigDto) {
        String xExternalUser = externalUserConfigDto.getXExternalUserValue();
        throwIfLockNotRespected(Boolean.TRUE, caseviewId, Boolean.TRUE, xExternalUser);

        Map<String, Object> bpsData = new HashMap<>(dataElementsToPatch);
        CaseViewBpsResponse bpsResponse;
        // TOBEDONE - return value for patch
        switch (format) {
            case ChannelsUtil.FORMAT_FLAT:
                bpsData.put(bpsBusinessKey, caseviewId);
                externalUserConfigDto.getXExternalUserCharacteristicOpt()
                        .ifPresent(characteristic -> bpsData.put(characteristic, xExternalUser));
                bpsResponse = bpsClient.executeUpdateService(serviceId, retainLock, bpsData, Boolean.TRUE, xExternalUser);
                ChannelsUtil.getFlatBusinessKeyOrThrow(bpsBusinessKey, bpsResponse.getData());
                break;
            case ChannelsUtil.FORMAT_HIERARCHICAL:
                ChannelsUtil.putIdToHierarchicalMap(bpsBusinessKey, caseviewId, bpsData);
                externalUserConfigDto.getXExternalUserCharacteristicOpt()
                        .ifPresent(characteristic -> ChannelsUtil.putIdToHierarchicalMap(characteristic, xExternalUser, bpsData));
                bpsResponse = bpsClient.executeUpdateService(serviceId, retainLock, bpsData, Boolean.FALSE, xExternalUser);
                ChannelsUtil.getHierarchicalBusinessKeyOrThrow(bpsBusinessKey, bpsResponse.getData());
                break;
            default:
                throw new ResourceBadRequestException(ChannelsUtil.EXPECTED_PARAM_MISMATCH, ChannelsUtil.FORMAT_FIELD_NAME, ChannelsUtil.EXPECTED_PARAM_MISMATCH);
        }
    }

    /**
     * Given the service id, runs the BPS service with the supplied data and returns the response data in
     * the format defined in the case-management-svc API.
     *
     * @param serviceId              the BPS service id.
     * @param bpsBusinessKey         The business key for the BPS use-case being accessed.
     * @param format                 format to indicate is flat or hierarchical json
     * @param actions                to indicate what action should be performed - search (or) count (or) search,count
     * @param searchCriteriaData     optional parameters based on which Search caseList is performed.
     * @param page               The page number parameter.
     * @param pageSize           The page size parameter.
     * @return the response data in the format defined in the case-management-svc API.
     */
    public CaseViewListResponse runGetMultipleCasesSearchV2(String serviceId, String bpsBusinessKey, String format, List<String> actions, Map<String, Object> searchCriteriaData, Integer page, Integer pageSize) {
        boolean isFlat;
        CompletableFuture<RestResponse<CaseViewListBpsResponse>> bpsSearchResponse = null;
        CompletableFuture<RestResponse<BpsTotalCountResponse>> bpsCountResponse = null;

        switch (format) {
            case ChannelsUtil.FORMAT_FLAT:
                isFlat = Boolean.TRUE;
                break;
            case ChannelsUtil.FORMAT_HIERARCHICAL:
                isFlat = Boolean.FALSE;
                break;
            default:
                throw new ResourceBadRequestException(ChannelsUtil.EXPECTED_PARAM_MISMATCH,
                    ChannelsUtil.FORMAT_FIELD_NAME,
                    ChannelsUtil.EXPECTED_PARAM_MISMATCH);
        }

        if (actions.contains(ActionType.SEARCH.toString())) {
            bpsSearchResponse = bpsClient.executeSearchServiceAsync(serviceId, searchCriteriaData, isFlat, page, pageSize);
        }
        if (actions.contains(ActionType.COUNT.toString())) {
            bpsCountResponse = bpsClient.executeGetTotalCountAsync(serviceId, searchCriteriaData, isFlat);
        }
        return getCaseViewListResponse(bpsBusinessKey, bpsSearchResponse, bpsCountResponse, isFlat, page, pageSize);
    }

    /**
     * Extracts data from the asynchronous REST responses and returns the response data in
     * the format defined in the case-management-svc API.
     *
     * @param bpsBusinessKey    The business key for the BPS use-case being accessed.
     * @param bpsSearchResponse RestRequest async bps search call response
     * @param bpsCountResponse  RestRequest async bps count call response
     * @param isFlat            Flag to indicate the format type is flat or hierarchical.
     * @return the response data in the format defined in the case-management-svc API.
     */
    private CaseViewListResponse getCaseViewListResponse(String bpsBusinessKey,
                                                         CompletableFuture<RestResponse<CaseViewListBpsResponse>> bpsSearchResponse,
                                                         CompletableFuture<RestResponse<BpsTotalCountResponse>> bpsCountResponse,
                                                         boolean isFlat,
                                                         Integer page,
                                                         Integer pageSize) {
        List<CaseViewModel> caseViewModelList;
        CaseViewListBpsResponse searchResponse;
        BpsTotalCountResponse countResponse;

        List<CaseViewListModel> caseViewListModelList = null;
        Integer caseListCount = null;
        Long totalCount = null;
        Long totalPageNumber = null;

        if (bpsSearchResponse != null) { // This will be true when action='search' or action='search,count'
            searchResponse = ChannelsUtil.getCompletedResultFuture(bpsSearchResponse).body();
            if (!CollectionUtils.isEmpty(searchResponse.getData())) {
                caseViewModelList = searchResponse.getData().stream()
                    .map(datum -> caseViewsMapper.toCaseViewModelStrongTyped(datum, isFlat ?
                        ChannelsUtil.getFlatBusinessKeyOrThrow(bpsBusinessKey, datum) :
                        ChannelsUtil.getHierarchicalBusinessKeyOrThrow(bpsBusinessKey, datum)))
                    .collect(Collectors.toList());
                caseViewListModelList = getLockStatus(caseViewModelList);
            } else {
                caseViewListModelList = Collections.emptyList();
            }
        }

        if (bpsCountResponse != null) { // This will be true when action='search,count' or action='count'
            countResponse = ChannelsUtil.getCompletedResultFuture(bpsCountResponse).body();
            totalCount = countResponse != null ? countResponse.getTotalCount() : null;

            // caseViewListModelList will not be null only if action='search,count', then set caseListCount based on caseViewListModelList size.
            // That means, caseListCount will have a valid value only when action='search,count'.
            caseListCount = caseViewListModelList != null ? caseViewListModelList.size() : null;
        }
        return caseViewsMapper.toCaseViewListResponse(caseViewListModelList, caseListCount, totalCount, page, pageSize, totalPageNumber);
    }

    /**
     * Get the lock status of each caseviews
     *
     * @param caseViewModelList Caseviews list retrieved
     * @return caseViewModelList with lock status
     */
    private List<CaseViewListModel> getLockStatus(List<CaseViewModel> caseViewModelList) {
        List<CaseViewListModel> newCaseViewModelList = null;

        if (!CollectionUtils.isEmpty(caseViewModelList)) {
            Set<String> businessKeySet = caseViewModelList.stream()
                    .map(CaseViewModel::getCaseviewId)
                    .collect(Collectors.toSet());
            Map<String, BpsLockResponse> lockMap = bpsClient.executeGetLockStatus(businessKeySet);

           newCaseViewModelList = caseViewModelList.stream()
                    .map(caseViewModel -> caseViewsMapper.toCaseViewModelWithLock(
                            lockMap.get(caseViewModel.getCaseviewId()),
                            caseViewModel))
                    .collect(Collectors.toList());
        }

        return newCaseViewModelList;
    }

    /**
     * Given the service id, runs the BPS SEARCHANDUPDATE service and returns the response data in the format defined in the
     * case-management-svc API.
     *
     * @param serviceId      the BPS service id.
     * @param bpsBusinessKey The business key for the BPS use-case being accessed.
     * @param retainLock     retainLock flag to pass for the BPS
     * @param caseviewId     the id of the case being accessed.
     * @param format         format to indicate is flat or hierachical json
     * @param externalUserConfigDto  X-External-User header value that contains external user id together with optional characteristic to inject it into.
     * @return the response data in the format defined in the case-management-svc API.
     */
    public CaseViewResponse runGetSingleCaseUpdate(String serviceId, String bpsBusinessKey, boolean retainLock, String caseviewId, String format, ExternalUserConfigDto externalUserConfigDto) {
        String xExternalUser = externalUserConfigDto.getXExternalUserValue();
        throwIfLockNotRespected(Boolean.TRUE, caseviewId, Boolean.FALSE, xExternalUser);

        CaseViewBpsResponse bpsResponse;
        switch (format) {
            case ChannelsUtil.FORMAT_FLAT:
                Map<String, Object> bpsDataFlat = new HashMap<>();
                bpsDataFlat.put(bpsBusinessKey, caseviewId);
                externalUserConfigDto.getXExternalUserCharacteristicOpt()
                        .ifPresent(characteristic -> bpsDataFlat.put(
                                characteristic, externalUserConfigDto.getXExternalUserValue()));
                bpsResponse = bpsClient.executeUpdateService(serviceId, retainLock, bpsDataFlat
                        , Boolean.TRUE, xExternalUser);
                return caseViewsMapper.toCaseViewResponse(bpsResponse, ChannelsUtil.getFlatBusinessKeyOrThrow(bpsBusinessKey, bpsResponse.getData()));
            case ChannelsUtil.FORMAT_HIERARCHICAL:
                Map<String, Object> bpsDataHier = ChannelsUtil.getHierarchicalBusinessKeyValue(bpsBusinessKey, caseviewId);
                externalUserConfigDto.getXExternalUserCharacteristicOpt()
                        .ifPresent(characteristic -> ChannelsUtil.putIdToHierarchicalMap(
                                characteristic, externalUserConfigDto.getXExternalUserValue(), bpsDataHier));
                bpsResponse = bpsClient.executeUpdateService(serviceId, retainLock, bpsDataHier, Boolean.FALSE, xExternalUser);
                return caseViewsMapper.toCaseViewResponse(bpsResponse, ChannelsUtil.getHierarchicalBusinessKeyOrThrow(bpsBusinessKey, bpsResponse.getData()));
            default:
                throw new ResourceBadRequestException(ChannelsUtil.EXPECTED_PARAM_MISMATCH,
                        ChannelsUtil.FORMAT_FIELD_NAME,
                        ChannelsUtil.EXPECTED_PARAM_MISMATCH);
        }
    }

    /**
     * Given the service id, runs the BPS SEARCH service and returns the response data in the format defined in the
     * case-management-svc API.
     *
     * @param serviceId      the BPS service id.
     * @param bpsBusinessKey The business key for the BPS use-case being accessed.
     * @param respectLock    When true, lock will be checked before use-case being accessed.
     * @param caseviewId     the id of the case being accessed.
     * @param format         format to indicate is flat or hierachical json
     * @param xExternalUser  X-External-User header that contains external user id.
     * @return the response data in the format defined in the case-management-svc API.
     * @throws ResourceUnknownException if BPS does not return the expected businessKey
     * @throws ResourceLockedException  if respectLock=true and BPS locked the case
     */
    public CaseViewResponse runGetSingleCaseSearch(String serviceId, String bpsBusinessKey, boolean respectLock, String caseviewId, String format, String xExternalUser) {
        throwIfLockNotRespected(respectLock, caseviewId, Boolean.FALSE, xExternalUser);

        CaseViewListBpsResponse bpsResponse;
        List<CaseViewModel> caseViewModelList = null;
        switch (format) {
            case ChannelsUtil.FORMAT_FLAT:
                bpsResponse = bpsClient.executeSearchService(serviceId,
                        Collections.singletonMap(bpsBusinessKey, caseviewId), Boolean.TRUE, xExternalUser);
                if (!CollectionUtils.isEmpty(bpsResponse.getData())) {
                    caseViewModelList = bpsResponse.getData().stream().map(datum ->
                                    caseViewsMapper.toCaseViewModelStrongTyped(datum,
                                            ChannelsUtil.getFlatBusinessKeyOrThrow(bpsBusinessKey, datum)))
                            .collect(Collectors.toList());
                }
                break;
            case ChannelsUtil.FORMAT_HIERARCHICAL:
                bpsResponse = bpsClient.executeSearchService(serviceId,
                        ChannelsUtil.getHierarchicalBusinessKeyValue(bpsBusinessKey, caseviewId), Boolean.FALSE, xExternalUser);
                if (!CollectionUtils.isEmpty(bpsResponse.getData())) {
                    caseViewModelList = bpsResponse.getData().stream().map(datum ->
                                    caseViewsMapper.toCaseViewModelStrongTyped(datum,
                                            ChannelsUtil.getHierarchicalBusinessKeyOrThrow(bpsBusinessKey, datum)))
                            .collect(Collectors.toList());
                }
                break;
            default:
                throw new ResourceBadRequestException(ChannelsUtil.EXPECTED_PARAM_MISMATCH,
                        ChannelsUtil.FORMAT_FIELD_NAME,
                        ChannelsUtil.EXPECTED_PARAM_MISMATCH);
        }

        // TOBEDONE - To check if this first retrieval is the best way,
        //            high chance that the element is not at the first index but exist at other index
        if (!CollectionUtils.isEmpty(caseViewModelList)) {
            CaseViewModel firstModel = caseViewModelList.get(0);
            // We expect just one entry that will have the bpsBusinessKey=caseviewId of interest.
            // If not tolerate the data if the first entry satisfies bpsBusinessKey=caseviewId.
            if (Objects.equals(firstModel.getCaseviewId(), caseviewId)) {
                if (caseViewModelList.size() > 1) {
                    LOGGER.warn("Additional BPS data elements being ignored"); // NOI18N
                }
                return caseViewsMapper.toCaseViewResponse(firstModel);
            } else {
                LOGGER.warn("First BPS data element not the expected caseviewId of {}, got {}. Returning empty.",  // NOI18N
                        caseviewId, firstModel.getCaseviewId());
            }
        }
        return caseViewsMapper.toCaseViewResponse(null);
    }

    private void throwIfLockNotRespected(boolean respectLock, String caseviewId, boolean isUpdate, String xExternalUser) {
        if (respectLock) {
            // format the xExternalUser
            String userId;
            if (ObjectUtils.isEmpty(xExternalUser)) {
                userId = SecurityUtil.getUserId();
            } else {
                userId = String.format("%s/%s", xExternalUser, SecurityUtil.getUserId());
            }
            BpsLockResponse bpsLockResponse = bpsClient.executeGetLock(caseviewId);
            if (bpsLockResponse.getBusinessKey() != null && !Objects.equals(caseviewId, bpsLockResponse.getBusinessKey())) {
                throw new ResourceUnknownException(ErrorCode.INVALID_LOCKED_RESOURCE,
                        String.format("Unexpected resource from BPS. Expecting lock status for caseViewId=[%s], but received [%s] instead", // NOI18N
                                caseviewId, bpsLockResponse.getBusinessKey()));
            }
            if (isUpdate && bpsLockResponse.getUserId() == null) {
                throw new ResourceLockedException(
                        String.format("Resource [%s] is not locked by current user", caseviewId), // NOI18N
                        ErrorCode.NOT_LOCKED, ResponseStatus.NOT_LOCKED_ROW_ERROR);
            }
            if (bpsLockResponse.getUserId() != null && !Objects.equals(bpsLockResponse.getUserId(), userId)) {
                throw new ResourceLockedException(
                        String.format("Resource [%s] is being locked by other user", bpsLockResponse.getBusinessKey()), // NOI18N
                        bpsLockResponse.getUserId(), bpsLockResponse.getTimestamp());
            }
        }
    }
}