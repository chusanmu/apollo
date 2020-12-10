package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.enums.ConfigSourceType;
import java.util.Properties;

/**
 * TODO: 配置仓库
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ConfigRepository {
  /**
   * TODO: 从仓库获得config
   * Get the config from this repository.
   * @return config
   */
  Properties getConfig();

  /**
   * Set the fallback repo for this repository.
   * @param upstreamConfigRepository the upstream repo
   */
  void setUpstreamRepository(ConfigRepository upstreamConfigRepository);

  /**
   * TODO: 添加变动监听
   * Add change listener.
   * @param listener the listener to observe the changes
   */
  void addChangeListener(RepositoryChangeListener listener);

  /**
   * 移除配置监听
   * Remove change listener.
   * @param listener the listener to remove
   */
  void removeChangeListener(RepositoryChangeListener listener);

  /**
   * TODO: 返回config的类型
   * Return the config's source type, i.e. where is the config loaded from
   *
   * @return the config's source type
   */
  ConfigSourceType getSourceType();
}
