package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigChangeListener;
import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.enums.PropertyChangeType;
import com.ctrip.framework.apollo.exceptions.ApolloConfigException;
import com.ctrip.framework.apollo.model.ConfigChange;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.ctrip.framework.apollo.util.factory.PropertiesFactory;
import com.ctrip.framework.apollo.util.function.Functions;
import com.ctrip.framework.apollo.util.parser.Parsers;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public abstract class AbstractConfig implements Config {
  private static final Logger logger = LoggerFactory.getLogger(AbstractConfig.class);

  private static final ExecutorService m_executorService;

  private final List<ConfigChangeListener> m_listeners = Lists.newCopyOnWriteArrayList();
  // TODO: 维护了感兴趣的keys和感兴趣的key的前缀
  private final Map<ConfigChangeListener, Set<String>> m_interestedKeys = Maps.newConcurrentMap();
  private final Map<ConfigChangeListener, Set<String>> m_interestedKeyPrefixes = Maps.newConcurrentMap();
  private final ConfigUtil m_configUtil;
  private volatile Cache<String, Integer> m_integerCache;
  private volatile Cache<String, Long> m_longCache;
  private volatile Cache<String, Short> m_shortCache;
  private volatile Cache<String, Float> m_floatCache;
  private volatile Cache<String, Double> m_doubleCache;
  private volatile Cache<String, Byte> m_byteCache;
  private volatile Cache<String, Boolean> m_booleanCache;
  private volatile Cache<String, Date> m_dateCache;
  private volatile Cache<String, Long> m_durationCache;
  private final Map<String, Cache<String, String[]>> m_arrayCache;
  private final List<Cache> allCaches;
  private final AtomicLong m_configVersion; //indicate config version

  protected PropertiesFactory propertiesFactory;

  static {
    m_executorService = Executors.newCachedThreadPool(ApolloThreadFactory
        .create("Config", true));
  }

  public AbstractConfig() {
    m_configUtil = ApolloInjector.getInstance(ConfigUtil.class);
    m_configVersion = new AtomicLong();
    m_arrayCache = Maps.newConcurrentMap();
    allCaches = Lists.newArrayList();
    propertiesFactory = ApolloInjector.getInstance(PropertiesFactory.class);
  }

  @Override
  public void addChangeListener(ConfigChangeListener listener) {
    addChangeListener(listener, null);
  }

  @Override
  public void addChangeListener(ConfigChangeListener listener, Set<String> interestedKeys) {
    addChangeListener(listener, interestedKeys, null);
  }

  /**
   *
   * 添加监听，changeListener
   * @param listener the config change listener
   * @param interestedKeys the keys that the listener is interested in
   * @param interestedKeyPrefixes the key prefixes that the listener is interested in,
   *                              e.g. "spring." means that {@code listener} is interested in keys that starts with "spring.", such as "spring.banner", "spring.jpa", etc.
   *                              and "application" means that {@code listener} is interested in keys that starts with "application", such as "applicationName", "application.port", etc.
   *                              For more details, see {@link com.ctrip.framework.apollo.spring.annotation.ApolloConfigChangeListener#interestedKeyPrefixes()}
   *                              and {@link java.lang.String#startsWith(String)}
   *
   */
  @Override
  public void addChangeListener(ConfigChangeListener listener, Set<String> interestedKeys, Set<String> interestedKeyPrefixes) {
    // TODO: 只有当前不存在这个listener的时候，然后添加进去
    if (!m_listeners.contains(listener)) {
      m_listeners.add(listener);
      // TODO: 当感兴趣的key不为空的时候
      if (interestedKeys != null && !interestedKeys.isEmpty()) {
        // TODO: 将感兴趣的keys放到m_interestedKeys里面去
        m_interestedKeys.put(listener, Sets.newHashSet(interestedKeys));
      }
      // TODO: 前缀不为空的时候，也放进去
      if (interestedKeyPrefixes != null && !interestedKeyPrefixes.isEmpty()) {
        m_interestedKeyPrefixes.put(listener, Sets.newHashSet(interestedKeyPrefixes));
      }
    }
  }

  /**
   * 移除changeListener
   * @param listener the specific config change listener to remove
   * @return
   */
  @Override
  public boolean removeChangeListener(ConfigChangeListener listener) {
    m_interestedKeys.remove(listener);
    m_interestedKeyPrefixes.remove(listener);
    return m_listeners.remove(listener);
  }

  /**
   * TODO: 获取int属性
   * @param key          the property name
   * @param defaultValue the default value when key is not found or any error occurred
   * @return
   */
  @Override
  public Integer getIntProperty(String key, Integer defaultValue) {
    try {
      // TODO: 判断m_integerCache是否为空，如果他为空，就开始初始化它
      if (m_integerCache == null) {
        synchronized (this) {
          if (m_integerCache == null) {
            // TODO: 创建integer的缓存
            m_integerCache = newCache();
          }
        }
      }

      return getValueFromCache(key, Functions.TO_INT_FUNCTION, m_integerCache, defaultValue);
    } catch (Throwable ex) {
      Tracer.logError(new ApolloConfigException(
          String.format("getIntProperty for %s failed, return default value %d", key,
              defaultValue), ex));
    }
    return defaultValue;
  }

  @Override
  public Long getLongProperty(String key, Long defaultValue) {
    try {
      if (m_longCache == null) {
        synchronized (this) {
          if (m_longCache == null) {
            m_longCache = newCache();
          }
        }
      }

      return getValueFromCache(key, Functions.TO_LONG_FUNCTION, m_longCache, defaultValue);
    } catch (Throwable ex) {
      Tracer.logError(new ApolloConfigException(
          String.format("getLongProperty for %s failed, return default value %d", key,
              defaultValue), ex));
    }
    return defaultValue;
  }

  @Override
  public Short getShortProperty(String key, Short defaultValue) {
    try {
      if (m_shortCache == null) {
        synchronized (this) {
          if (m_shortCache == null) {
            m_shortCache = newCache();
          }
        }
      }

      return getValueFromCache(key, Functions.TO_SHORT_FUNCTION, m_shortCache, defaultValue);
    } catch (Throwable ex) {
      Tracer.logError(new ApolloConfigException(
          String.format("getShortProperty for %s failed, return default value %d", key,
              defaultValue), ex));
    }
    return defaultValue;
  }

  @Override
  public Float getFloatProperty(String key, Float defaultValue) {
    try {
      if (m_floatCache == null) {
        synchronized (this) {
          if (m_floatCache == null) {
            m_floatCache = newCache();
          }
        }
      }

      return getValueFromCache(key, Functions.TO_FLOAT_FUNCTION, m_floatCache, defaultValue);
    } catch (Throwable ex) {
      Tracer.logError(new ApolloConfigException(
          String.format("getFloatProperty for %s failed, return default value %f", key,
              defaultValue), ex));
    }
    return defaultValue;
  }

  @Override
  public Double getDoubleProperty(String key, Double defaultValue) {
    try {
      if (m_doubleCache == null) {
        synchronized (this) {
          if (m_doubleCache == null) {
            m_doubleCache = newCache();
          }
        }
      }

      return getValueFromCache(key, Functions.TO_DOUBLE_FUNCTION, m_doubleCache, defaultValue);
    } catch (Throwable ex) {
      Tracer.logError(new ApolloConfigException(
          String.format("getDoubleProperty for %s failed, return default value %f", key,
              defaultValue), ex));
    }
    return defaultValue;
  }

  @Override
  public Byte getByteProperty(String key, Byte defaultValue) {
    try {
      if (m_byteCache == null) {
        synchronized (this) {
          if (m_byteCache == null) {
            m_byteCache = newCache();
          }
        }
      }

      return getValueFromCache(key, Functions.TO_BYTE_FUNCTION, m_byteCache, defaultValue);
    } catch (Throwable ex) {
      Tracer.logError(new ApolloConfigException(
          String.format("getByteProperty for %s failed, return default value %d", key,
              defaultValue), ex));
    }
    return defaultValue;
  }

  @Override
  public Boolean getBooleanProperty(String key, Boolean defaultValue) {
    try {
      if (m_booleanCache == null) {
        synchronized (this) {
          if (m_booleanCache == null) {
            m_booleanCache = newCache();
          }
        }
      }

      return getValueFromCache(key, Functions.TO_BOOLEAN_FUNCTION, m_booleanCache, defaultValue);
    } catch (Throwable ex) {
      Tracer.logError(new ApolloConfigException(
          String.format("getBooleanProperty for %s failed, return default value %b", key,
              defaultValue), ex));
    }
    return defaultValue;
  }

  @Override
  public String[] getArrayProperty(String key, final String delimiter, String[] defaultValue) {
    try {
      if (!m_arrayCache.containsKey(delimiter)) {
        synchronized (this) {
          if (!m_arrayCache.containsKey(delimiter)) {
            m_arrayCache.put(delimiter, this.<String[]>newCache());
          }
        }
      }

      Cache<String, String[]> cache = m_arrayCache.get(delimiter);
      String[] result = cache.getIfPresent(key);

      if (result != null) {
        return result;
      }

      return getValueAndStoreToCache(key, new Function<String, String[]>() {
        @Override
        public String[] apply(String input) {
          return input.split(delimiter);
        }
      }, cache, defaultValue);
    } catch (Throwable ex) {
      Tracer.logError(new ApolloConfigException(
          String.format("getArrayProperty for %s failed, return default value", key), ex));
    }
    return defaultValue;
  }

  @Override
  public <T extends Enum<T>> T getEnumProperty(String key, Class<T> enumType, T defaultValue) {
    try {
      String value = getProperty(key, null);

      if (value != null) {
        return Enum.valueOf(enumType, value);
      }
    } catch (Throwable ex) {
      Tracer.logError(new ApolloConfigException(
          String.format("getEnumProperty for %s failed, return default value %s", key,
              defaultValue), ex));
    }

    return defaultValue;
  }

  @Override
  public Date getDateProperty(String key, Date defaultValue) {
    try {
      if (m_dateCache == null) {
        synchronized (this) {
          if (m_dateCache == null) {
            m_dateCache = newCache();
          }
        }
      }

      return getValueFromCache(key, Functions.TO_DATE_FUNCTION, m_dateCache, defaultValue);
    } catch (Throwable ex) {
      Tracer.logError(new ApolloConfigException(
          String.format("getDateProperty for %s failed, return default value %s", key,
              defaultValue), ex));
    }

    return defaultValue;
  }

  @Override
  public Date getDateProperty(String key, String format, Date defaultValue) {
    try {
      String value = getProperty(key, null);

      if (value != null) {
        return Parsers.forDate().parse(value, format);
      }
    } catch (Throwable ex) {
      Tracer.logError(new ApolloConfigException(
          String.format("getDateProperty for %s failed, return default value %s", key,
              defaultValue), ex));
    }

    return defaultValue;
  }

  @Override
  public Date getDateProperty(String key, String format, Locale locale, Date defaultValue) {
    try {
      String value = getProperty(key, null);

      if (value != null) {
        return Parsers.forDate().parse(value, format, locale);
      }
    } catch (Throwable ex) {
      Tracer.logError(new ApolloConfigException(
          String.format("getDateProperty for %s failed, return default value %s", key,
              defaultValue), ex));
    }

    return defaultValue;
  }

  @Override
  public long getDurationProperty(String key, long defaultValue) {
    try {
      if (m_durationCache == null) {
        synchronized (this) {
          if (m_durationCache == null) {
            m_durationCache = newCache();
          }
        }
      }

      return getValueFromCache(key, Functions.TO_DURATION_FUNCTION, m_durationCache, defaultValue);
    } catch (Throwable ex) {
      Tracer.logError(new ApolloConfigException(
          String.format("getDurationProperty for %s failed, return default value %d", key,
              defaultValue), ex));
    }

    return defaultValue;
  }

  @Override
  public <T> T getProperty(String key, Function<String, T> function, T defaultValue) {
    try {
      String value = getProperty(key, null);

      if (value != null) {
        return function.apply(value);
      }
    } catch (Throwable ex) {
      Tracer.logError(new ApolloConfigException(
              String.format("getProperty for %s failed, return default value %s", key,
                      defaultValue), ex));
    }

    return defaultValue;
  }

  private <T> T getValueFromCache(String key, Function<String, T> parser, Cache<String, T> cache, T defaultValue) {
    // TODO: 尝试从缓存里面取值
    T result = cache.getIfPresent(key);

    // TODO: 如果缓存里面有值，直接返回回去
    if (result != null) {
      return result;
    }

    return getValueAndStoreToCache(key, parser, cache, defaultValue);
  }

  private <T> T getValueAndStoreToCache(String key, Function<String, T> parser, Cache<String, T> cache, T defaultValue) {
    // TODO: 当前config的version
    long currentConfigVersion = m_configVersion.get();
    // TODO: 根据key去获取属性
    String value = getProperty(key, null);

    // TODO: 如果拿到了，就类型转换一下
    if (value != null) {
      T result = parser.apply(value);

      // TODO: 转为之后，结果不是null，并且版本号对的上，就把它放进缓存里面
      if (result != null) {
        synchronized (this) {
          // TODO: 如果版本号 对的上，就把它放进缓存
          if (m_configVersion.get() == currentConfigVersion) {
            cache.put(key, result);
          }
        }
        return result;
      }
    }

    return defaultValue;
  }

  /**
   * TODO: 创建cache对象，设置了存储最大大小，以及过期时间
   *
   * @param <T>
   * @return
   */
  private <T> Cache<String, T> newCache() {
    Cache<String, T> cache = CacheBuilder.newBuilder()
        .maximumSize(m_configUtil.getMaxConfigCacheSize())
        .expireAfterAccess(m_configUtil.getConfigCacheExpireTime(), m_configUtil.getConfigCacheExpireTimeUnit())
        .build();
    allCaches.add(cache);
    return cache;
  }

  /**
   * TODO: 清除所有的缓存
   * Clear config cache
   */
  protected void clearConfigCache() {
    synchronized (this) {
      // TODO: 遍历所有的缓存，然后不为空的话 就进行清除
      for (Cache c : allCaches) {
        if (c != null) {
          c.invalidateAll();
        }
      }
      // TODO: 版本号+1
      m_configVersion.incrementAndGet();
    }
  }

  /**
   * 传播配置变更事件
   * @param changeEvent
   */
  protected void fireConfigChange(final ConfigChangeEvent changeEvent) {
    // TODO: 遍历所有的listener，
    for (final ConfigChangeListener listener : m_listeners) {
      // check whether the listener is interested in this change event
      // TODO: 判断是否是它感兴趣的，如果不是，就直接continue
      if (!isConfigChangeListenerInterested(listener, changeEvent)) {
        continue;
      }
      // TODO: listener回调 这个回调是异步的
      m_executorService.submit(new Runnable() {
        @Override
        public void run() {
          String listenerName = listener.getClass().getName();
          Transaction transaction = Tracer.newTransaction("Apollo.ConfigChangeListener", listenerName);
          try {
            // TODO: 进行回调
            listener.onChange(changeEvent);
            transaction.setStatus(Transaction.SUCCESS);
          } catch (Throwable ex) {
            transaction.setStatus(ex);
            Tracer.logError(ex);
            logger.error("Failed to invoke config change listener {}", listenerName, ex);
          } finally {
            transaction.complete();
          }
        }
      });
    }
  }

  /**
   * TODO: 判断这个event是否是这个listener感兴趣的
   *
   * @param configChangeListener
   * @param configChangeEvent
   * @return
   */
  private boolean isConfigChangeListenerInterested(ConfigChangeListener configChangeListener, ConfigChangeEvent configChangeEvent) {
    // TODO: 获得这个listener感兴趣的keys
    Set<String> interestedKeys = m_interestedKeys.get(configChangeListener);
    // TODO: 获取这个listener感兴趣的前缀
    Set<String> interestedKeyPrefixes = m_interestedKeyPrefixes.get(configChangeListener);

    // TODO: 没有值，表示所有的它都感兴趣
    if ((interestedKeys == null || interestedKeys.isEmpty())
        && (interestedKeyPrefixes == null || interestedKeyPrefixes.isEmpty())) {
      return true; // no interested keys means interested in all keys
    }

    // TODO: 不为空的话，就进行遍历
    if (interestedKeys != null) {
      for (String interestedKey : interestedKeys) {
        // TODO: 变更的key是否包含感兴趣的key
        if (configChangeEvent.isChanged(interestedKey)) {
          return true;
        }
      }
    }
    // TODO: 如果感兴趣的前缀不为空
    if (interestedKeyPrefixes != null) {
      for (String prefix : interestedKeyPrefixes) {
        for (final String changedKey : configChangeEvent.changedKeys()) {
          // TODO: 如果changeKey是prefix开头的，那么直接返回true
          if (changedKey.startsWith(prefix)) {
            return true;
          }
        }
      }
    }
    // TODO: 最后返回false
    return false;
  }

  List<ConfigChange> calcPropertyChanges(String namespace, Properties previous,
                                         Properties current) {
    if (previous == null) {
      previous = propertiesFactory.getPropertiesInstance();
    }

    if (current == null) {
      current =  propertiesFactory.getPropertiesInstance();
    }

    Set<String> previousKeys = previous.stringPropertyNames();
    Set<String> currentKeys = current.stringPropertyNames();

    Set<String> commonKeys = Sets.intersection(previousKeys, currentKeys);
    Set<String> newKeys = Sets.difference(currentKeys, commonKeys);
    Set<String> removedKeys = Sets.difference(previousKeys, commonKeys);

    List<ConfigChange> changes = Lists.newArrayList();

    for (String newKey : newKeys) {
      changes.add(new ConfigChange(namespace, newKey, null, current.getProperty(newKey),
          PropertyChangeType.ADDED));
    }

    for (String removedKey : removedKeys) {
      changes.add(new ConfigChange(namespace, removedKey, previous.getProperty(removedKey), null,
          PropertyChangeType.DELETED));
    }

    for (String commonKey : commonKeys) {
      String previousValue = previous.getProperty(commonKey);
      String currentValue = current.getProperty(commonKey);
      if (Objects.equal(previousValue, currentValue)) {
        continue;
      }
      changes.add(new ConfigChange(namespace, commonKey, previousValue, currentValue,
          PropertyChangeType.MODIFIED));
    }

    return changes;
  }
}
