/*
 * Copyright (c) Experian, 2021. All rights reserved.
 */

package com.experian.eda.casemanagement.v0;

import com.experian.eda.casemanagement.common.util.SecurityUtil;
import com.experian.eda.casemanagement.exception.ResourcePermissionException;
import com.experian.eda.casemanagement.generated.resource.ILocksResource;
import com.experian.eda.casemanagement.service.LocksService;
import com.experian.saas.codegen.utils.types.SmartBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.Response;

/**
 * Resource for interacting with BPS locks.
 */
@Component
@RequiredArgsConstructor
public class LocksResource implements ILocksResource {
    private final LocksService locksService;

    @Override
    public Response delete(String authorization, String xCorrelationId, String xExternalUser, String caseviewId, SmartBoolean force) {
        boolean isForce = force != null && force.getBool();
        if (isForce && !SecurityUtil.hasPermissions(SecurityUtil.Permission.RELEASE_LOCK)) {
            throw new ResourcePermissionException("No permission to force unlock."); // NOI18N
        }
        locksService.runUnlock(caseviewId, force != null && force.getBool(), xExternalUser);
        return Response.noContent().build();
    }
}
