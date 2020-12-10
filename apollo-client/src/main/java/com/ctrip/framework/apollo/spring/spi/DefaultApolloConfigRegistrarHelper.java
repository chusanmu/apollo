package com.ctrip.framework.apollo.spring.spi;

import com.ctrip.framework.apollo.core.spi.Ordered;
import com.ctrip.framework.apollo.spring.annotation.ApolloAnnotationProcessor;
import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;
import com.ctrip.framework.apollo.spring.annotation.SpringValueProcessor;
import com.ctrip.framework.apollo.spring.config.PropertySourcesProcessor;
import com.ctrip.framework.apollo.spring.property.SpringValueDefinitionProcessor;
import com.ctrip.framework.apollo.spring.util.BeanRegistrationUtil;
import com.google.common.collect.Lists;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

public class DefaultApolloConfigRegistrarHelper implements ApolloConfigRegistrarHelper {

  private Environment environment;

  @Override
  public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
    // TODO: 获取EnableApolloConfig注解
    AnnotationAttributes attributes = AnnotationAttributes
        .fromMap(importingClassMetadata.getAnnotationAttributes(EnableApolloConfig.class.getName()));
    // TODO: value指定了多个namespace
    final String[] namespaces = attributes.getStringArray("value");
    final int order = attributes.getNumber("order");
    // TODO: 解析namespace
    final String[] resolvedNamespaces = this.resolveNamespaces(namespaces);

    PropertySourcesProcessor.addNamespaces(Lists.newArrayList(resolvedNamespaces), order);

    Map<String, Object> propertySourcesPlaceholderPropertyValues = new HashMap<>();
    // to make sure the default PropertySourcesPlaceholderConfigurer's priority is higher than PropertyPlaceholderConfigurer
    propertySourcesPlaceholderPropertyValues.put("order", 0);


    /* ---------------- 开始进行注册bean -------------- */

    // TODO: 注册 PropertySourcesPlaceholderConfigurer 这个bean
    BeanRegistrationUtil.registerBeanDefinitionIfNotExists(registry, PropertySourcesPlaceholderConfigurer.class.getName(),
        PropertySourcesPlaceholderConfigurer.class, propertySourcesPlaceholderPropertyValues);
    // TODO: 注册PropertySourcesProcessor
    BeanRegistrationUtil.registerBeanDefinitionIfNotExists(registry, PropertySourcesProcessor.class.getName(),
        PropertySourcesProcessor.class);
    BeanRegistrationUtil.registerBeanDefinitionIfNotExists(registry, ApolloAnnotationProcessor.class.getName(),
        ApolloAnnotationProcessor.class);
    BeanRegistrationUtil.registerBeanDefinitionIfNotExists(registry, SpringValueProcessor.class.getName(),
        SpringValueProcessor.class);
    BeanRegistrationUtil.registerBeanDefinitionIfNotExists(registry, SpringValueDefinitionProcessor.class.getName(),
        SpringValueDefinitionProcessor.class);
  }

  /**
   * TODO: 获取 namespaces，通过environment进行解析namespace，允许你用 ${} 的方式进行配置
   *
   * @param namespaces
   * @return
   */
  private String[] resolveNamespaces(String[] namespaces) {
    String[] resolvedNamespaces = new String[namespaces.length];
    for (int i = 0; i < namespaces.length; i++) {
      // throw IllegalArgumentException if given text is null or if any placeholders are unresolvable
      resolvedNamespaces[i] = this.environment.resolveRequiredPlaceholders(namespaces[i]);
    }
    return resolvedNamespaces;
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }
}
