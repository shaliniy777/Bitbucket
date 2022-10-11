/*
 * Copyright (c) Experian, 2021. All rights reserved.
 */

package com.experian.eda.casemanagement.service;

import com.experian.eda.casemanagement.channel.bps.v0.BpsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service that will handle business logic of case lock
 */
@Service
@RequiredArgsConstructor
public class LocksService {
    private final BpsClient bpsClient;

    /**
     * Try to unlock case based on the bpsBusinessKey
     *
     * @param bpsBusinessKey BPS Case ID
     * @param force          Flag to indicate force unlock, when true it will be able to unlock another user locked case.
     * @param xExternalUser  the real user id from external
     */
    public void runUnlock(String bpsBusinessKey, boolean force, String xExternalUser) {
        bpsClient.executeUnlock(bpsBusinessKey, force, xExternalUser);
    }
}