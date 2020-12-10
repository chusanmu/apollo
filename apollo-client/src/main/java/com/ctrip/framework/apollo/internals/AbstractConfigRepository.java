package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.util.factory.PropertiesFactory;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.util.ExceptionUtil;
import com.google.common.collect.Lists;

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public abstract class AbstractConfigRepository implements ConfigRepository {
  private static final Logger logger = LoggerFactory.getLogger(AbstractConfigRepository.class);
  private List<RepositoryChangeListener> m_listeners = Lists.newCopyOnWriteArrayList();
  protected PropertiesFactory propertiesFactory = ApolloInjector.getInstance(PropertiesFactory.class);

  /**
   * TODO: 尝试同步
   * @return
   */
  protected boolean trySync() {
    try {
      // TODO: 开始同步，然后返回true
      sync();
      return true;
    } catch (Throwable ex) {
      Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(ex));
      logger
          .warn("Sync config failed, will retry. Repository {}, reason: {}", this.getClass(), ExceptionUtil
              .getDetailMessage(ex));
    }
    return false;
  }

  protected abstract void sync();

  /**
   * TODO: 当前listeners集合不包括listener，才进行添加
   *
   * @param listener the listener to observe the changes
   */
  @Override
  public void addChangeListener(RepositoryChangeListener listener) {
    if (!m_listeners.contains(listener)) {
      m_listeners.add(listener);
    }
  }

  /**
   * TODO: 移除listener监听
   * @param listener the listener to remove
   */
  @Override
  public void removeChangeListener(RepositoryChangeListener listener) {
    m_listeners.remove(listener);
  }

  /**
   * TODO: 传播仓库变更事件
   *
   * @param namespace
   * @param newProperties
   */
  protected void fireRepositoryChange(String namespace, Properties newProperties) {
    // TODO: 遍历listeners 逐个的进行回调
    for (RepositoryChangeListener listener : m_listeners) {
      try {
        listener.onRepositoryChange(namespace, newProperties);
      } catch (Throwable ex) {
        Tracer.logError(ex);
        logger.error("Failed to invoke repository change listener {}", listener.getClass(), ex);
      }
    }
  }
}
