/*
 * @Author: jackning 270580156@qq.com
 * @Date: 2024-03-22 22:59:18
 * @LastEditors: jackning 270580156@qq.com
 * @LastEditTime: 2025-04-12 16:59:16
 * @Description: bytedesk.com https://github.com/Bytedesk/bytedesk
 *   Please be aware of the BSL license restrictions before installing Bytedesk IM – 
 *  selling, reselling, or hosting Bytedesk IM as a service is a breach of the terms and automatically terminates your rights under the license.
 *  Business Source License 1.1: https://github.com/Bytedesk/bytedesk/blob/main/LICENSE 
 *  contact: 270580156@qq.com 
 *  联系：270580156@qq.com
 * Copyright (c) 2024 by bytedesk.com, All Rights Reserved. 
 */
package com.bytedesk.kbase.llm.qa;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.alibaba.fastjson2.JSON;
import com.bytedesk.core.base.BaseRestServiceWithExcel;
import com.bytedesk.core.category.CategoryEntity;
import com.bytedesk.core.category.CategoryRequest;
import com.bytedesk.core.category.CategoryResponse;
import com.bytedesk.core.category.CategoryRestService;
import com.bytedesk.core.message.MessageTypeEnum;
import com.bytedesk.core.rbac.auth.AuthService;
import com.bytedesk.core.rbac.user.UserEntity;
import com.bytedesk.core.uid.UidUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class QaRestService extends BaseRestServiceWithExcel<QaEntity, QaRequest, QaResponse, QaExcel> {

    private final QaRepository qaRepository;

    private final ModelMapper modelMapper;

    private final UidUtils uidUtils;

    private final CategoryRestService categoryService;

    private final AuthService authService;

    @Override
    public Page<QaEntity> queryByOrgEntity(QaRequest request) {
        Pageable pageable = request.getPageable();
        Specification<QaEntity> spec = QaSpecification.search(request);
        return qaRepository.findAll(spec, pageable);
    }

    @Override
    public Page<QaResponse> queryByOrg(QaRequest request) {
        Page<QaEntity> page = queryByOrgEntity(request);
        return page.map(this::convertToResponse);
    }

    @Override
    public Page<QaResponse> queryByUser(QaRequest request) {
        UserEntity user = authService.getUser();
        if (user == null) {
            throw new RuntimeException("user not found");
        }
        request.setUserUid(user.getUid());
        //
        return queryByOrg(request);
    }

    @Override
    public QaResponse queryByUid(QaRequest request) {
        Optional<QaEntity> optionalEntity = findByUid(request.getUid());
        if (optionalEntity.isPresent()) {
            QaEntity entity = optionalEntity.get();
            entity.increaseClickCount();
            // 
            QaEntity savedEntity = qaRepository.save(entity);
            if (savedEntity == null) {
                throw new RuntimeException("Failed to update click count");
            }
            return convertToResponse(savedEntity);
        }
        return null;
    }

    @Cacheable(value = "qa", key = "#uid", unless = "#result == null")
    @Override
    public Optional<QaEntity> findByUid(String uid) {
        return qaRepository.findByUid(uid);
    }

    @Cacheable(value = "qa", key = "#question", unless = "#result == null")
    public List<QaEntity> findByQuestionContains(String question) {
        return qaRepository.findByQuestionContains(question);
    }

    public Boolean existsByUid(String uid) {
        return qaRepository.existsByUid(uid);
    }

    @Override
    @Transactional
    public QaResponse create(QaRequest request) {
        try {
            // 如果提供了uid，先尝试查找现有记录
            if (StringUtils.hasText(request.getUid())) {
                Optional<QaEntity> existingQa = findByUid(request.getUid());
                if (existingQa.isPresent()) {
                    return convertToResponse(existingQa.get());
                }
            }
            // 创建新记录
            QaEntity entity = modelMapper.map(request, QaEntity.class);
            if (!StringUtils.hasText(request.getUid())) {
                entity.setUid(uidUtils.getUid());
            }
            UserEntity user = authService.getUser();
            if (user != null) {
                entity.setUserUid(user.getUid());
            }
            // 如何将string类型startDate和endDate转换为LocalDateTime类型？
            // if (StringUtils.hasText(request.getStartDate())) {
            //     entity.setStartDate(BdDateUtils.parseLocalDateTime(request.getStartDate()));
            // }
            // if (StringUtils.hasText(request.getEndDate())) {
            //     entity.setEndDate(BdDateUtils.parseLocalDateTime(request.getEndDate()));
            // }
            // entity.setType(MessageTypeEnum.fromValue(request.getType()).name());
            // 根据request.relatedQaUids查找关联的FAQ
            List<QaEntity> relatedQas = new ArrayList<>();
            for (String relatedQaUid : request.getRelatedQaUids()) {
                Optional<QaEntity> relatedQa = findByUid(relatedQaUid);
                if (relatedQa.isPresent()) {
                    relatedQas.add(relatedQa.get());
                } else {
                    throw new RuntimeException("relatedQaUid not found");
                }
            }
            entity.setRelatedQas(relatedQas);

            try {
                QaEntity savedEntity = save(entity);
                if (savedEntity != null) {
                    return convertToResponse(savedEntity);
                }
            } catch (Exception e) {
                // 如果保存时发生唯一键冲突，再次尝试查找
                if (e.getCause() instanceof java.sql.SQLIntegrityConstraintViolationException) {
                    Optional<QaEntity> existingQa = findByUid(entity.getUid());
                    if (existingQa.isPresent()) {
                        return convertToResponse(existingQa.get());
                    }
                }
                throw e;
            }

            throw new RuntimeException("Failed to create FAQ");
        } catch (Exception e) {
            log.error("Error creating FAQ: {}", e.getMessage(), e);
            throw new RuntimeException("Error creating FAQ", e);
        }
    }

    @Override
    @Transactional
    public QaResponse update(QaRequest request) {

        Optional<QaEntity> optional = findByUid(request.getUid());
        if (optional.isPresent()) {
            QaEntity entity = optional.get();
            // modelMapper.map(request, entity);
            entity.setQuestion(request.getQuestion());
            entity.setAnswer(request.getAnswer());
            entity.setAnswerList(request.getAnswerList());
            entity.setStatus(request.getStatus());
            entity.setTagList(request.getTagList());
            entity.setType(request.getType());
            entity.setEnabled(request.getEnabled());
            // 如何将string类型startDate和endDate转换为LocalDateTime类型？
            // if (StringUtils.hasText(request.getStartDate())) {
            //     entity.setStartDate(BdDateUtils.parseLocalDateTime(request.getStartDate()));
            // }
            // if (StringUtils.hasText(request.getEndDate())) {
            //     entity.setEndDate(BdDateUtils.parseLocalDateTime(request.getEndDate()));
            // }
            entity.setStartDate(request.getStartDate());
            entity.setEndDate(request.getEndDate());
            //
            entity.setCategoryUid(request.getCategoryUid());
            entity.setKbUid(request.getKbUid());
            //
            // 根据request.relatedQaUids查找关联的FAQ
            List<QaEntity> relatedQas = new ArrayList<>();
            for (String relatedQaUid : request.getRelatedQaUids()) {
                Optional<QaEntity> relatedQa = findByUid(relatedQaUid);
                if (relatedQa.isPresent()) {
                    relatedQas.add(relatedQa.get());
                } else {
                    throw new RuntimeException("relatedQaUid not found");
                }
            }
            entity.setRelatedQas(relatedQas);

            return convertToResponse(save(entity));
        } else {
            throw new RuntimeException("qa not found");
        }
    }

    // update enable
    public QaResponse enable(QaRequest request) {
        Optional<QaEntity> optional = findByUid(request.getUid());
        if (optional.isPresent()) {
            QaEntity entity = optional.get();
            entity.setEnabled(request.getEnabled());
            QaEntity savedEntity = save(entity);
            if (savedEntity == null) {
                throw new RuntimeException("Failed to update FAQ");
            }
            return convertToResponse(savedEntity);
        } else {
            throw new RuntimeException("qa not found");
        }
    }

    public QaResponse rateUp(String uid) {
        Optional<QaEntity> optional = findByUid(uid);
        if (optional.isPresent()) {
            QaEntity entity = optional.get();
            entity.increaseUpCount();
            // 
            QaEntity savedEntity = save(entity);
            if (savedEntity == null) {
                throw new RuntimeException("Failed to rate up FAQ");
            }
            return convertToResponse(savedEntity);
        } else {
            throw new RuntimeException("qa not found");
        }
    }

    public QaResponse rateDown(String uid) {
        Optional<QaEntity> optional = findByUid(uid);
        if (optional.isPresent()) {
            QaEntity entity = optional.get();
            entity.increaseDownCount();
            // 
            QaEntity savedEntity = save(entity);
            if (savedEntity == null) {
                throw new RuntimeException("Failed to rate down FAQ");
            }
            return convertToResponse(save(entity));
        } else {
            throw new RuntimeException("qa not found");
        }
    }

    @Override
    public QaEntity save(QaEntity entity) {
        try {
            return doSave(entity);
        } catch (ObjectOptimisticLockingFailureException e) {
            return handleOptimisticLockingFailureException(e, entity);
        }
    }

    @Override
    protected QaEntity doSave(QaEntity entity) {
        return qaRepository.save(entity);
    }

    @Override
    public QaEntity handleOptimisticLockingFailureException(ObjectOptimisticLockingFailureException e, QaEntity entity) {
        try {
            log.warn("处理乐观锁冲突: {}", entity.getUid());
            Optional<QaEntity> latest = qaRepository.findByUid(entity.getUid());
            if (latest.isPresent()) {
                QaEntity latestEntity = latest.get();
                // 保持原有实体的部分属性
                latestEntity.setQuestion(entity.getQuestion());
                latestEntity.setAnswer(entity.getAnswer());
                latestEntity.setAnswerList(entity.getAnswerList());
                latestEntity.setType(entity.getType());
                latestEntity.setEnabled(entity.isEnabled());
                latestEntity.setCategoryUid(entity.getCategoryUid());
                latestEntity.setKbUid(entity.getKbUid());
                
                // 处理相关问答时特别小心
                if (entity.getRelatedQas() != null && !entity.getRelatedQas().isEmpty()) {
                    latestEntity.setRelatedQas(entity.getRelatedQas());
                }
                
                // 文档ID列表和状态
                latestEntity.setDocIdList(entity.getDocIdList());
                latestEntity.setStatus(entity.getStatus());
                latestEntity.setTagList(entity.getTagList());
                
                return qaRepository.save(latestEntity);
            }
        } catch (Exception ex) {
            log.error("无法处理乐观锁冲突: {}", ex.getMessage(), ex);
        }
        throw new RuntimeException("无法解决实体版本冲突: " + entity.getUid());
    }

    public void save(List<QaEntity> entities) {
        qaRepository.saveAll(entities);
    }

    @Override
    public void deleteByUid(String uid) {
        Optional<QaEntity> optional = findByUid(uid);
        if (optional.isPresent()) {
            optional.get().setDeleted(true);
            save(optional.get());
        }
    }

    @Override
    public void delete(QaRequest entity) {
        deleteByUid(entity.getUid());
    }

    @Override
    public QaResponse convertToResponse(QaEntity entity) {
        // return modelMapper.map(entity, QaResponse.class);
        QaResponse response = QaResponse.builder()
                .uid(entity.getUid())
                .question(entity.getQuestion())
                .answer(entity.getAnswer())
                .answerList(entity.getAnswerList())
                .isLlmQa(entity.isLlmQa())
                .type(entity.getType())
                .status(entity.getStatus())
                .viewCount(entity.getViewCount())
                .clickCount(entity.getClickCount())
                .upCount(entity.getUpCount())
                .downCount(entity.getDownCount())
                // .downShowTransferToAgentButton(entity.isDownShowTransferToAgentButton())
                .enabled(entity.isEnabled())
                .tagList(entity.getTagList())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .categoryUid(entity.getCategoryUid())
                .kbUid(entity.getKbUid())
                .fileUid(entity.getFileUid())
                // .docUid(entity.getDocId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();

        // 处理相关问题，避免循环依赖
        if (entity.getRelatedQas() != null) {
            List<QaResponse.SimpleQaResponse> simpleQas = entity.getRelatedQas().stream()
                    .map(relatedQa -> QaResponse.SimpleQaResponse.builder()
                            .uid(relatedQa.getUid())
                            .question(relatedQa.getQuestion())
                            .answer(relatedQa.getAnswer())
                            .type(relatedQa.getType())
                            .status(relatedQa.getStatus())
                            .build())
                    .collect(Collectors.toList());
            response.setRelatedQas(simpleQas);
        }

        return response;
    }

    @Override
    public QaExcel convertToExcel(QaEntity qa) {
        QaExcel excel = modelMapper.map(qa, QaExcel.class);
        if (StringUtils.hasText(qa.getCategoryUid())) {
            Optional<CategoryEntity> categoryOptional = categoryService.findByUid(qa.getCategoryUid());
            if (categoryOptional.isPresent()) {
                excel.setCategory(categoryOptional.get().getName());
            } else {
                excel.setCategory("未分类");
            }
        } else {
            excel.setCategory("未分类");
        }
        excel.setAnswerList(JSON.toJSONString(qa.getAnswerList()));
        if (qa.isEnabled()) {
            excel.setEnabled("是");
        } else {
            excel.setEnabled("否");
        }

        return excel;
    }

    public QaEntity convertExcelToQa(QaExcel excel, String uploadType, String fileUid, String kbUid, String orgUid) {
        // return modelMapper.map(excel, Qa.class); // String categoryUid,
        QaEntity qa = QaEntity.builder().build();
        qa.setUid(uidUtils.getUid());
        qa.setQuestion(excel.getQuestion());
        qa.setAnswer(excel.getAnswer());
        qa.setType(MessageTypeEnum.fromValue(excel.getType()).name());
        //
        Optional<CategoryEntity> categoryOptional = categoryService.findByNameAndKbUid(excel.getCategory(), kbUid);
        if (categoryOptional.isPresent()) {
            qa.setCategoryUid(categoryOptional.get().getUid());
        } else {
            // create category
            CategoryRequest categoryRequest = CategoryRequest.builder()
                    .name(excel.getCategory())
                    .type(uploadType)
                    .kbUid(kbUid)
                    .orgUid(orgUid)
                    .build();
            CategoryResponse categoryResponse = categoryService.create(categoryRequest);
            qa.setCategoryUid(categoryResponse.getUid());
        }
        qa.setFileUid(fileUid);
        qa.setKbUid(kbUid);
        qa.setOrgUid(orgUid);
        //
        return qa;
    }


}
