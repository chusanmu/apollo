package com.ctrip.framework.apollo.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

import com.ctrip.framework.apollo.core.ConfigConsts;

/**
 * TODO: 两种方式启用apollo, 一种是使用这个注解进行启动，另一个是使用配置文件去启动 apollo.bootstrap.enabled 为启用时，也会启用apollo
 * Use this annotation to register Apollo property sources when using Java Config.
 *
 * <p>Configuration example with multiple namespaces:</p>
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableApolloConfig({"someNamespace","anotherNamespace"})
 * public class AppConfig {
 *
 * }
 * </pre>
 *
 * <p>Configuration example with placeholder:</p>
 * <pre class="code">
 * // The namespace could also be specified as a placeholder, e.g. ${redis.namespace:xxx},
 * // which will use the value of the key "redis.namespace" or "xxx" if this key is not configured.
 * // Please note that this placeholder could not be configured in Apollo as Apollo is not activated during this phase.
 * &#064;Configuration
 * &#064;EnableApolloConfig({"${redis.namespace:xxx}"})
 * public class AppConfig {
 *
 * }
 * </pre>
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(ApolloConfigRegistrar.class)
public @interface EnableApolloConfig {
  /**
   * Apollo namespaces to inject configuration into Spring Property Sources.
   */
  String[] value() default {ConfigConsts.NAMESPACE_APPLICATION};

  /**
   * The order of the apollo config, default is {@link Ordered#LOWEST_PRECEDENCE}, which is Integer.MAX_VALUE.
   * If there are properties with the same name in different apollo configs, the apollo config with smaller order wins.
   * @return
   */
  int order() default Ordered.LOWEST_PRECEDENCE;
}
