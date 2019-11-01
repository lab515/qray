package io.github.lab515.qray.runtime;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Remotable {
    Remotype type() default Remotype.REMOTE; // for test case, it's runnable, for lib, it's remotable
    String provider() default "";
    boolean rollback() default false; // reseved for hybrid mode, not used, for test case, please call rollback explictly
    boolean beamMeUp() default true;
    boolean batch() default true;
    String[] resources() default {};
}