/*
 * @Author: jackning 270580156@qq.com
 * @Date: 2024-07-09 22:19:21
 * @LastEditors: jackning 270580156@qq.com
 * @LastEditTime: 2025-04-12 17:25:51
 * @Description: bytedesk.com https://github.com/Bytedesk/bytedesk
 *   Please be aware of the BSL license restrictions before installing Bytedesk IM – 
 *  selling, reselling, or hosting Bytedesk IM as a service is a breach of the terms and automatically terminates your rights under the license.
 *  Business Source License 1.1: https://github.com/Bytedesk/bytedesk/blob/main/LICENSE 
 *  contact: 270580156@qq.com 
 *  联系：270580156@qq.com
 * Copyright (c) 2024 by bytedesk.com, All Rights Reserved. 
 */
package com.bytedesk.kbase.llm.text;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.bytedesk.core.base.BaseSpecification;

import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TextSpecification extends BaseSpecification {
    
    public static Specification<TextEntity> search(TextRequest request) {
        log.info("request: {}", request);
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.addAll(getBasicPredicates(root, criteriaBuilder, request.getOrgUid()));
            // name
            if (StringUtils.hasText(request.getName())) {
                predicates.add(criteriaBuilder.like(root.get("name"), "%" + request.getName() + "%"));
            }
            // content
            if (StringUtils.hasText(request.getContent())) {
                predicates.add(criteriaBuilder.like(root.get("content"), "%" + request.getContent() + "%"));
            }
            // categoryUid
            if (StringUtils.hasText(request.getCategoryUid())) {
                predicates.add(criteriaBuilder.equal(root.get("categoryUid"), request.getCategoryUid()));
            }
            // kbUid
            if (StringUtils.hasText(request.getKbUid())) {  
                predicates.add(criteriaBuilder.equal(root.get("kbUid"), request.getKbUid()));
            }
            //
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
