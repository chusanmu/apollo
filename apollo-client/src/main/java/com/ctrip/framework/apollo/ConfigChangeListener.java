package com.ctrip.framework.apollo;

import com.ctrip.framework.apollo.model.ConfigChangeEvent;

/**
 * TODO: 配置文件变更监听器
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ConfigChangeListener {
  /**
   * TODO: 当配置变更的时候，触发事件，changeEvent
   * Invoked when there is any config change for the namespace.
   * @param changeEvent the event for this change
   */
  void onChange(ConfigChangeEvent changeEvent);
}
