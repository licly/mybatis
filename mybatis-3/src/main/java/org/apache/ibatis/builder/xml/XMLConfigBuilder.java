/**
 *    Copyright 2009-2020 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * 解析XML配置
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  private boolean parsed;
  private final XPathParser parser;
  /**
   * mybatis可以配置多种环境，可以通过该变量控制使用哪种环境
   * <environments default="development">
   *   <environment id="development">
   *     事务管理器的配置
   *     在 MyBatis 中有两种类型的事务管理器（也就是 type="[JDBC|MANAGED]"）：
   *     JDBC – 这个配置直接使用了 JDBC 的提交和回滚设施，它依赖从数据源获得的连接来管理事务作用域。
   *     MANAGED – 这个配置几乎没做什么。它从不提交或回滚一个连接，而是让容器来管理事务的整个生命周期（比如 JEE 应用服务器的上下文）
   *     如果正在使用 Spring + MyBatis，则没有必要配置事务管理器，因为 Spring 模块会使用自带的管理器来覆盖前面的配置。
   *     <transactionManager type="JDBC">
   *       <property name="..." value="..."/>
   *     </transactionManager>
   *     <dataSource type="POOLED">
   *       <property name="driver" value="${driver}"/>
   *       <property name="url" value="${url}"/>
   *       <property name="username" value="${username}"/>
   *       <property name="password" value="${password}"/>
   *     </dataSource>
   *   </environment>
   * </environments>
   * 对应<environment>标签的id属性，默认是development
   */
  private String environment;
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  public Configuration parse() {
    // 如果已经解析过，抛出BuilderException
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    // 标志为已解析
    parsed = true;
    // 解析Mybatis配置文件，mybatis所有配置解析都是从这里开始的，包括Mapper
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  /**
   * 解析mybatis configuration配置文件
   * @param root
   */
  private void parseConfiguration(XNode root) {
    try {
      // issue #117 read properties first
      // 先解析<properties>标签，因为下面的标签解析可能引用到properties中的属性
      propertiesElement(root.evalNode("properties"));
      // 将<settings>标签解析为Properties
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      loadCustomVfs(settings);
      // 解析配置的日志实现
      loadCustomLogImpl(settings);

      // 解析<typeAliases>标签
      typeAliasesElement(root.evalNode("typeAliases"));
      // 解析<plugins>标签
      pluginElement(root.evalNode("plugins"));
      // 解析<objectFactory>标签，可以通过配置该属性，使用自己的对象工厂
      objectFactoryElement(root.evalNode("objectFactory"));

      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      // 解析reflector标签
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      // 设置settings属性
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      // 解析<environments>
      environmentsElement(root.evalNode("environments"));
      // 解析<databaseIdProvider>
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      // 解析TypeHandlers标签
      typeHandlerElement(root.evalNode("typeHandlers"));
      // 解析mapper标签
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }

    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    // 校验是否存在不正确setting属性
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  /**
   * 解析自定义别名
   * @param parent <typeAliases></typeAliases>标签
   */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        // <package name="xxx"> 标签处理，每一个xxx包下的bean都会将类名首字母小写作为别名
        if ("package".equals(child.getName())) {
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);

        } else {
          // <typeAlias alias="xxx" type="xxx.xxx"/>
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  /**
   * 解析插件
   * <plugins>
   *   <plugin interceptor="org.mybatis.example.ExamplePlugin">
   *     <property name="someProperty" value="100"/>
   *   </plugin>
   * </plugins>
   * @param parent <plugins></plugins>
   */
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        String interceptor = child.getStringAttribute("interceptor");
        Properties properties = child.getChildrenAsProperties();
        // 创建插件实例
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance();
        // 设置属性
        interceptorInstance.setProperties(properties);
        // 添加到Configuration
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  /**
   * 解析自定义ObjectFactory
   * <objectFactory type="org.mybatis.example.ExampleObjectFactory">
   *   <property name="someProperty" value="100"/>
   * </objectFactory>
   * @param context <objectFactory>
   */
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setReflectorFactory(factory);
    }
  }

  /**
   * 解析properties标签
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      Properties defaults = context.getChildrenAsProperties();
      // 可以引入外部的配置文件，resource属性是外部配置文件的地址
      String resource = context.getStringAttribute("resource");
      // 同时也可以通过URL引入
      String url = context.getStringAttribute("url");
      // resource属性和url属性只能使用一个，不能都配置
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }

      // 把外部配置添加到properties
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }

      // 也可以在应用启动时将Properties参数传进来，这里进行添加
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }

      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  /**
   * 设置Settings属性
   * @param props
   */
  private void settingsElement(Properties props) {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
    configuration.setShrinkWhitespacesInSql(booleanValueOf(props.getProperty("shrinkWhitespacesInSql"), false));
  }

  /**
   * 解析设置环境
   * <environments default="development">
   *   <environment id="development">
   *     <transactionManager type="JDBC">
   *       <property name="..." value="..."/>
   *     </transactionManager>
   *     <dataSource type="POOLED">
   *       <property name="driver" value="${driver}"/>
   *       <property name="url" value="${url}"/>
   *       <property name="username" value="${username}"/>
   *       <property name="password" value="${password}"/>
   *     </dataSource>
   *   </environment>
   * </environments>
   * @param context <environments></environments>
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        // 默认使用default环境
        environment = context.getStringAttribute("default");
      }

      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        // 查找指定的环境
        if (isSpecifiedEnvironment(id)) {
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          DataSource dataSource = dsFactory.getDataSource();
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  /**
   * Mybatis可以根据不同的数据库厂商执行不同的语句，依赖于Mapper映射语句的databaseId属性
   * 解析<databaseIdProvider>标签
   * <databaseIdProvider type="DB_VENDOR">
   *   <property name="SQL Server" value="sqlserver"/>
   *   <property name="DB2" value="db2"/>
   *   <property name="Oracle" value="oracle" />
   * </databaseIdProvider>
   * @param context <databaseIdProvider></databaseIdProvider>
   * @throws Exception
   */
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      if ("VENDOR".equals(type)) {
        type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor().newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      // 事务管理器类型 type="[JDBC|MANAGED]" 见官方文档
      String type = context.getStringAttribute("type");
      // DataSource 属性
      Properties props = context.getChildrenAsProperties();
      // 从别名注册中心获取TransactionFactory类型，然后实例化
      TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      // 数据源类型 type="[UNPOOLED|POOLED|JNDI]"
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  /**
   * 解析TypeHandler
   * <typeHandlers>
   *   <typeHandler handler="org.mybatis.example.ExampleTypeHandler"/>
   * </typeHandlers>
   * @param parent
   */
  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        // 处理整个包下的TypeHandler
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * 解析<mappers>标签
   * @param parent
   * @throws Exception
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent == null) {
      return;
    }

    for (XNode child : parent.getChildren()) {
      // 通过package标签指定mapper接口包名
      // <mappers>
      //   <package name="org.mybatis.builder"/>
      // </mappers>
      if ("package".equals(child.getName())) {
        // 得到指定的包名
        String mapperPackage = child.getStringAttribute("name");
        configuration.addMappers(mapperPackage);
      }

      // 指定文件
      else {
        // 使用Mapper文件相对路径定位mapper文件
        // e.g. <mapper resource="org/mybatis/builder/PostMapper.xml"/>
        String resource = child.getStringAttribute("resource");
        // 使用Mapper文件绝对路径
        // e.g. <mapper url="file:///var/mappers/PostMapper.xml"/>
        String url = child.getStringAttribute("url");
        // 使用mapper接口，class属性是接口全限定名
        // e.g. <mapper class="org.mybatis.builder.AuthorMapper"/>
        String mapperClass = child.getStringAttribute("class");

        if (resource != null && url == null && mapperClass == null) {
          // 使用相对路径
          ErrorContext.instance().resource(resource);
          InputStream inputStream = Resources.getResourceAsStream(resource);
          XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
          mapperParser.parse();

        } else if (resource == null && url != null && mapperClass == null) {
          // 使用绝对路径
          ErrorContext.instance().resource(url);
          InputStream inputStream = Resources.getUrlAsStream(url);
          XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
          mapperParser.parse();

        } else if (resource == null && url == null && mapperClass != null) {
          // 使用接口
          Class<?> mapperInterface = Resources.classForName(mapperClass);
          configuration.addMapper(mapperInterface);
        } else {
          // 不能在一个<mapper>标签同时定义两种及以上mapper映射方式
          throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
        }
      }
    }
  }

  /**
   * 是否是指定的环境
   * @param id
   * @return
   */
  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
