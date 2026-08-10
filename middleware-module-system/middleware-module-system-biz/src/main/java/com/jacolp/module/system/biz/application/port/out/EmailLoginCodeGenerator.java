package com.jacolp.module.system.biz.application.port.out;

/** Generates one raw email login code for immediate protected storage. */
public interface EmailLoginCodeGenerator {
    String generate();
}
