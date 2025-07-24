package com.java_template.application.criterion;

import com.java_template.application.entity.Mail;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;

public class IsGloomyMailCriterion implements CyodaCriterion {

    @Override
    public boolean test(CyodaEventContext<?> context) {
        Object entity = context.getEntity();
        if (entity instanceof Mail) {
            Mail mail = (Mail) entity;
            return mail.getIsHappy() != null && !mail.getIsHappy();
        }
        return false;
    }

}