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
package org.apache.ibatis.mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

/**
 * 描述<select/update/insert/delete/>或@Select、@update等注解配置的SQL信息
 * @author Clinton Begin
 */
public final class MappedStatement {

  private String resource;
  private Configuration configuration;

  /**
   * 在命名空间中唯一的标识符，可以被用来引用这条语句。
   */
  private String id;

  /**
   * 这是一个给驱动的建议值，尝试让驱动程序每次批量返回的结果行数等于这个设置值。 默认值为未设置（unset）（依赖驱动）。
   * 如果需要查询很多条数据，可以使用分页实现，但是这样效率比较低，需要查询很多次；也可以一次性查出来，但是需要很大内存，一般机器不能承受
   * 这里可以通过设置fetchSize来提高效率。在服务端一次查询，然后客户端每次拉取fetchSize条数据（https://www.cnblogs.com/baimingqian/p/11761942.html）
   */
  private Integer fetchSize;

  /**
   * SQL执行的过期时间
   */
  private Integer timeout;

  /**
   * 参数可选值为STATEMENT、PREPARED或CALLABLE，
   * 这会让MyBatis分别使用Statement、PreparedStatement或CallableStatement与数据库交互，
   * 默认值为PREPARED。
   */
  private StatementType statementType;

  /**
   * 参数可选值为:
   * FORWARD_ONLY（结果集只能向下遍历，不能使用ResultSet#absolute/afterLast/beforeFirst/first/last/previous/relative等方法）
   * SCROLL_SENSITIVE（返回可滚动的结果集，当数据库变化时，当前结果集同步改变.mysql不支持该类型）
   * SCROLL_INSENSITIVE（结果集的游标可以上下移动，当数据库变化时，当前结果集不变）
   * 用于设置ResultSet对象的特征，具体可参考第2章JDBC规范的相关内容。
   * 默认未设置，由JDBC驱动决定。
   */
  private ResultSetType resultSetType;

  /**
   * 解析<select/update/insert/delete/>得到SQLSource对象
   */
  private SqlSource sqlSource;

  /**
   * 二级缓存实例
   */
  private Cache cache;

  /**
   * 用于引用外部 parameterMap 的属性，目前已被废弃。请使用行内参数映射和 parameterType 属性。
   */
  private ParameterMap parameterMap;

  /**
   * mapper resultMap引用的结果映射
   */
  private List<ResultMap> resultMaps;

  /**
   * 对应Mapper 标签的 flushCache属性，默认情况下：
   * false：MappedStatement的SQLCommandType是Select
   * true：update
   * 将其设置为 true 后，只要语句被调用，都会导致本地缓存和二级缓存被清空，默认值：false。
   */
  private boolean flushCacheRequired;

  /**
   * 是否使用二级缓存
   * 将其设置为 true 后，将会导致本条语句的结果被二级缓存缓存起来，默认值：对 select 元素为 true。
   */
  private boolean useCache;

  /**
   * 这个设置仅针对嵌套结果 select语句适用，如果为true，就是假定嵌套结果包含在一起或分组在一起，
   * 这样的话，当返回一个主结果行的时候，就不会发生对前面结果集引用的情况。这就使得在获取嵌套结果集的时候不至于导致内存不够用，默认值为false。
   */
  private boolean resultOrdered;
  private SqlCommandType sqlCommandType;

  /**
   * 主键生成策略，默认是Jdbc3KeyGenerator,即数据库自增主键
   * 当配置了<selectKey>时，使用SelectKeyGenerator生成主键。
   */
  private KeyGenerator keyGenerator;

  /**
   * 该属性仅对<update>和<insert>标签有用，
   * 用于将数据库自增主键或者<insert>标签中<selectKey>标签返回的值填充到实体的属性中，如果有多个属性，则使用逗号分隔。
   */
  private String[] keyProperties;

  private String[] keyColumns;

  /**
   * <select>标签中通过resultMap属性指定ResultMap是不是嵌套的ResultMap。
   */
  private boolean hasNestedResultMaps;
  private String databaseId;

  /**
   * 用于输出日志
   */
  private Log statementLog;

  /**
   * 指定LanguageDriver实现，MyBatis中的LanguageDriver用于解析<select|update|insert|delete>标签中的SQL语句，生成SqlSource对象。
   */
  private LanguageDriver lang;
  private String[] resultSets;

  MappedStatement() {
    // constructor disabled
  }

  public static class Builder {
    private MappedStatement mappedStatement = new MappedStatement();

    public Builder(Configuration configuration, String id, SqlSource sqlSource, SqlCommandType sqlCommandType) {
      mappedStatement.configuration = configuration;
      mappedStatement.id = id;
      mappedStatement.sqlSource = sqlSource;
      mappedStatement.statementType = StatementType.PREPARED;
      mappedStatement.resultSetType = ResultSetType.DEFAULT;
      mappedStatement.parameterMap = new ParameterMap.Builder(configuration, "defaultParameterMap", null, new ArrayList<>()).build();
      mappedStatement.resultMaps = new ArrayList<>();
      mappedStatement.sqlCommandType = sqlCommandType;
      mappedStatement.keyGenerator =
        configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType)
          ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
      String logId = id;
      if (configuration.getLogPrefix() != null) {
        logId = configuration.getLogPrefix() + id;
      }
      mappedStatement.statementLog = LogFactory.getLog(logId);
      mappedStatement.lang = configuration.getDefaultScriptingLanguageInstance();
    }

    public Builder resource(String resource) {
      mappedStatement.resource = resource;
      return this;
    }

    public String id() {
      return mappedStatement.id;
    }

    public Builder parameterMap(ParameterMap parameterMap) {
      mappedStatement.parameterMap = parameterMap;
      return this;
    }

    public Builder resultMaps(List<ResultMap> resultMaps) {
      mappedStatement.resultMaps = resultMaps;
      for (ResultMap resultMap : resultMaps) {
        mappedStatement.hasNestedResultMaps = mappedStatement.hasNestedResultMaps || resultMap.hasNestedResultMaps();
      }
      return this;
    }

    public Builder fetchSize(Integer fetchSize) {
      mappedStatement.fetchSize = fetchSize;
      return this;
    }

    public Builder timeout(Integer timeout) {
      mappedStatement.timeout = timeout;
      return this;
    }

    public Builder statementType(StatementType statementType) {
      mappedStatement.statementType = statementType;
      return this;
    }

    public Builder resultSetType(ResultSetType resultSetType) {
      mappedStatement.resultSetType = resultSetType == null ? ResultSetType.DEFAULT : resultSetType;
      return this;
    }

    public Builder cache(Cache cache) {
      mappedStatement.cache = cache;
      return this;
    }

    public Builder flushCacheRequired(boolean flushCacheRequired) {
      mappedStatement.flushCacheRequired = flushCacheRequired;
      return this;
    }

    /**
     * @param useCache in Default case，true：select，false：!select
     * @return
     */
    public Builder useCache(boolean useCache) {
      mappedStatement.useCache = useCache;
      return this;
    }

    public Builder resultOrdered(boolean resultOrdered) {
      mappedStatement.resultOrdered = resultOrdered;
      return this;
    }

    public Builder keyGenerator(KeyGenerator keyGenerator) {
      mappedStatement.keyGenerator = keyGenerator;
      return this;
    }

    public Builder keyProperty(String keyProperty) {
      mappedStatement.keyProperties = delimitedStringToArray(keyProperty);
      return this;
    }

    public Builder keyColumn(String keyColumn) {
      mappedStatement.keyColumns = delimitedStringToArray(keyColumn);
      return this;
    }

    public Builder databaseId(String databaseId) {
      mappedStatement.databaseId = databaseId;
      return this;
    }

    public Builder lang(LanguageDriver driver) {
      mappedStatement.lang = driver;
      return this;
    }

    public Builder resultSets(String resultSet) {
      mappedStatement.resultSets = delimitedStringToArray(resultSet);
      return this;
    }

    /**
     * Resul sets.
     *
     * @param resultSet
     *          the result set
     * @return the builder
     * @deprecated Use {@link #resultSets}
     */
    @Deprecated
    public Builder resulSets(String resultSet) {
      mappedStatement.resultSets = delimitedStringToArray(resultSet);
      return this;
    }

    public MappedStatement build() {
      assert mappedStatement.configuration != null;
      assert mappedStatement.id != null;
      assert mappedStatement.sqlSource != null;
      assert mappedStatement.lang != null;
      mappedStatement.resultMaps = Collections.unmodifiableList(mappedStatement.resultMaps);
      return mappedStatement;
    }
  }

  public KeyGenerator getKeyGenerator() {
    return keyGenerator;
  }

  public SqlCommandType getSqlCommandType() {
    return sqlCommandType;
  }

  public String getResource() {
    return resource;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  public String getId() {
    return id;
  }

  public boolean hasNestedResultMaps() {
    return hasNestedResultMaps;
  }

  public Integer getFetchSize() {
    return fetchSize;
  }

  public Integer getTimeout() {
    return timeout;
  }

  public StatementType getStatementType() {
    return statementType;
  }

  public ResultSetType getResultSetType() {
    return resultSetType;
  }

  public SqlSource getSqlSource() {
    return sqlSource;
  }

  public ParameterMap getParameterMap() {
    return parameterMap;
  }

  public List<ResultMap> getResultMaps() {
    return resultMaps;
  }

  public Cache getCache() {
    return cache;
  }

  public boolean isFlushCacheRequired() {
    return flushCacheRequired;
  }

  public boolean isUseCache() {
    return useCache;
  }

  public boolean isResultOrdered() {
    return resultOrdered;
  }

  public String getDatabaseId() {
    return databaseId;
  }

  public String[] getKeyProperties() {
    return keyProperties;
  }

  public String[] getKeyColumns() {
    return keyColumns;
  }

  public Log getStatementLog() {
    return statementLog;
  }

  public LanguageDriver getLang() {
    return lang;
  }

  public String[] getResultSets() {
    return resultSets;
  }

  /**
   * Gets the result sets.
   *
   * @return the result sets
   * @deprecated Use {@link #getResultSets()}
   */
  @Deprecated
  public String[] getResulSets() {
    return resultSets;
  }

  public BoundSql getBoundSql(Object parameterObject) {
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    if (parameterMappings == null || parameterMappings.isEmpty()) {
      boundSql = new BoundSql(configuration, boundSql.getSql(), parameterMap.getParameterMappings(), parameterObject);
    }

    // check for nested result maps in parameter mappings (issue #30)
    for (ParameterMapping pm : boundSql.getParameterMappings()) {
      String rmId = pm.getResultMapId();
      if (rmId != null) {
        ResultMap rm = configuration.getResultMap(rmId);
        if (rm != null) {
          hasNestedResultMaps |= rm.hasNestedResultMaps();
        }
      }
    }

    return boundSql;
  }

  private static String[] delimitedStringToArray(String in) {
    if (in == null || in.trim().length() == 0) {
      return null;
    } else {
      return in.split(",");
    }
  }

}
