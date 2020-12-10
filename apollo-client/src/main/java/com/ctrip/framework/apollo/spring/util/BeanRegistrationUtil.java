package com.ctrip.framework.apollo.spring.util;

import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public class BeanRegistrationUtil {
  public static boolean registerBeanDefinitionIfNotExists(BeanDefinitionRegistry registry, String beanName,
      Class<?> beanClass) {
    return registerBeanDefinitionIfNotExists(registry, beanName, beanClass, null);
  }

  /**
   * 注册bean，如果不存在的情况下，进行注册
   * @param registry
   * @param beanName
   * @param beanClass
   * @param extraPropertyValues
   * @return
   */
  public static boolean registerBeanDefinitionIfNotExists(BeanDefinitionRegistry registry, String beanName,
                                                          Class<?> beanClass, Map<String, Object> extraPropertyValues) {
    // TODO: 已经包括了这个beanName, 就不会再注册了
    if (registry.containsBeanDefinition(beanName)) {
      return false;
    }

    // TODO: 把所有的beanDefinitionNames 拿到
    String[] candidates = registry.getBeanDefinitionNames();

    for (String candidate : candidates) {
      // TODO: 通过name,得到 beanDefinition
      BeanDefinition beanDefinition = registry.getBeanDefinition(candidate);
      // TODO: beanName不同，但是容器里面已经注册过这个类型的bean了 也不允许注册了
      if (Objects.equals(beanDefinition.getBeanClassName(), beanClass.getName())) {
        return false;
      }
    }

    // TODO: 创建beanDefinition
    BeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition(beanClass).getBeanDefinition();

    // TODO: 如果它的属性不为空
    if (extraPropertyValues != null) {
      // TODO: 进行设置属性值
      for (Map.Entry<String, Object> entry : extraPropertyValues.entrySet()) {
        beanDefinition.getPropertyValues().add(entry.getKey(), entry.getValue());
      }
    }

    // TODO: 最后注册beanDefinition
    registry.registerBeanDefinition(beanName, beanDefinition);

    return true;
  }


}
