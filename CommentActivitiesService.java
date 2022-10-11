/*
 * Copyright (c) Experian, 2021. All rights reserved.
 */

package com.experian.eda.casemanagement.service;

import com.experian.eda.casemanagement.channel.antivirus.v0.AVScanResult;
import com.experian.eda.casemanagement.channel.antivirus.v0.AntivirusClient;
import com.experian.eda.casemanagement.channel.bps.v0.BpsClient;
import com.experian.eda.casemanagement.channel.bps.v0.model.BpsAttachmentDataDto;
import com.experian.eda.casemanagement.channel.bps.v0.model.BpsGetNotesDataResponse;
import com.experian.eda.casemanagement.channel.bps.v0.model.BpsInvalidAttachmentDataDto;
import com.experian.eda.casemanagement.channel.bps.v0.model.BpsNoteDataDto;
import com.experian.eda.casemanagement.channel.bps.v0.model.BpsPostNoteResponse;
import com.experian.eda.casemanagement.common.util.SecurityUtil;
import com.experian.eda.casemanagement.generated.model.AttachmentMeta;
import com.experian.eda.casemanagement.generated.model.CommentWithAttachmentsData;
import com.experian.eda.casemanagement.generated.model.CreatedCommentData;
import com.experian.eda.casemanagement.generated.model.InvalidAttachmentMeta;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.experian.eda.casemanagement.channel.antivirus.v0.AVScanResult.ScanResult.SUCCESS;
import static com.experian.eda.casemanagement.generated.model.InvalidAttachmentMeta.CodeEnum.BAD_DATA;
import static com.experian.eda.casemanagement.generated.model.InvalidAttachmentMeta.CodeEnum.EXCEEDED_FILES_PER_MINUTE;
import static com.experian.eda.casemanagement.generated.model.InvalidAttachmentMeta.CodeEnum.FILE_FAILED_VIRUS_CHECK;
import static com.experian.eda.casemanagement.generated.model.InvalidAttachmentMeta.CodeEnum.FILE_TOO_LARGE;
import static com.experian.eda.casemanagement.generated.model.InvalidAttachmentMeta.CodeEnum.FILE_VIRUS_CHECK_INCOMPLETE;
import static com.experian.eda.casemanagement.generated.model.InvalidAttachmentMeta.CodeEnum.FILE_VIRUS_CHECK_UNAVAILABLE;
import static com.experian.eda.casemanagement.generated.model.InvalidAttachmentMeta.CodeEnum.MISMATCHED_TYPE;
import static com.experian.eda.casemanagement.generated.model.InvalidAttachmentMeta.CodeEnum.NO_ATTACH_PERMISSION;
import static com.experian.eda.casemanagement.generated.model.InvalidAttachmentMeta.CodeEnum.SYSTEM_ERROR;
import static com.experian.eda.casemanagement.generated.model.InvalidAttachmentMeta.CodeEnum.UNSUPPORTED_FILE_TYPE;

/**
 * The service that back the logic to handle comment (notes) activities
 */
@Service
@CustomLog
@RequiredArgsConstructor
public class CommentActivitiesService {

    private static final String FILE_FAILED_VIRUS_CHECK_DESCRIPTION = "The file failed the virus check."; //NOI18N
    private static final String FILE_VIRUS_CHECK_INCOMPLETE_DESCRIPTION = "The file virus check is not completed."; //NOI18N
    private static final String FILE_VIRUS_CHECK_UNAVAILABLE_DESCRIPTION = "The file virus check is not available."; //NOI18N
    private static final String NO_ATTACH_PERMISSION_DESCRIPTION = "The file not persisted due to user does not have add attachment permission."; //NOI18N

    private final BpsClient bpsClient;
    private final AntivirusClient antivirusClient;

    /**
     * Return an instance of CreatedCommentData with data and attachments details from BPS. The data are passed to the
     * antivirus check first and then processed to the BPS.
     *
     * @param caseviewId     the caseView id
     * @param commentText    the comment text
     * @param attachments    the attachments related
     * @param xExternalUser  X-External-User header that contains external user id.
     * @return a CreatedCommentData with result details
     */
    public CreatedCommentData runPostCommentWithAttachmentsForCase(String caseviewId, String commentText, List<FormDataBodyPart> attachments, String xExternalUser) {
        List<InvalidAttachmentMeta> invalidAttachments = convertToInvalidAttachmentWhenNoPermission(attachments);
        if (!attachments.isEmpty()) {
            invalidAttachments = getInvalidAttachmentFromVirusScan(attachments);
        }
        BpsPostNoteResponse bpsPostNoteResponse = bpsClient.executePostNote(caseviewId, commentText, attachments, invalidAttachments, xExternalUser);
        List<AttachmentMeta> validAttachments = getListValidAttachmentMeta(bpsPostNoteResponse.getValidAttachments());
        List<InvalidAttachmentMeta> bpsInvalidAttachments = getListInvalidAttachmentMeta(bpsPostNoteResponse.getInvalidAttachments());
        invalidAttachments.addAll(bpsInvalidAttachments);
        return getCommentData(bpsPostNoteResponse, validAttachments, invalidAttachments);
    }

    private CreatedCommentData getCommentData(BpsPostNoteResponse bpsPostNoteResponse,
                                              List<AttachmentMeta> validAttachments,
                                              List<InvalidAttachmentMeta> invalidAttachments) {
        return new CreatedCommentData(bpsPostNoteResponse.getBusinessKey(),
                bpsPostNoteResponse.getId(),
                bpsPostNoteResponse.getContent(),
                bpsPostNoteResponse.getUserId(),
                bpsPostNoteResponse.getCreatedAt(),
                validAttachments,
                invalidAttachments);
    }

    /**
     * Populates the invalid attachments list {List<InvalidAttachmentMeta>} with attachments that have failed the
     * anti-virus service validation and removes the bad attachments from the request attachments list
     * {List<FormDataBodyPart>}
     *
     * @param attachments the form-data list of attachments in the request
     * @return an updated list of invalid attachments
     */
    private List<InvalidAttachmentMeta> getInvalidAttachmentFromVirusScan(List<FormDataBodyPart> attachments) {
        List<InvalidAttachmentMeta> invalidAttachmentMetaList = new ArrayList<>();
        Iterator<FormDataBodyPart> attachIterator = attachments.iterator();
        while (attachIterator.hasNext()) {
            FormDataBodyPart attachment = attachIterator.next();
            AVScanResult avScanResult = antivirusClient.scan(attachment);
            AVScanResult.ScanResult scanResult = avScanResult.getScanResult();
            if (scanResult != SUCCESS) {
                invalidAttachmentMetaList.add(new InvalidAttachmentMeta(attachment.getContentDisposition().getFileName(),
                        getInvalidCode(scanResult), getInvalidDescription(scanResult)));
                attachIterator.remove();
            }
        }
        return invalidAttachmentMetaList;
    }

    /**
     * Convert all attachments to invalid when there is no Add-Attachment permission
     * Clear all attachments from the request attachments list argument
     *
     * @param attachments the form-data list of attachments in the request, will be cleared when no permissions
     * @return list of all attachments as invalid, or empty list when Add-Attachment permission exist.
     */
    private List<InvalidAttachmentMeta> convertToInvalidAttachmentWhenNoPermission(List<FormDataBodyPart> attachments) {
        List<InvalidAttachmentMeta> invalidAttachmentMetaList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(attachments) && !SecurityUtil.hasPermissions(SecurityUtil.Permission.ADD_ATTACHMENT)) {
            Iterator<FormDataBodyPart> attachIterator = attachments.iterator();
            while (attachIterator.hasNext()) {
                FormDataBodyPart attachment = attachIterator.next();
                invalidAttachmentMetaList.add(new InvalidAttachmentMeta(attachment.getContentDisposition().getFileName(),
                        NO_ATTACH_PERMISSION, NO_ATTACH_PERMISSION_DESCRIPTION));
                attachIterator.remove(); // clear all attachments from the request
            }
        }
        return invalidAttachmentMetaList;

    }

    private static InvalidAttachmentMeta.CodeEnum convertCode(BpsInvalidAttachmentDataDto.Result result) {
        switch (result) {
            case TOO_LONG:
                return FILE_TOO_LARGE;
            case UNSUPPORTED_TYPE:
                return UNSUPPORTED_FILE_TYPE;
            case BAD_DATA:
                return BAD_DATA;
            case MISMATCHED_TYPE:
                return MISMATCHED_TYPE;
            case EXCEEDS_CADENCE:
                return EXCEEDED_FILES_PER_MINUTE;
            default:
                return SYSTEM_ERROR;
        }
    }

    private static InvalidAttachmentMeta.CodeEnum getInvalidCode(AVScanResult.ScanResult scanResult) {
        switch (scanResult) {
            case FAIL:
                return FILE_FAILED_VIRUS_CHECK;
            case UNAVAILABLE:
                return FILE_VIRUS_CHECK_UNAVAILABLE;
            default:
                return FILE_VIRUS_CHECK_INCOMPLETE;
        }
    }

    private static String getInvalidDescription(AVScanResult.ScanResult scanResult) {
        switch (scanResult) {
            case FAIL:
                return FILE_FAILED_VIRUS_CHECK_DESCRIPTION;
            case UNAVAILABLE:
                return FILE_VIRUS_CHECK_UNAVAILABLE_DESCRIPTION;
            default:
                return FILE_VIRUS_CHECK_INCOMPLETE_DESCRIPTION;
        }
    }

    /**
     * Converts the valid attachments that come from the BPS to the CM API model
     *
     * @param validAttachments the attachments to convert
     * @return an instance of List<AttachmentMeta>
     */
    private List<AttachmentMeta> getListValidAttachmentMeta(List<BpsAttachmentDataDto> validAttachments) {
        if (Objects.isNull(validAttachments)) {
            return new ArrayList<>();
        } else {
            return validAttachments.stream()
                    .map(attachment -> new AttachmentMeta(attachment.getId(), attachment.getFileName(),
                            attachment.getFileSizeAsLong(), attachment.getFileType()))
                    .collect(Collectors.toList());
        }
    }

    private List<InvalidAttachmentMeta> getListInvalidAttachmentMeta(List<BpsInvalidAttachmentDataDto> invalidAttachments) {
        if (Objects.isNull(invalidAttachments)) {
            return new ArrayList<>();
        } else {
            return invalidAttachments.stream()
                    .map(attachment -> new InvalidAttachmentMeta(attachment.getFileName(),
                            convertCode(attachment.getReasonCode()),
                            attachment.getReasonText()))
                    .collect(Collectors.toList());
        }
    }

    protected List<CommentWithAttachmentsData> convertToComment(BpsGetNotesDataResponse bpsGetNotesDataResponse) {
        return bpsGetNotesDataResponse.getNotes()
                .stream()
                .filter(CommentActivitiesService::hasValidFields)
                .map(note -> new CommentWithAttachmentsData(
                        note.getBusinessKey(),
                        note.getId(),
                        truncateContent(note.getContent()),
                        note.getUserId(),
                        note.getCreatedAt(),
                        convertToV0Attachments(note.getValidAttachments())))
                .collect(Collectors.toList());
    }

    private static List<com.experian.eda.casemanagement.generated.model.AttachmentMeta> convertToV0Attachments(
            List<BpsAttachmentDataDto> validBpsAttachments) {
        if (Objects.isNull(validBpsAttachments)) {
            return new ArrayList<>();
        } else {
            if (!validBpsAttachments.isEmpty() && !SecurityUtil.hasPermissions(SecurityUtil.Permission.VIEW_ATTACHMENT)) {
                LOGGER.debug("Valid attachment(s) exist but no permission to view, return empty attachment."); //NOI18N
                return new ArrayList<>();
            }
            return validBpsAttachments.stream()
                    .map(att -> new com.experian.eda.casemanagement.generated.model.AttachmentMeta(
                            att.getId(),
                            att.getFileName(),
                            att.getFileSizeAsLong(),
                            att.getFileType()))
                    .collect(Collectors.toList());
        }
    }

    protected static boolean hasValidFields(BpsNoteDataDto note) {
        if (StringUtils.isBlank(note.getUserId()) ||
                StringUtils.isBlank(note.getBusinessKey()) ||
                StringUtils.isBlank(note.getId()) ||
                StringUtils.isBlank(note.getContent())) {
            LOGGER.warn("Filtering out non-compliant BPS Note: {}", note); // NOI18N
            return false;
        } else {
            return true;
        }
    }

    protected static String truncateContent(String content) {
        return content.length() > ServicesUtil.CONTENT_SIZE_MAX
                ? StringUtils.abbreviate(content, ServicesUtil.CONTENT_SIZE_MAX)
                : content;
    }
}
