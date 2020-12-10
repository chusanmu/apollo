package com.ctrip.framework.apollo.spring.config;

import java.util.List;

import com.ctrip.framework.apollo.Config;
import com.google.common.collect.Lists;

public class ConfigPropertySourceFactory {

  private final List<ConfigPropertySource> configPropertySources = Lists.newLinkedList();

  public ConfigPropertySource getConfigPropertySource(String name, Config source) {
    ConfigPropertySource configPropertySource = new ConfigPropertySource(name, source);

    // TODO: 把namespace对应的 propertySource存起来
    configPropertySources.add(configPropertySource);

    return configPropertySource;
  }

  public List<ConfigPropertySource> getAllConfigPropertySources() {
    return Lists.newLinkedList(configPropertySources);
  }
}
