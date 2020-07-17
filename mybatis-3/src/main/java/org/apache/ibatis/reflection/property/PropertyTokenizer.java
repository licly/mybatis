/**
 *    Copyright 2009-2017 the original author or authors.
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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * 属性分词器，Mapper文件中，入参是对象的情况下，层级属性定义方式为：一级属性.二级属性.三级属性……
 * 比如入参为"user":{"name","licly","age":20}
 *
 * 针对子属性，实现迭代器模式
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
  /**
   * 当前层级属性名，如果fullName传入user.name，则该属性值为user
   */
  private String name;

  /**
   * 如果当前层级属性是个单层级属性，indexedName和name含义相同
   * 如果当前层级属性是个数组类型的，indexedName是当前层级属性名
   * 比如fullName传入user[0],indexedName是user
   */
  private final String indexedName;

  /**
   * 如果当前层级属性是个数组类型的，index是当前层级属性下标
   * 比如fullName传入user[0],index是0
   */
  private String index;

  /**
   * 当前层级属性的子属性全名称，如果fullName传入user.name，则该属性值为name
   */
  private final String children;

  public PropertyTokenizer(String fullName) {
    // 界定符，划分当前层级属性和子属性
    int delim = fullName.indexOf('.');
    if (delim > -1) {
      name = fullName.substring(0, delim);
      children = fullName.substring(delim + 1);
    } else {
      name = fullName;
      children = null;
    }
    indexedName = name;
    // 如果当前层级属性是个数组
    delim = name.indexOf('[');
    if (delim > -1) {
      index = name.substring(delim + 1, name.length() - 1);
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  @Override
  public boolean hasNext() {
    return children != null;
  }

  @Override
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }
}
