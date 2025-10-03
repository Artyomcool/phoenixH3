package phoenix.h3.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface R {
    int ABSOLUTE = -1;

    int EDI = 0;
    int ESI = 1;
    int EBP = 2;
    int ESP = 3;
    int EBX = 4;
    int EDX = 5;
    int ECX = 6;
    int EAX = 7;

    int value();
}
