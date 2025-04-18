/*
 * @Author: jackning 270580156@qq.com
 * @Date: 2024-06-14 10:45:08
 * @LastEditors: jackning 270580156@qq.com
 * @LastEditTime: 2025-03-27 16:57:13
 * @Description: bytedesk.com https://github.com/Bytedesk/bytedesk
 *   Please be aware of the BSL license restrictions before installing Bytedesk IM – 
 *  selling, reselling, or hosting Bytedesk IM as a service is a breach of the terms and automatically terminates your rights under the license.
 *  Business Source License 1.1: https://github.com/Bytedesk/bytedesk/blob/main/LICENSE 
 *  contact: 270580156@qq.com 
 *  联系：270580156@qq.com
 * Copyright (c) 2024 by bytedesk.com, All Rights Reserved. 
 */
package com.bytedesk.kbase.settings;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.bytedesk.core.constant.BytedeskConsts;
import com.bytedesk.core.constant.I18Consts;
import com.bytedesk.core.constant.TypeConsts;
import com.bytedesk.core.enums.LanguageEnum;
import com.bytedesk.kbase.faq.FaqEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Embeddable
@Builder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class ServiceSettings implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank
    @Builder.Default
    private String language = LanguageEnum.ZH_CN.name();

    @NotBlank
    @Builder.Default
    private boolean autoPopup = false;

    // 顶部提示开关
    @NotBlank
    @Builder.Default
    private boolean showTopTip = false;
    
    @NotBlank
    @Builder.Default
    private String topTip = I18Consts.I18N_TOP_TIP;

    private LocalDateTime topTipStart;

    private LocalDateTime topTipEnd;

    // 满意度评价设置--------------------------------------------------
    // show rate btn on chat toolbar
    @NotBlank
    @Builder.Default
    private boolean showRateBtn = false;

    // 关闭会话时自动发送满意度评价
    @NotBlank
    @Builder.Default
    private boolean autoInviteRate = false;

    // invite rate tip
    @Builder.Default
    private String inviteRateTip = I18Consts.I18N_INVITE_RATE_TIP;
    
    // 自定义评价最低消息数量，未达到最低对话消息数，禁止评价
    @NotBlank
    @Builder.Default
    private int rateMsgCount = 3;

    // 5星评分、10星评分
    // @Builder.Default
    // private int rateStarCount = 5;

    // 评价选择按钮文字
    // @Builder.Default
    // @Convert(converter = StringListConverter.class)
    // @Column(columnDefinition = TypeConsts.COLUMN_TYPE_TEXT)
    // private List<String> rateTagList = new ArrayList<>();

    //-----------------------------------------------------------------------------------

    // 询前表单
    // 是否显示询前表单
    // TODO: 自定义询前表单字段
    @NotBlank
    @Builder.Default
    private boolean showPreForm = false;

    @Builder.Default
    @Column(columnDefinition = TypeConsts.COLUMN_TYPE_TEXT)
    private String preForm = BytedeskConsts.EMPTY_JSON_STRING;

    //-----------------------------------------------------------------------------------

    // show history message or not
    @NotBlank
    @Builder.Default
    private boolean showHistory = false;

    // 输入联想开关
    @NotBlank
    @Builder.Default
    private boolean showInputAssociation = false;

    // 防骚扰验证开关，TODO: 自定义验证规则: 1. 访问频率 2. 发消息时间间隔
    @NotBlank
    @Builder.Default
    private boolean showCaptcha = false;

    @NotBlank
    @Builder.Default
    @Column(columnDefinition = TypeConsts.COLUMN_TYPE_TEXT)
    private String welcomeTip = I18Consts.I18N_WELCOME_TIP;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    private List<FaqEntity> welcomeFaqs = new ArrayList<>();

    //-----------------------------------------------------------------------------------

    /** auto close time in minutes */
    @NotBlank
    @Builder.Default
    private Double autoCloseMin = Double.valueOf(25);

    @Builder.Default
    private String autoCloseTip = I18Consts.I18N_AUTO_CLOSE_TIP;

    @Builder.Default
    private String agentCloseTip = I18Consts.I18N_AGENT_CLOSE_TIP;

    //-----------------------------------------------------------------------------------

    // 桌面版聊天窗口右侧iframe
    @Builder.Default
    private boolean showRightIframe = false;

    // 桌面版聊天窗口右侧iframe地址
    private String rightIframeUrl;

    // 是否显示预搜索
    @Builder.Default
    private boolean showPreSearch = true;

    // 常见问题
    @Builder.Default
    private boolean showFaqs = false;
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    private List<FaqEntity> faqs = new ArrayList<>();

    // 快捷按钮
    @Builder.Default
    private boolean showQuickFaqs = false;
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    private List<FaqEntity> quickFaqs = new ArrayList<>();

    // 猜你想问
    @Builder.Default
    private boolean showGuessFaqs = false;
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    private List<FaqEntity> guessFaqs = new ArrayList<>();

    // 热门问题
    @Builder.Default
    private boolean showHotFaqs = false;
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    private List<FaqEntity> hotFaqs = new ArrayList<>();

    // 快捷功能
    @Builder.Default
    private boolean showShortcutFaqs = false;
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    private List<FaqEntity> shortcutFaqs = new ArrayList<>();

    // 未知答案固定回复
    @Builder.Default
    private boolean showUnknownAnswer = false;

    @Builder.Default
    private String unknownAnswer = "抱歉，我暂时无法回答这个问题。";

    // 满足一定触发条件下，机器人支持主动触发某个任务或者回复某些话术。
    // 例如：1、长时间访客无消息，机器人主动发问或者触发任务，主动暖场；
    // 2、满足一定条件，自动触发某个任务。

    // 主动触发设置
    @Builder.Default
    private boolean enableProactiveTrigger = false;  // 是否启用主动触发

    @Builder.Default
    private int noResponseTimeout = 300;  // 访客无响应超时时间(秒)，默认5分钟

    @Builder.Default
    @Column(columnDefinition = TypeConsts.COLUMN_TYPE_TEXT)
    private String proactiveMessage = "您好，看起来您有一段时间没有互动了。请问还需要帮助吗？";  // 主动发送的消息内容

    @Builder.Default
    private int maxProactiveCount = 3;  // 最大主动触发次数，防止打扰用户

    @Builder.Default
    private int proactiveInterval = 600;  // 两次主动触发的最小间隔(秒)，默认10分钟

    /**
     * @see ServiceTrigger
     */
    @Builder.Default
    @Column(columnDefinition = TypeConsts.COLUMN_TYPE_TEXT)
    private String triggerConditions = BytedeskConsts.EMPTY_JSON_STRING;  // 触发条件配置，JSON格式

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    private List<FaqEntity> proactiveFaqs = new ArrayList<>();  // 主动推送的常见问题列表

    @NotBlank
    @Builder.Default
    private boolean showLogo = true;

    // validate until date, when expire the service will be disabled
    private Date validateUntil;
    
}
