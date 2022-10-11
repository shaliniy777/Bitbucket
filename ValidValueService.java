/*
 * Copyright (c) Experian, 2022. All rights reserved.
 */

package com.experian.eda.casemanagement.service;

import com.experian.eda.casemanagement.channel.bps.v0.BpsClient;
import com.experian.eda.casemanagement.channel.bps.v0.model.validvalue.ValidValueBpsResponse;
import com.experian.eda.casemanagement.exception.BpsValidationException;
import com.experian.eda.casemanagement.generated.model.ValidValueResponse;
import com.experian.eda.casemanagement.service.mapper.validvalues.ValidValuesMapper;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service to handle valid values request to BPS.
 */
@CustomLog
@Service
@RequiredArgsConstructor
public class ValidValueService {

    private final BpsClient bpsClient;
    private final ValidValuesMapper mapper;

    /**
     * Retrieve the valid value from BPS
     *
     * @return valid value response
     */
    public ValidValueResponse getValidValues() {
        ValidValueBpsResponse bpsResponse = bpsClient.getValidValues();
        if (bpsResponse == null) {
            throw new BpsValidationException(200, "BPS return null response"); // NOI18N
        }
        return mapper.toValidValueResponse(bpsResponse);
    }

}
