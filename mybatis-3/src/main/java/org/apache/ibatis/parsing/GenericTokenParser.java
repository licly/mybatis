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
package org.apache.ibatis.parsing;

/**
 * 通用符号解析器，解析完毕调用handler把结果返回
 * @author Clinton Begin
 */
public class GenericTokenParser {

  private final String openToken;
  private final String closeToken;
  /**
   *
   */
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  public String parse(String text) {
    // text不能为空
    if (text == null || text.isEmpty()) {
      return "";
    }

    // search open token 搜索开始匹配符号，没有的话，直接返回
    // 匹配到openToken的下标
    int start = text.indexOf(openToken);
    if (start == -1) {
      return text;
    }

    char[] src = text.toCharArray();
    // 当前字符串处理的偏移量，offset=text.length - 1表示text处理完毕
    int offset = 0;
    final StringBuilder builder = new StringBuilder();
    // 匹配符号内部的字符串
    StringBuilder expression = null;
    while (start > -1) {
      // start > 0 src[start - 1] == '\\' 表示字符被转义，'\\'表示转义字符\
      if (start > 0 && src[start - 1] == '\\') {
        // this open token is escaped. remove the backslash and continue.
        // append解析完的内容。因为这里openToken是被转义的，所以需要按原样append
        builder.append(src, offset, start - offset - 1).append(openToken);
        // 更新offset
        offset = start + openToken.length();
      } else {
        // found open token. let's search close token.
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          expression.setLength(0);
        }

        builder.append(src, offset, start - offset);
        // 偏移量移动openToken位，因为openToken是不需要匹配的
        offset = start + openToken.length();
        // 搜索closeToken索引
        int end = text.indexOf(closeToken, offset);
        // 有匹配closeToken
        while (end > -1) {
          if (end > offset && src[end - 1] == '\\') {
            // 匹配closeToken是转义字符
            // this close token is escaped. remove the backslash and continue.
            // append expression，end - offset - 1:转义字符不append
            expression.append(src, offset, end - offset - 1).append(closeToken);
            offset = end + closeToken.length();
            // 继续搜索closeToken
            end = text.indexOf(closeToken, offset);
          } else {
            // 匹配closeToken并且不是转义字符
            expression.append(src, offset, end - offset);
            break;
          }
        }

        if (end == -1) {
          // close token was not found. closeToken没找到
          builder.append(src, start, src.length - start);
          offset = src.length;
        } else {
          // 找到匹配符号，把表达式交给具体handler处理，然后append返回值
          builder.append(handler.handleToken(expression.toString()));
          offset = end + closeToken.length();
        }
      }

      // 更新匹配到的字符串索引
      start = text.indexOf(openToken, offset);
    }

    // 拼接剩余字符串
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
