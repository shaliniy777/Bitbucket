/*
 * Copyright (c) Experian, 2021. All rights reserved.
 */


package com.experian.eda.casemanagement.service;

import com.experian.eda.casemanagement.channel.bps.v0.BpsClient;
import com.experian.eda.casemanagement.channel.bps.v0.model.BpsAttachmentContentDto;
import com.experian.eda.casemanagement.channel.bps.v0.model.BpsDocumentContentDto;
import com.experian.eda.casemanagement.channel.bps.v0.model.Response;
import com.experian.eda.casemanagement.generated.model.DocumentContentResponse;
import com.experian.eda.casemanagement.service.mapper.documents.DocumentMapper;
import com.experian.saas.service.wrapper.core.rest.RestResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service to handle retrieve the document content based on activity type
 */
@Service
@RequiredArgsConstructor
public class DocumentsService {

    private final BpsClient bpsClient;

    private final DocumentMapper mapper;

    /**
     * To get the document content based on document key
     * @param documentKey document key
     * @param documentType document type
     * @return DocumentContentResponse object
     */
    public DocumentContentResponse getDocumentContent(String documentKey, String documentType) {
        RestResponse<Response<BpsDocumentContentDto>> result = bpsClient.getDocumentContent(documentKey);
        return mapper.toDocumentContentResponse(result.body(), documentType, result.getHeaders());
    }

    /**
     * To get the attachment content based on commentId and attachmentId
     * @param commentId the comment id
     * @param attachmentId attachment id
     * @return DocumentContentResponse
     */
    public DocumentContentResponse getNoteAttachmentContent(@NonNull String commentId, @NonNull String attachmentId) {
        Response<BpsAttachmentContentDto> result = bpsClient.getAttachmentContent(commentId, attachmentId);
        return mapper.toDocumentContentResponse(result);
    }
}
