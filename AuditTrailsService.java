/*
 * Copyright (c) Experian, 2021. All rights reserved.
 */

package com.experian.eda.casemanagement.service;

import com.experian.eda.casemanagement.channel.ChannelsUtil;
import com.experian.eda.casemanagement.channel.bps.v0.BpsClient;
import com.experian.eda.casemanagement.generated.model.AuditTrailDetailsResponse;
import com.experian.eda.casemanagement.generated.model.AuditTrailsResponse;
import com.experian.eda.casemanagement.service.mapper.audittrails.AuditTrailsMapper;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The service to handle audit trails
 */
@Service
@CustomLog
@RequiredArgsConstructor
public class AuditTrailsService {

    private final BpsClient bpsClient;
    private final AuditTrailsMapper auditTrailsMapper;

    /**
     * Get audit trails service method
     *
     * @param caseviewId application id
     * @return a {@link com.experian.eda.casemanagement.generated.model.AuditTrailsResponse} object
     */
    public AuditTrailsResponse getAuditTrails(String caseviewId) {
        return auditTrailsMapper.toAuditTrailsResponse(
                ChannelsUtil.getCompletedResultFuture(
                        bpsClient.executeGetHistoryAsync(caseviewId)
                ).body()
        );
    }

    /**
     * Get audit trail characteristics list service method
     *
     * @param historyId history id during persist
     * @return a {@link com.experian.eda.casemanagement.generated.model.AuditTrailDetailsResponse} object
     */
    public AuditTrailDetailsResponse getAuditTrailDetails(String historyId) {
        return auditTrailsMapper.toAuditTrailDetailsResponse(
                bpsClient.executeGetHistoryDetails(historyId).body().getData()
        );
    }
}
