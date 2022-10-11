/*
 * Copyright (c) Experian, 2021. All rights reserved.
 */

package com.experian.eda.casemanagement.service;

import com.experian.eda.casemanagement.channel.bps.config.FilterConfigManager;
import com.experian.eda.casemanagement.channel.bps.v0.BpsClient;
import com.experian.eda.casemanagement.channel.bps.v0.model.datadefinition.CharacteristicMetaDto;
import com.experian.eda.casemanagement.channel.bps.v0.model.datadefinition.DataDefinitionDto;
import com.experian.eda.casemanagement.channel.bps.v0.model.datadefinition.UsecaseServiceDataDefinitionBpsResponse;
import com.experian.eda.casemanagement.channel.token.v0.InternalTokenClient;
import com.experian.eda.casemanagement.generated.model.UsecaseServiceDataDefinitionResponse;
import com.experian.eda.casemanagement.service.mapper.datadefinition.UsecaseServiceDataDefinitionModelMapper;
import com.experian.saas.service.wrapper.core.rest.RestResponse;
import lombok.CustomLog;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.experian.eda.casemanagement.channel.ChannelsUtil.getCompletedResultFuture;

/**
 * Service to handle Usecase services meta data related request to BPS.
 */
@CustomLog
@Service
@RequiredArgsConstructor
public class DataDefinitionsService {
    private final BpsClient bpsClient;
    private final UsecaseServiceDataDefinitionModelMapper mapper;
    private final FilterConfigManager filterConfigManager;
    private final InternalTokenClient internalTokenClient;

    @Setter
    @Value("${case-management.bps-default-datetime-format}")
    private String bpsDefaultDateTimeFormat;

    private static final String DATA_DEF_CACHE_NAME = "DataDefinitionsService.mergedCharacteristicMetaData"; //NOI18N

    /**
     * Retrieve usecase service (input/output) data definition characteristic meta data.
     *
     * @param serviceId usecase serviceId, ex: cases-summary.
     * @return (input / output) data definition containing characteristic meta data.
     */
    public UsecaseServiceDataDefinitionResponse getUsecaseServiceDataDefinitions(String serviceId) {
        UsecaseServiceDataDefinitionBpsResponse bpsResponse = bpsClient.getUsecaseDataDefinition(serviceId);
        return mapper.toCmResponse(bpsResponse);
    }

    /**
     * Clean the merged data definition cache and repopulate it by calling to BPS endpoints
     *
     * @return refreshed merged data definition
     */
    @CacheEvict(value = DATA_DEF_CACHE_NAME, allEntries = true)
    public MergedCharacteristicMetaData getRepopulatedMergedFlatDataDefinitions() {
        LOGGER.info("Cleaning {} cache.", DATA_DEF_CACHE_NAME);
        return getMergedFlatDataDefinitions();
    }

    /**
     * Retrieve input and output data definition based on service id that configured at filter config,
     * then transform it into characteristic meta data with tree format.
     * This method will return the characteristic key in flat format.
     * All the '[index]' will be trimmed from the key, removing all the duplicate data under a key with array index
     *
     * @return Merged IDD & ODD from all allowed BPS service id configured at filter config
     */
    @Cacheable(value = DATA_DEF_CACHE_NAME, unless = "#result == null")
    public MergedCharacteristicMetaData getMergedFlatDataDefinitions() {
        LOGGER.info("Calling BPS data definition endpoint and saving to {} cache.", DATA_DEF_CACHE_NAME);
        String auth = internalTokenClient.getJWTFromInternalTokenService();
        if (null != auth) {
            // Create parallel future calls to BPS data-definitions endpoint asynchronously using all allowed BPS service id
            List<CompletableFuture<RestResponse<UsecaseServiceDataDefinitionBpsResponse>>> allFutures =
                filterConfigManager.getAllowedFilterDefinitions().stream()
                    .map(filterDefinition -> bpsClient.getUsecaseDataDefinitionAsync(filterDefinition.getBpsServiceId(), auth))
                    .collect(Collectors.toList());
            // Wait for all call to be completed, and apply merging when all complete
            CompletableFuture<MergedCharacteristicMetaData> mergedDataDefinitionFuture = CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0]))
                .thenApply(aVoid -> {
                    List<UsecaseServiceDataDefinitionBpsResponse> responses = allFutures.stream()
                        .map(future -> getCompletedResultFuture(future).body())
                        .collect(Collectors.toList());

                    // merge all the BPS Response IDD and ODD into one map of characteristic types
                    Map<String, CharacteristicMetaDto.CharacteristicType> dataDefinitions = responses.stream()
                        .flatMap(response -> response.getData() == null ? Stream.empty()
                            : response.getData().getStreamOfMergedCharacteristicType())
                        .collect(Collectors.toMap(
                            e -> DataDefinitionDto.ARRAY_INDEX_PATTERN.matcher(e.getKey()).replaceAll(""),
                            Map.Entry::getValue,
                            (existingValue, newValue) -> existingValue)); // On duplicate use the existingValue
                    return new MergedCharacteristicMetaData(dataDefinitions, retrieveDateFormat(responses));
                });
            // retrieve the future completed value, throw the CompletionException cause if occur
            return getCompletedResultFuture(mergedDataDefinitionFuture);
        }
        return null;
    }

    private String retrieveDateFormat(List<UsecaseServiceDataDefinitionBpsResponse> dataDefinitionBpsResponses) {
        Optional<UsecaseServiceDataDefinitionBpsResponse> anyBpsResponse = dataDefinitionBpsResponses.stream()
                .filter(response -> response.getData() != null).findAny();
        String dateFormat = bpsDefaultDateTimeFormat;
        boolean responseHasDateFormat = anyBpsResponse.isPresent() && StringUtils.hasLength(anyBpsResponse.get().getData().getDateFormat());
        if (responseHasDateFormat) {
            dateFormat = anyBpsResponse.get().getData().getDateFormat();
        } else {
            LOGGER.warn("BPS Does not return date time format, {} used as default. This might be caused by BPS version does not support the endpoint or does not return the date time format yet.", bpsDefaultDateTimeFormat); //NOI18N
        }
        return dateFormat;
    }

    /**
     * Merged characteristic meta data wrapper class
     */
    @Getter
    @EqualsAndHashCode
    @RequiredArgsConstructor
    public static class MergedCharacteristicMetaData {
        private final Map<String, CharacteristicMetaDto.CharacteristicType> dataDefinitions;
        private final String dateFormat;
    }

}
