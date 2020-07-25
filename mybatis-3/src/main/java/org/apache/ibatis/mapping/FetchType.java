/**
 *    Copyright 2009-2015 the original author or authors.
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

/**
 * <collection>和<association>标签提供了一个fetchType属性，用于控制级联查询的加载行为，
 * fetchType属性值为lazy时表示该级联查询采用懒加载方式，当fetchType属性值为eager时表示该级联查询采用积极加载方式。
 * @author Eduardo Macarron
 */
public enum FetchType {
  LAZY, EAGER, DEFAULT
}
