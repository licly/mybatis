/**
 *    Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.scripting.xmltags;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * 用于描述Mapper XML文件中配置的SQL资源信息，这些这些SQL通常含有SQL动态标签和${}占位符参数，需要Mapper调用时才能确定具体的语句
 * @author Clinton Begin
 */
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration;

  /**
   * 一个SQL标签树的根节点
   */
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  /**
   * 解析SqlSource，获取绑定的BoundSql对象
   * @param parameterObject
   * @return
   */
  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    // 新建DynamicContext对象，存储解析后的SQL内容
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    // 解析SQL树
    rootSqlNode.apply(context);

    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    // 获取参数类型，如果参数为null，设备Object类型，否则取用参数的真实类型
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();

    // 把带#{}的SQL解析成带占位符？的Sql，生成StaticSqlSource对象
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());

    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    // 取context绑定的额外参数设置到boundSql
    context.getBindings().forEach(boundSql::setAdditionalParameter);
    return boundSql;
  }

}
