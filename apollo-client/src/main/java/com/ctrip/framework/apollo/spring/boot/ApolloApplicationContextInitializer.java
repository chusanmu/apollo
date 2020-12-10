package com.ctrip.framework.apollo.spring.boot;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.spring.config.ConfigPropertySourceFactory;
import com.ctrip.framework.apollo.spring.config.PropertySourcesConstants;
import com.ctrip.framework.apollo.spring.util.SpringInjector;
import com.ctrip.framework.apollo.util.factory.PropertiesFactory;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Initialize apollo system properties and inject the Apollo config in Spring Boot bootstrap phase
 *
 * <p>Configuration example:</p>
 * <pre class="code">
 *   # set app.id
 *   app.id = 100004458
 *   # enable apollo bootstrap config and inject 'application' namespace in bootstrap phase
 *   apollo.bootstrap.enabled = true
 * </pre>
 *
 * or
 *
 * <pre class="code">
 *   # set app.id
 *   app.id = 100004458
 *   # enable apollo bootstrap config
 *   apollo.bootstrap.enabled = true
 *   # will inject 'application' and 'FX.apollo' namespaces in bootstrap phase
 *   apollo.bootstrap.namespaces = application,FX.apollo
 * </pre>
 *
 *
 * If you want to load Apollo configurations even before Logging System Initialization Phase,
 *  add
 * <pre class="code">
 *   # set apollo.bootstrap.eagerLoad.enabled
 *   apollo.bootstrap.eagerLoad.enabled = true
 * </pre>
 *
 *  This would be very helpful when your logging configurations is set by Apollo.
 *
 *  for example, you have defined logback-spring.xml in your project, and you want to inject some attributes into logback-spring.xml.
 *
 */
public class ApolloApplicationContextInitializer implements
    ApplicationContextInitializer<ConfigurableApplicationContext> , EnvironmentPostProcessor, Ordered {
  public static final int DEFAULT_ORDER = 0;

  private static final Logger logger = LoggerFactory.getLogger(ApolloApplicationContextInitializer.class);
  private static final Splitter NAMESPACE_SPLITTER = Splitter.on(",").omitEmptyStrings().trimResults();
  private static final String[] APOLLO_SYSTEM_PROPERTIES = {"app.id", ConfigConsts.APOLLO_CLUSTER_KEY,
      "apollo.cacheDir", "apollo.accesskey.secret", ConfigConsts.APOLLO_META_KEY, PropertiesFactory.APOLLO_PROPERTY_ORDER_ENABLE};

  private final ConfigPropertySourceFactory configPropertySourceFactory = SpringInjector
      .getInstance(ConfigPropertySourceFactory.class);

  private int order = DEFAULT_ORDER;

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    ConfigurableEnvironment environment = context.getEnvironment();

    // TODO: 判断你有没有启用apollo呀，如果没启用直接return了，默认false 不启用
    if (!environment.getProperty(PropertySourcesConstants.APOLLO_BOOTSTRAP_ENABLED, Boolean.class, false)) {
      logger.debug("Apollo bootstrap config is not enabled for context {}, see property: ${{}}", context, PropertySourcesConstants.APOLLO_BOOTSTRAP_ENABLED);
      return;
    }
    logger.debug("Apollo bootstrap config is enabled for context {}", context);

    // TODO: 如果启用了，这时候开始初始化
    initialize(environment);
  }


  /**
   * Initialize Apollo Configurations Just after environment is ready.
   *
   * @param environment
   */
  protected void initialize(ConfigurableEnvironment environment) {

    // TODO: 判断环境中 是否有这个 ApolloBootstrapPropertySources 属性了，如果已经有了，则说明初始化过了，就直接return掉
    if (environment.getPropertySources().contains(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME)) {
      //already initialized
      return;
    }

    // TODO: 获取namespaces, 默认是application, 可以配置多个，然后以逗号分隔
    String namespaces = environment.getProperty(PropertySourcesConstants.APOLLO_BOOTSTRAP_NAMESPACES, ConfigConsts.NAMESPACE_APPLICATION);
    logger.debug("Apollo bootstrap namespaces: {}", namespaces);
    // TODO: 获取namespaces列表
    List<String> namespaceList = NAMESPACE_SPLITTER.splitToList(namespaces);


    CompositePropertySource composite = new CompositePropertySource(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME);
    // TODO: 把从不同的namespace获取
    for (String namespace : namespaceList) {
      Config config = ConfigService.getConfig(namespace);

      composite.addPropertySource(configPropertySourceFactory.getConfigPropertySource(namespace, config));
    }

    // TODO: 将composite设置到环境中去
    environment.getPropertySources().addFirst(composite);
  }

  /**
   * To fill system properties from environment config
   */
  void initializeSystemProperty(ConfigurableEnvironment environment) {
    // TODO: 填充apollo的一些属性 例如app.id apollo.meta等
    for (String propertyName : APOLLO_SYSTEM_PROPERTIES) {
      fillSystemPropertyFromEnvironment(environment, propertyName);
    }
  }

  private void fillSystemPropertyFromEnvironment(ConfigurableEnvironment environment, String propertyName) {
    // TODO: 如果这个值在系统里面已经有了，那直接返回就行了，否则去填充至System中
    if (System.getProperty(propertyName) != null) {
      return;
    }

    // TODO: 从环境中把这个属性值拿到
    String propertyValue = environment.getProperty(propertyName);

    if (Strings.isNullOrEmpty(propertyValue)) {
      return;
    }
    // TODO: 设置到System中
    System.setProperty(propertyName, propertyValue);
  }

  /**
   *
   * In order to load Apollo configurations as early as even before Spring loading logging system phase,
   * this EnvironmentPostProcessor can be called Just After ConfigFileApplicationListener has succeeded.
   *
   * <br />
   * The processing sequence would be like this: <br />
   * Load Bootstrap properties and application properties -----> load Apollo configuration properties ----> Initialize Logging systems
   *
   * @param configurableEnvironment
   * @param springApplication
   */
  @Override
  public void postProcessEnvironment(ConfigurableEnvironment configurableEnvironment, SpringApplication springApplication) {

    // should always initialize system properties like app.id in the first place
    // TODO: 填充系统属性
    initializeSystemProperty(configurableEnvironment);

    Boolean eagerLoadEnabled = configurableEnvironment.getProperty(PropertySourcesConstants.APOLLO_BOOTSTRAP_EAGER_LOAD_ENABLED, Boolean.class, false);

    //EnvironmentPostProcessor should not be triggered if you don't want Apollo Loading before Logging System Initialization
    // TODO: 是否在日志系统加载之前进行加载
    if (!eagerLoadEnabled) {
      return;
    }

    // TODO: 判断是否开启了apollo 如果开启了，然后进行初始化
    Boolean bootstrapEnabled = configurableEnvironment.getProperty(PropertySourcesConstants.APOLLO_BOOTSTRAP_ENABLED, Boolean.class, false);

    if (bootstrapEnabled) {
      initialize(configurableEnvironment);
    }

  }

  /**
   * @since 1.3.0
   */
  @Override
  public int getOrder() {
    return order;
  }

  /**
   * @since 1.3.0
   */
  public void setOrder(int order) {
    this.order = order;
  }
}
