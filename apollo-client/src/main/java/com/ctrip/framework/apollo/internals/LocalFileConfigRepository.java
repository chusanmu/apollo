package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.enums.ConfigSourceType;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.utils.ClassLoaderUtil;
import com.ctrip.framework.apollo.exceptions.ApolloConfigException;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.ctrip.framework.apollo.util.ExceptionUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

/**
 * TODO:本地文件仓库
 * @author Jason Song(song_s@ctrip.com)
 */
public class LocalFileConfigRepository extends AbstractConfigRepository
    implements RepositoryChangeListener {
  private static final Logger logger = LoggerFactory.getLogger(LocalFileConfigRepository.class);
  /**
   * TODO:apollo同步至本地的路径
   */
  private static final String CONFIG_DIR = "/config-cache";
  private final String m_namespace;
  private File m_baseDir;
  private final ConfigUtil m_configUtil;
  private volatile Properties m_fileProperties;
  private volatile ConfigRepository m_upstream;

  private volatile ConfigSourceType m_sourceType = ConfigSourceType.LOCAL;

  /**
   * Constructor.
   *
   * @param namespace the namespace
   */
  public LocalFileConfigRepository(String namespace) {
    this(namespace, null);
  }

  /**
   * 指定namespace 以及上游 configRepository
   * @param namespace
   * @param upstream
   */
  public LocalFileConfigRepository(String namespace, ConfigRepository upstream) {
    m_namespace = namespace;
    m_configUtil = ApolloInjector.getInstance(ConfigUtil.class);
    // TODO: 指定缓存路径
    this.setLocalCacheDir(findLocalCacheDir(), false);
    // TODO: 指定上游repository
    this.setUpstreamRepository(upstream);
    this.trySync();
  }

  void setLocalCacheDir(File baseDir, boolean syncImmediately) {
    m_baseDir = baseDir;
    this.checkLocalConfigCacheDir(m_baseDir);
    if (syncImmediately) {
      this.trySync();
    }
  }

  /**
   * 返回本地缓存的配置文件
   * @return
   */
  private File findLocalCacheDir() {
    try {
      String defaultCacheDir = m_configUtil.getDefaultLocalCacheDir();
      Path path = Paths.get(defaultCacheDir);
      if (!Files.exists(path)) {
        Files.createDirectories(path);
      }
      if (Files.exists(path) && Files.isWritable(path)) {
        return new File(defaultCacheDir, CONFIG_DIR);
      }
    } catch (Throwable ex) {
      //ignore
    }

    return new File(ClassLoaderUtil.getClassPath(), CONFIG_DIR);
  }

  /**
   * 获取配置
   *
   * @return
   */
  @Override
  public Properties getConfig() {
    // TODO: 如果 m_fileProperties 为空，就去上游同步
    if (m_fileProperties == null) {
      sync();
    }
    Properties result = propertiesFactory.getPropertiesInstance();
    result.putAll(m_fileProperties);
    return result;
  }

  @Override
  public void setUpstreamRepository(ConfigRepository upstreamConfigRepository) {
    if (upstreamConfigRepository == null) {
      return;
    }
    //clear previous listener
    // TODO: 移除掉之前的listener
    if (m_upstream != null) {
      m_upstream.removeChangeListener(this);
    }
    m_upstream = upstreamConfigRepository;
    // TODO: 尝试从上游repository进行更新
    trySyncFromUpstream();
    // TODO: 重新添加监听
    upstreamConfigRepository.addChangeListener(this);
  }

  @Override
  public ConfigSourceType getSourceType() {
    return m_sourceType;
  }

  /**
   * TODO: 当远程仓库发生变更的时候
   *
   * @param namespace the namespace of this repository change
   * @param newProperties the properties after change
   */
  @Override
  public void onRepositoryChange(String namespace, Properties newProperties) {
    // TODO: 如果两个配置文件，远端的和本地缓存的相同，直接就返回了
    if (newProperties.equals(m_fileProperties)) {
      return;
    }
    // TODO: 创建一个properties
    Properties newFileProperties = propertiesFactory.getPropertiesInstance();
    // TODO: 把最新的properties加到newFileProperties中
    newFileProperties.putAll(newProperties);
    // TODO: 进行更新,如果有变更，会将最新的持久化到本地缓存中
    updateFileProperties(newFileProperties, m_upstream.getSourceType());
    // TODO: 接着传播仓库变更事件
    this.fireRepositoryChange(namespace, newProperties);
  }

  @Override
  protected void sync() {
    //sync with upstream immediately
    // TODO: 立即从上游进行同步
    boolean syncFromUpstreamResultSuccess = trySyncFromUpstream();

    // TODO: 同步成功了，直接就返回
    if (syncFromUpstreamResultSuccess) {
      return;
    }

    Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "syncLocalConfig");
    Throwable exception = null;
    try {
      transaction.addData("Basedir", m_baseDir.getAbsolutePath());
      // TODO: 从本地加载缓存的配置文件
      m_fileProperties = this.loadFromLocalCacheFile(m_baseDir, m_namespace);
      // TODO: 指定文件类型为本地加载
      m_sourceType = ConfigSourceType.LOCAL;
      transaction.setStatus(Transaction.SUCCESS);
    } catch (Throwable ex) {
      Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(ex));
      transaction.setStatus(ex);
      exception = ex;
      //ignore
    } finally {
      transaction.complete();
    }

    // TODO: 没有加载到，标记一下source为none，然后就直接抛出异常了.
    if (m_fileProperties == null) {
      m_sourceType = ConfigSourceType.NONE;
      throw new ApolloConfigException(
          "Load config from local config failed!", exception);
    }
  }

  private boolean trySyncFromUpstream() {
    // TODO: 如果upStream为空，就直接返回false了
    if (m_upstream == null) {
      return false;
    }
    try {
      // TODO: 更新本地缓存配置
      updateFileProperties(m_upstream.getConfig(), m_upstream.getSourceType());
      return true;
    } catch (Throwable ex) {
      Tracer.logError(ex);
      logger
          .warn("Sync config from upstream repository {} failed, reason: {}", m_upstream.getClass(),
              ExceptionUtil.getDetailMessage(ex));
    }
    return false;
  }

  private synchronized void updateFileProperties(Properties newProperties, ConfigSourceType sourceType) {
    this.m_sourceType = sourceType;
    // TODO: fileProperties 看看 配置文件是否发生了变化
    if (newProperties.equals(m_fileProperties)) {
      return;
    }
    // TODO: 更新m_fileProperties
    this.m_fileProperties = newProperties;
    // TODO: 持久化到本地文件缓存中
    persistLocalCacheFile(m_baseDir, m_namespace);
  }

  /**
   * TODO: 加载本地缓存的配置文件
   *
   * @param baseDir
   * @param namespace
   * @return
   * @throws IOException
   */
  private Properties loadFromLocalCacheFile(File baseDir, String namespace) throws IOException {
    Preconditions.checkNotNull(baseDir, "Basedir cannot be null");
    // TODO: 获取本地的File
    File file = assembleLocalCacheFile(baseDir, namespace);
    Properties properties = null;

    // TODO: 是一个文件，并且可以读
    if (file.isFile() && file.canRead()) {
      InputStream in = null;

      try {
        // TODO: 创建input输入流
        in = new FileInputStream(file);
        // TODO: 创建一个Properties，然后把输入放进去
        properties = propertiesFactory.getPropertiesInstance();
        // TODO: 进行加载
        properties.load(in);
        logger.debug("Loading local config file {} successfully!", file.getAbsolutePath());
      } catch (IOException ex) {
        Tracer.logError(ex);
        throw new ApolloConfigException(String
            .format("Loading config from local cache file %s failed", file.getAbsolutePath()), ex);
      } finally {
        try {
          // TODO: 最后输入流不为空，就直接进行关闭
          if (in != null) {
            in.close();
          }
        } catch (IOException ex) {
          // ignore
        }
      }
    } else {
      throw new ApolloConfigException(
          String.format("Cannot read from local cache file %s", file.getAbsolutePath()));
    }

    return properties;
  }

  void persistLocalCacheFile(File baseDir, String namespace) {
    // TODO: baseDir为空就直接返回掉了
    if (baseDir == null) {
      return;
    }
    // TODO: 装配本地缓存文件
    File file = assembleLocalCacheFile(baseDir, namespace);

    OutputStream out = null;

    Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "persistLocalConfigFile");
    transaction.addData("LocalConfigFile", file.getAbsolutePath());
    try {
      // TODO: 直接将property写到输出流中
      out = new FileOutputStream(file);
      m_fileProperties.store(out, "Persisted by DefaultConfig");
      transaction.setStatus(Transaction.SUCCESS);
    } catch (IOException ex) {
      ApolloConfigException exception =
          new ApolloConfigException(
              String.format("Persist local cache file %s failed", file.getAbsolutePath()), ex);
      Tracer.logError(exception);
      transaction.setStatus(exception);
      logger.warn("Persist local cache file {} failed, reason: {}.", file.getAbsolutePath(),
          ExceptionUtil.getDetailMessage(ex));
    } finally {
      // TODO: 关闭输出流
      if (out != null) {
        try {
          out.close();
        } catch (IOException ex) {
          //ignore
        }
      }
      transaction.complete();
    }
  }

  private void checkLocalConfigCacheDir(File baseDir) {
    if (baseDir.exists()) {
      return;
    }
    Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "createLocalConfigDir");
    transaction.addData("BaseDir", baseDir.getAbsolutePath());
    try {
      // TODO: 创建本地目录
      Files.createDirectory(baseDir.toPath());
      transaction.setStatus(Transaction.SUCCESS);
    } catch (IOException ex) {
      ApolloConfigException exception =
          new ApolloConfigException(
              String.format("Create local config directory %s failed", baseDir.getAbsolutePath()),
              ex);
      Tracer.logError(exception);
      transaction.setStatus(exception);
      logger.warn(
          "Unable to create local config cache directory {}, reason: {}. Will not able to cache config file.",
          baseDir.getAbsolutePath(), ExceptionUtil.getDetailMessage(ex));
    } finally {
      transaction.complete();
    }
  }

  /**
   * 生成fileName
   * @param baseDir
   * @param namespace
   * @return
   */
  File assembleLocalCacheFile(File baseDir, String namespace) {
    // TODO: 生成本地缓存文件的名字，以+号分隔，进行拼接 生成fileName
    String fileName =
        String.format("%s.properties", Joiner.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR)
            .join(m_configUtil.getAppId(), m_configUtil.getCluster(), namespace));
    return new File(baseDir, fileName);
  }
}
