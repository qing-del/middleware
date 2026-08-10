package com.jacolp.module.system.biz.application.port.out;

import com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeDeliveryRequest;

/** Synchronous delivery boundary for one login email-code. */
public interface EmailLoginCodeDeliveryPort {

    void deliver(EmailLoginCodeDeliveryRequest request);
}
