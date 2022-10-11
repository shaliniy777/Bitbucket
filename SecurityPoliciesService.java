/*
 * Copyright (c) Experian, 2021. All rights reserved.
 */

package com.experian.eda.casemanagement.service;

import com.experian.eda.casemanagement.channel.bps.v0.BpsClient;
import com.experian.eda.casemanagement.channel.bps.v0.model.Response;
import com.experian.eda.casemanagement.channel.bps.v0.model.securitypolicy.BpsSecurityPolicyDto;
import com.experian.eda.casemanagement.generated.model.SecurityPoliciesResponse;
import com.experian.eda.casemanagement.service.mapper.securitypolicies.SecurityPoliciesMapper;
import com.experian.saas.service.wrapper.core.rest.RestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service that will handle the logic of get security policies from BPS
 */
@Service
@RequiredArgsConstructor
public class SecurityPoliciesService {

    private final BpsClient bpsClient;
    private final SecurityPoliciesMapper mapper;

    /**
     * Retrieve the security policies from BPS
     *
     * @return security policies response
     */
    public SecurityPoliciesResponse getSecurityPolicies() {
        RestResponse<Response<BpsSecurityPolicyDto>> bpsResponse = bpsClient.getSecurityPolicies();

        return mapper.toSecurityPoliciesResponse(bpsResponse.body());
    }
}
