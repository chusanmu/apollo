package com.ctrip.framework.apollo.spring.annotation;

import com.ctrip.framework.apollo.spring.spi.ApolloConfigRegistrarHelper;
import com.ctrip.framework.foundation.internals.ServiceBootstrap;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public class ApolloConfigRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

  /**
   * TODO: 使用SPI的方式 加载ApolloConfigRegistrarHelp, 默认是 DefaultApolloConfigRegistrarHelper
   */
  private final ApolloConfigRegistrarHelper helper = ServiceBootstrap.loadPrimary(ApolloConfigRegistrarHelper.class);

  @Override
  public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
    helper.registerBeanDefinitions(importingClassMetadata, registry);
  }

  /**
   * TODO: 设置environment
   *
   * @param environment
   */
  @Override
  public void setEnvironment(Environment environment) {
    this.helper.setEnvironment(environment);
  }

}
