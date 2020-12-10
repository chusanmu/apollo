package com.ctrip.framework.apollo.internals;

import java.util.Properties;

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public interface RepositoryChangeListener {
  /**
   * TODO: 当namespace对应的properties发生变更的时候 会进行回调
   * Invoked when config repository changes.
   * @param namespace the namespace of this repository change
   * @param newProperties the properties after change
   */
  void onRepositoryChange(String namespace, Properties newProperties);
}
